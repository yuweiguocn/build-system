/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ASSETS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.ArtifactType;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.resources.AssetMerger;
import com.android.ide.common.resources.AssetSet;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.resources.FileValidity;
import com.android.ide.common.resources.MergedAssetWriter;
import com.android.ide.common.resources.MergingException;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public class MergeSourceSetFolders extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    private Provider<Directory> outputDir;

    @OutputDirectory
    public Provider<Directory> getOutputDir() {
        return outputDir;
    }

    // ----- PRIVATE TASK API -----

    // file inputs as raw files, lazy behind a memoized/bypassed supplier
    private Supplier<Collection<File>> sourceFolderInputs;

    // supplier of the assets set, for execution only.
    private Supplier<List<AssetSet>> assetSetSupplier;

    // for the dependencies
    private ArtifactCollection libraries = null;

    private BuildableArtifact shadersOutputDir = null;
    private FileCollection copyApk = null;
    private String ignoreAssets = null;

    private final FileValidity<AssetSet> fileValidity = new FileValidity<>();

    private final WorkerExecutorFacade workerExecutor;

    @Inject
    public MergeSourceSetFolders(WorkerExecutor workerExecutor) {
        this.workerExecutor = Workers.INSTANCE.getWorker(workerExecutor);
    }

    @Override
    @Internal
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir().get().getAsFile();
        FileUtils.cleanOutputDir(destinationDir);

        List<AssetSet> assetSets = computeAssetSetList();

        // create a new merger and populate it with the sets.
        AssetMerger merger = new AssetMerger();

        try (WorkerExecutorFacade workerExecutor = this.workerExecutor) {
            for (AssetSet assetSet : assetSets) {
                // set needs to be loaded.
                assetSet.loadFromFiles(getILogger());
                merger.addDataSet(assetSet);
            }

            // get the merged set and write it down.
            MergedAssetWriter writer = new MergedAssetWriter(destinationDir, workerExecutor);

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            getLogger().error("Could not merge source set folders: ", e);
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        // create a merger and load the known state.
        AssetMerger merger = new AssetMerger();
        try (WorkerExecutorFacade workerExecutor = this.workerExecutor) {
            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction();
                return;
            }

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            List<AssetSet> assetSets = computeAssetSetList();

            if (!merger.checkValidUpdate(assetSets)) {
                getLogger().info("Changed Asset sets: full task run!");
                doFullTaskAction();
                return;

            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                File changedFile = entry.getKey();

                // Ignore directories.
                if (changedFile.isDirectory()) {
                    continue;
                }

                merger.findDataSetContaining(changedFile, fileValidity);
                if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction();
                    return;

                } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.getDataSet().updateWith(
                            fileValidity.getSourceFile(),
                            changedFile,
                            entry.getValue(),
                            getILogger())) {
                        getLogger().info(
                                "Failed to process {} event! Full task run", entry.getValue());
                        doFullTaskAction();
                        return;
                    }
                }
            }

            MergedAssetWriter writer =
                    new MergedAssetWriter(getOutputDir().get().getAsFile(), workerExecutor);

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            getLogger().error("Could not merge source set folders: ", e);
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        } finally {
            // some clean up after the task to help multi variant/module builds.
            fileValidity.clear();
        }
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getLibraries() {
        if (libraries != null) {
            return libraries.getArtifactFiles();
        }

        return null;
    }

    @VisibleForTesting
    public void setLibraries(@NonNull ArtifactCollection libraries) {
        this.libraries = libraries;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getShadersOutputDir() {
        return shadersOutputDir;
    }

    @VisibleForTesting
    void setShadersOutputDir(BuildableArtifact shadersOutputDir) {
        this.shadersOutputDir = shadersOutputDir;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getCopyApk() {
        return copyApk;
    }

    @VisibleForTesting
    void setCopyApk(FileCollection copyApk) {
        this.copyApk = copyApk;
    }

    @Input
    @Optional
    public String getIgnoreAssets() {
        return ignoreAssets;
    }

    @VisibleForTesting
    void setAssetSetSupplier(Supplier<List<AssetSet>> assetSetSupplier) {
        this.assetSetSupplier = assetSetSupplier;
    }

    // input list for the source folder based asset folders.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Collection<File> getSourceFolderInputs() {
        return sourceFolderInputs.get();
    }

    /**
     * Compute the list of Asset set to be used during execution based all the inputs.
     */
    @VisibleForTesting
    List<AssetSet> computeAssetSetList() {
        List<AssetSet> assetSetList;

        List<AssetSet> assetSets = assetSetSupplier.get();
        if (copyApk == null
                && shadersOutputDir == null
                && ignoreAssets == null
                && libraries == null) {
            assetSetList = assetSets;
        } else {
            int size = assetSets.size() + 3;
            if (libraries != null) {
                size += libraries.getArtifacts().size();
            }

            assetSetList = Lists.newArrayListWithExpectedSize(size);

            // get the dependency base assets sets.
            // add at the beginning since the libraries are less important than the folder based
            // asset sets.
            if (libraries != null) {
                // the order of the artifact is descending order, so we need to reverse it.
                Set<ResolvedArtifactResult> libArtifacts = libraries.getArtifacts();
                for (ResolvedArtifactResult artifact : libArtifacts) {
                    AssetSet assetSet =
                            new AssetSet(ProcessApplicationManifest.getArtifactName(artifact));
                    assetSet.addSource(artifact.getFile());

                    // add to 0 always, since we need to reverse the order.
                    assetSetList.add(0, assetSet);
                }
            }

            // add the generated folders to the first set of the folder-based sets.
            List<File> generatedAssetFolders = Lists.newArrayList();

            if (shadersOutputDir != null) {
                generatedAssetFolders.addAll(shadersOutputDir.getFiles());
            }

            if (copyApk != null) {
                generatedAssetFolders.addAll(copyApk.getFiles());
            }

            // add the generated files to the main set.
            final AssetSet mainAssetSet = assetSets.get(0);
            assert mainAssetSet.getConfigName().equals(BuilderConstants.MAIN);
            mainAssetSet.addSources(generatedAssetFolders);

            assetSetList.addAll(assetSets);
        }

        if (ignoreAssets != null) {
            for (AssetSet set : assetSetList) {
                set.setIgnoredPatterns(ignoreAssets);
            }
        }

        return assetSetList;
    }


    protected abstract static class CreationAction
            extends VariantTaskCreationAction<MergeSourceSetFolders> {

        protected CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public Class<MergeSourceSetFolders> getType() {
            return MergeSourceSetFolders.class;
        }

        @Override
        public void configure(@NonNull MergeSourceSetFolders task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            task.setIncrementalFolder(scope.getIncrementalDir(getName()));
        }
    }

    public static class MergeAssetBaseCreationAction extends CreationAction {

        final boolean includeDependencies;
        final ArtifactType outputArtifactType;
        private Provider<Directory> outputDir;

        public MergeAssetBaseCreationAction(
                @NonNull VariantScope scope,
                @NonNull ArtifactType outputArtifactType,
                boolean includeDependencies) {
            super(scope);
            this.outputArtifactType = outputArtifactType;
            this.includeDependencies = includeDependencies;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("merge", "Assets");
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputDir =
                    getVariantScope()
                            .getArtifacts()
                            .createDirectory(outputArtifactType, taskName, "out");
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends MergeSourceSetFolders> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setMergeAssetsTask(taskProvider);
        }

        @Override
        public void configure(@NonNull MergeSourceSetFolders task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            final BaseVariantData variantData = scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

            final Function<SourceProvider, Collection<File>> assetDirFunction =
                    SourceProvider::getAssetsDirectories;
            task.assetSetSupplier = () -> variantConfig.getSourceFilesAsAssetSets(assetDirFunction);
            task.sourceFolderInputs = () -> variantConfig.getSourceFiles(assetDirFunction);

            task.shadersOutputDir =
                    scope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.SHADER_ASSETS);

            AaptOptions options = scope.getGlobalScope().getExtension().getAaptOptions();
            if (options != null) {
                task.ignoreAssets = options.getIgnoreAssets();
            }

            if (includeDependencies) {
                task.libraries = scope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, ASSETS);
            }

            task.outputDir = outputDir;

            task.dependsOn(scope.getTaskContainer().getAssetGenTask());
        }
    }

    public static class MergeAppAssetCreationAction extends MergeAssetBaseCreationAction {
        public MergeAppAssetCreationAction(@NonNull VariantScope scope) {
            super(scope, InternalArtifactType.MERGED_ASSETS, true);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("merge", "Assets");
        }
    }

    public static class LibraryAssetCreationAction extends MergeAssetBaseCreationAction {
        public LibraryAssetCreationAction(@NonNull VariantScope scope) {
            super(scope, InternalArtifactType.LIBRARY_ASSETS, false);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("package", "Assets");
        }
    }

    public static class MergeJniLibFoldersCreationAction extends CreationAction {
        private Provider<Directory> outputDir;

        public MergeJniLibFoldersCreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("merge", "JniLibFolders");
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputDir =
                    getVariantScope()
                            .getArtifacts()
                            .createDirectory(InternalArtifactType.MERGED_JNI_LIBS, taskName, "out");
        }

        @Override
        public void configure(@NonNull MergeSourceSetFolders mergeAssetsTask) {
            super.configure(mergeAssetsTask);
            BaseVariantData variantData = getVariantScope().getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

            final Function<SourceProvider, Collection<File>> assetDirFunction =
                    SourceProvider::getJniLibsDirectories;
            mergeAssetsTask.assetSetSupplier =
                    () -> variantConfig.getSourceFilesAsAssetSets(assetDirFunction);
            mergeAssetsTask.sourceFolderInputs =
                    () -> variantConfig.getSourceFiles(assetDirFunction);

            mergeAssetsTask.outputDir = outputDir;
        }
    }

    public static class MergeShaderSourceFoldersCreationAction extends CreationAction {
        private Provider<Directory> outputDir;

        public MergeShaderSourceFoldersCreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("merge", "Shaders");
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputDir =
                    getVariantScope()
                            .getArtifacts()
                            .createDirectory(InternalArtifactType.MERGED_SHADERS, taskName, "out");
        }

        @Override
        public void configure(@NonNull MergeSourceSetFolders mergeAssetsTask) {
            super.configure(mergeAssetsTask);
            BaseVariantData variantData = getVariantScope().getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

            final Function<SourceProvider, Collection<File>> assetDirFunction =
                    SourceProvider::getShadersDirectories;
            mergeAssetsTask.assetSetSupplier =
                    () -> variantConfig.getSourceFilesAsAssetSets(assetDirFunction);
            mergeAssetsTask.sourceFolderInputs =
                    () -> variantConfig.getSourceFiles(assetDirFunction);

            mergeAssetsTask.outputDir = outputDir;
        }
    }
}
