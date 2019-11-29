/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildElementsTransformParams;
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.SigningConfigMetadata;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.tooling.BuildException;
import org.gradle.workers.WorkerExecutor;

/** Package a abi dimension specific split APK */
public class PackageSplitAbi extends AndroidBuilderTask {

    private BuildableArtifact processedAbiResources;

    private File outputDirectory;

    private boolean jniDebuggable;

    private FileCollection signingConfig;

    private FileCollection jniFolders;

    private AndroidVersion minSdkVersion;

    private File incrementalDir;

    private Collection<String> aaptOptionsNoCompress;

    private Set<String> splits;

    private final WorkerExecutorFacade workers;

    @Inject
    public PackageSplitAbi(WorkerExecutor workerExecutor) {
        workers = Workers.INSTANCE.getWorker(workerExecutor);
    }

    @InputFiles
    public BuildableArtifact getProcessedAbiResources() {
        return processedAbiResources;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @Input
    public Set<String> getSplits() {
        return splits;
    }

    @Input
    public boolean isJniDebuggable() {
        return jniDebuggable;
    }

    @InputFiles
    public FileCollection getSigningConfig() {
        return signingConfig;
    }

    @InputFiles
    public FileCollection getJniFolders() {
        return jniFolders;
    }

    @Input
    public int getMinSdkVersion() {
        return minSdkVersion.getFeatureLevel();
    }

    @Input
    public Collection<String> getNoCompressExtensions() {
        return aaptOptionsNoCompress != null ? aaptOptionsNoCompress : Collections.emptyList();
    }

    @TaskAction
    protected void doFullTaskAction() throws IOException {
        FileUtils.cleanOutputDir(incrementalDir);

        ExistingBuildElements.from(
                        InternalArtifactType.ABI_PROCESSED_SPLIT_RES, processedAbiResources)
                .transform(
                        workers,
                        PackageSplitAbiTransformRunnable.class,
                        (apkInfo, input) ->
                                new PackageSplitAbiTransformParams(apkInfo, input, this))
                .into(InternalArtifactType.ABI_PACKAGED_SPLIT, outputDirectory);
    }

    private static class PackageSplitAbiTransformRunnable extends BuildElementsTransformRunnable {

        @Inject
        public PackageSplitAbiTransformRunnable(@NonNull PackageSplitAbiTransformParams params) {
            super(params);
        }

        @Override
        public void run() {
            PackageSplitAbiTransformParams params = (PackageSplitAbiTransformParams) getParams();
            try (IncrementalPackager pkg =
                    new IncrementalPackagerBuilder(IncrementalPackagerBuilder.ApkFormat.FILE)
                            .withOutputFile(params.getOutput())
                            .withSigning(
                                    SigningConfigMetadata.Companion.load(params.signingConfigFile))
                            .withCreatedBy(params.createdBy)
                            .withMinSdk(params.minSdkVersion)
                            // .withManifest(manifest)
                            .withAaptOptionsNoCompress(params.aaptOptionsNoCompress)
                            .withIntermediateDir(params.incrementalDir)
                            .withKeepTimestampsInApk(params.keepTimestampsInApk)
                            .withDebuggableBuild(params.isJniDebuggable)
                            .withJniDebuggableBuild(params.isJniDebuggable)
                            .withAcceptedAbis(ImmutableSet.of(params.apkInfo.getFilterName()))
                            .build()) {

                ImmutableMap<RelativeFile, FileStatus> nativeLibs =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(params.jniFolders);

                pkg.updateNativeLibraries(nativeLibs);

                ImmutableMap<RelativeFile, FileStatus> androidResources =
                        IncrementalRelativeFileSets.fromZip(params.input);
                pkg.updateAndroidResources(androidResources);
            } catch (IOException e) {
                throw new BuildException(e.getMessage(), e);
            }
        }
    }

    private static class PackageSplitAbiTransformParams extends BuildElementsTransformParams {
        private final File input;
        private final ApkData apkInfo;
        private final File output;
        private final File incrementalDir;
        private final File signingConfigFile;
        private final String createdBy;
        private final Collection<String> aaptOptionsNoCompress;
        private final Set<File> jniFolders;
        private final boolean keepTimestampsInApk;
        private final boolean isJniDebuggable;
        private final int minSdkVersion;

        PackageSplitAbiTransformParams(ApkData apkInfo, File input, PackageSplitAbi task) {
            this.apkInfo = apkInfo;
            this.input = input;
            output =
                    new File(
                            task.outputDirectory,
                            getApkName(
                                    apkInfo,
                                    (String)
                                            task.getProject()
                                                    .getProperties()
                                                    .get("archivesBaseName"),
                                    task.signingConfig != null));
            incrementalDir = task.incrementalDir;
            signingConfigFile = SigningConfigMetadata.Companion.getOutputFile(task.signingConfig);
            createdBy = task.getBuilder().getCreatedBy();
            aaptOptionsNoCompress = task.aaptOptionsNoCompress;
            jniFolders = task.getJniFolders().getFiles();
            keepTimestampsInApk = AndroidGradleOptions.keepTimestampsInApk(task.getProject());
            isJniDebuggable = task.jniDebuggable;
            minSdkVersion = task.getMinSdkVersion();
        }

        @NonNull
        @Override
        public File getOutput() {
            return output;
        }
    }

    private static String getApkName(
            final ApkData apkData, String archivesBaseName, boolean isSigned) {
        String apkName = archivesBaseName + "-" + apkData.getBaseName();
        return apkName + (isSigned ? "" : "-unsigned") + SdkConstants.DOT_ANDROID_PACKAGE;
    }

    // ----- CreationAction -----

    public static class CreationAction extends VariantTaskCreationAction<PackageSplitAbi> {

        private File outputDirectory;

        public CreationAction(VariantScope scope) {
            super(scope);
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("package", "SplitAbi");
        }

        @Override
        @NonNull
        public Class<PackageSplitAbi> getType() {
            return PackageSplitAbi.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputDirectory =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.ABI_PACKAGED_SPLIT, taskName, "out");
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends PackageSplitAbi> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setPackageSplitAbiTask(taskProvider);
        }

        @Override
        public void configure(@NonNull PackageSplitAbi task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            VariantConfiguration config = scope.getVariantConfiguration();
            task.processedAbiResources =
                    scope.getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.ABI_PROCESSED_SPLIT_RES);
            task.signingConfig = scope.getSigningConfigFileCollection();
            task.outputDirectory = outputDirectory;
            task.minSdkVersion = config.getMinSdkVersion();
            task.incrementalDir = scope.getIncrementalDir(task.getName());

            task.aaptOptionsNoCompress =
                    scope.getGlobalScope().getExtension().getAaptOptions().getNoCompress();
            task.jniDebuggable = config.getBuildType().isJniDebuggable();

            task.jniFolders =
                    scope.getTransformManager()
                            .getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS);
            task.jniDebuggable = config.getBuildType().isJniDebuggable();
            task.splits = scope.getVariantData().getFilters(OutputFile.FilterType.ABI);

            MutableTaskContainer taskContainer = scope.getTaskContainer();

            if (taskContainer.getExternalNativeBuildTask() != null) {
                task.dependsOn(taskContainer.getExternalNativeBuildTask());
            }
        }
    }
}
