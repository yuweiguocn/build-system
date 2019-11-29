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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputProperty;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.manifmerger.ManifestProvider;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskProvider;

/**
 * A task that processes the manifest for test modules and tests in androidTest.
 *
 * <p>For both test modules and tests in androidTest process is the same, expect
 * for how the tested application id is extracted.</p>
 *
 * <p>Tests in androidTest get that info form the
 * {@link VariantConfiguration#getTestedApplicationId()}, while the test modules get the info from
 * the published intermediate manifest with type {@link AndroidArtifacts#TYPE_METADATA}
 * of the tested app.</p>
 */
public class ProcessTestManifest extends ManifestProcessorTask {

    @NonNull private Provider<Directory> testTargetMetadata;

    @Nullable
    private File testManifestFile;

    /** Whether there's just a single APK with both test and tested code. */
    private boolean onlyTestApk;

    private File tmpDir;
    private Supplier<String> testApplicationId;
    private Supplier<String> testedApplicationId;
    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<String> instrumentationRunner;
    private Supplier<Boolean> handleProfiling;
    private Supplier<Boolean> functionalTest;
    private Supplier<Map<String, Object>> placeholdersValues;

    private ArtifactCollection manifests;

    private Supplier<String> testLabel;

    private OutputScope outputScope;

    @Inject
    public ProcessTestManifest(ObjectFactory objectFactory) {
        super(objectFactory);
    }

    public OutputScope getOutputScope() {
        return outputScope;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        if (testedApplicationId == null && testTargetMetadata == null) {
            throw new RuntimeException("testedApplicationId and testTargetMetadata are null");
        }
        String testedApplicationId = this.getTestedApplicationId();
        if (!onlyTestApk && testTargetMetadata != null) {
            BuildElements manifestOutputs =
                    ExistingBuildElements.from(
                            MERGED_MANIFESTS, testTargetMetadata.get().getAsFile());

            java.util.Optional<BuildOutput> mainSplit =
                    manifestOutputs
                            .stream()
                            .filter(
                                    output ->
                                            output.getApkData().getType()
                                                    != VariantOutput.OutputType.SPLIT)
                            .findFirst();

            if (mainSplit.isPresent()) {
                testedApplicationId =
                        mainSplit.get().getProperties().get(BuildOutputProperty.PACKAGE_ID);
            } else {
                throw new RuntimeException("cannot find main APK");
            }
        }
        // TODO : LOAD FROM APK_LIST...
        List<ApkData> apkDatas = outputScope.getApkDatas();
        if (apkDatas.isEmpty()) {
            throw new RuntimeException("No output defined for test module, please file a bug");
        }
        if (apkDatas.size() > 1) {
            throw new RuntimeException(
                    "Test modules only support a single split, this one defines"
                            + Joiner.on(",").join(apkDatas));
        }
        ApkData mainApkData = apkDatas.get(0);
        File manifestOutputFolder =
                Strings.isNullOrEmpty(mainApkData.getDirName())
                        ? getManifestOutputDirectory().get().getAsFile()
                        : getManifestOutputDirectory()
                                .get()
                                .file(mainApkData.getDirName())
                                .getAsFile();


        FileUtils.mkdirs(manifestOutputFolder);
        File manifestOutputFile = new File(manifestOutputFolder, SdkConstants.ANDROID_MANIFEST_XML);

        getBuilder()
                .mergeManifestsForTestVariant(
                        getTestApplicationId(),
                        getMinSdkVersion(),
                        getTargetSdkVersion(),
                        testedApplicationId,
                        getInstrumentationRunner(),
                        getHandleProfiling(),
                        getFunctionalTest(),
                        getTestLabel(),
                        getTestManifestFile(),
                        computeProviders(),
                        getPlaceholdersValues(),
                        manifestOutputFile,
                        getTmpDir());

        new BuildElements(
                        ImmutableList.of(
                                new BuildOutput(MERGED_MANIFESTS, mainApkData, manifestOutputFile)))
                .save(getManifestOutputDirectory().get().getAsFile());
    }

    @Nullable
    @Override
    public File getAaptFriendlyManifestOutputFile() {
        return null;
    }

    @Nullable
    @InputFile
    @Optional
    public File getTestManifestFile() {
        return testManifestFile;
    }

    public void setTestManifestFile(@Nullable File testManifestFile) {
        this.testManifestFile = testManifestFile;
    }

    public File getTmpDir() {
        return tmpDir;
    }

    public void setTmpDir(File tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Input
    public String getTestApplicationId() {
        return testApplicationId.get();
    }
    @Input
    @Optional
    public String getTestedApplicationId() {
        return testedApplicationId.get();
    }

    @Input
    public String getMinSdkVersion() {
        return minSdkVersion.get();
    }
    @Input
    public String getTargetSdkVersion() {
        return targetSdkVersion.get();
    }

    @Input
    public String getInstrumentationRunner() {
        return instrumentationRunner.get();
    }

    @Input
    public Boolean getHandleProfiling() {
        return handleProfiling.get();
    }

    @Input
    public Boolean getFunctionalTest() {
        return functionalTest.get();
    }

    @Input
    @Optional
    public String getTestLabel() {
        return testLabel.get();
    }

    @Input
    public Map<String, Object> getPlaceholdersValues() {
        return placeholdersValues.get();
    }

    @InputFiles
    @Optional
    public Provider<Directory> getTestTargetMetadata() {
        return testTargetMetadata;
    }

    /**
     * Compute the final list of providers based on the manifest file collection.
     * @return the list of providers.
     */
    public List<ManifestProvider> computeProviders() {
        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size());

        for (ResolvedArtifactResult artifact : artifacts) {
            providers.add(
                    new ProcessApplicationManifest.CreationAction.ManifestProviderImpl(
                            artifact.getFile(),
                            ProcessApplicationManifest.getArtifactName(artifact)));
        }

        return providers;
    }

    @InputFiles
    public FileCollection getManifests() {
        return manifests.getArtifactFiles();
    }

    public static class CreationAction
            extends AnnotationProcessingTaskCreationAction<ProcessTestManifest> {

        @NonNull
        private final VariantScope scope;

        @NonNull private final Provider<Directory> testTargetMetadata;
        private Provider<Directory> manifestOutputDirectory;

        public CreationAction(
                @NonNull VariantScope scope, @NonNull Provider<Directory> testTargetMetadata) {
            super(scope, scope.getTaskName("process", "Manifest"), ProcessTestManifest.class);
            this.scope = scope;
            this.testTargetMetadata = testTargetMetadata;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            scope.getArtifacts()
                    .republish(
                            InternalArtifactType.MERGED_MANIFESTS,
                            InternalArtifactType.MANIFEST_METADATA);

            manifestOutputDirectory =
                    scope.getArtifacts()
                            .createDirectory(
                                    InternalArtifactType.MERGED_MANIFESTS,
                                    BuildArtifactsHolder.OperationType.INITIAL,
                                    taskName,
                                    "");
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ProcessTestManifest> taskProvider) {
            super.handleProvider(taskProvider);
            scope.getTaskContainer().setProcessManifestTask(taskProvider);

            scope.getArtifacts()
                    .registerProducer(
                            InternalArtifactType.MERGED_MANIFESTS,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            taskProvider.map(ManifestProcessorTask::getManifestOutputDirectory),
                            "");
        }

        @Override
        public void configure(@NonNull final ProcessTestManifest task) {
            super.configure(task);

            task.checkManifestResult =
                    scope.getArtifacts()
                            .getFinalArtifactFilesIfPresent(
                                    InternalArtifactType.CHECK_MANIFEST_RESULT);

            final VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    scope.getVariantConfiguration();

            task.setTestManifestFile(config.getMainManifest());
            task.outputScope = scope.getOutputScope();

            task.setTmpDir(
                    FileUtils.join(
                            scope.getGlobalScope().getIntermediatesDir(),
                            "tmp",
                            "manifest",
                            scope.getDirName()));

            task.minSdkVersion =
                    TaskInputHelper.memoize(() -> config.getMinSdkVersion().getApiString());

            task.targetSdkVersion =
                    TaskInputHelper.memoize(() -> config.getTargetSdkVersion().getApiString());

            task.testTargetMetadata = testTargetMetadata;
            task.testApplicationId = TaskInputHelper.memoize(config::getTestApplicationId);

            // will only be used if testTargetMetadata is null.
            task.testedApplicationId = TaskInputHelper.memoize(config::getTestedApplicationId);

            VariantConfiguration testedConfig = config.getTestedConfig();
            task.onlyTestApk = testedConfig != null && testedConfig.getType().isAar();

            task.instrumentationRunner = TaskInputHelper.memoize(config::getInstrumentationRunner);
            task.handleProfiling = TaskInputHelper.memoize(config::getHandleProfiling);
            task.functionalTest = TaskInputHelper.memoize(config::getFunctionalTest);
            task.testLabel = TaskInputHelper.memoize(config::getTestLabel);

            task.manifests = scope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);

            task.placeholdersValues = TaskInputHelper.memoize(config::getManifestPlaceholders);
        }
    }
}
