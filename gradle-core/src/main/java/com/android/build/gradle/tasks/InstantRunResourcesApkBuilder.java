/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildElementsTransformParams;
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.SigningConfigMetadata;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.signing.KeytoolException;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;
import org.gradle.workers.WorkerExecutor;

/**
 * Task for create a split apk per packaged resources.
 *
 * <p>Right now, there is only one packaged resources file in InstantRun mode, but we could decide
 * to slice the resources in the future.
 */
public class InstantRunResourcesApkBuilder extends AndroidBuilderTask {

    @VisibleForTesting public static final String APK_FILE_NAME = "resources";

    /** Used to stop the worker item from actually doing the packaging in unit tests */
    @VisibleForTesting static boolean doPackageCodeSplitApk = true;

    private AndroidBuilder androidBuilder;
    private InstantRunBuildContext instantRunBuildContext;
    private File outputDirectory;
    private FileCollection signingConf;
    private File supportDirectory;

    private BuildableArtifact resources;

    private InternalArtifactType resInputType;

    private final WorkerExecutorFacade workers;

    @Inject
    public InstantRunResourcesApkBuilder(WorkerExecutor workerExecutor) {
        workers = Workers.INSTANCE.getWorker(workerExecutor);
    }

    @InputFiles
    public FileCollection getSigningConf() {
        return signingConf;
    }

    @Input
    public String getResInputType() {
        return resInputType.name();
    }

    @Input
    public String getPatchingPolicy() {
        return instantRunBuildContext.getPatchingPolicy().name();
    }

    @InputFiles
    public BuildableArtifact getResourcesFile() {
        return resources;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    protected void doFullTaskAction() {

        if (instantRunBuildContext.getPatchingPolicy()
                != InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES) {
            // when not packaging resources in a separate APK, delete the output APK file so
            // that if we switch back to this mode later on, we ensure that the APK is rebuilt
            // and re-added to the build context and therefore the build-info.xml
            getResInputBuildArtifacts()
                    .forEach(
                            buildOutput -> {
                                ApkData apkInfo = buildOutput.getApkData();
                                final File outputFile =
                                        new File(
                                                outputDirectory,
                                                mangleApkName(apkInfo)
                                                        + SdkConstants.DOT_ANDROID_PACKAGE);
                                try {
                                    FileUtils.deleteIfExists(outputFile);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
            return;
        }
        List<File> outputFiles = new ArrayList<>();
        getResInputBuildArtifacts()
                .transform(
                        workers,
                        ApkBuilderRunnable.class,
                        (apkInfo, input) -> {
                            ApkBuilderParams params = new ApkBuilderParams(apkInfo, input, this);
                            outputFiles.add(params.getOutput());
                            return params;
                        })
                .into(InternalArtifactType.INSTANT_RUN_PACKAGED_RESOURCES, outputDirectory);
        outputFiles.forEach(it -> instantRunBuildContext.addChangedFile(FileType.SPLIT, it));
    }

    private static class ApkBuilderRunnable extends BuildElementsTransformRunnable {

        public ApkBuilderRunnable(@NonNull ApkBuilderParams params) {
            super(params);
        }

        @Override
        public void run() {
            ApkBuilderParams params = (ApkBuilderParams) getParams();

            try {

                FileUtils.deleteIfExists(params.outputFile);
                FileUtils.mkdirs(params.outputFile.getParentFile());

                // packageCodeSplitApk uses a temporary directory for incremental runs.
                // Since we don't
                // do incremental builds here, make sure it gets an empty directory.
                FileUtils.cleanOutputDir(params.tempDir);
                if (doPackageCodeSplitApk) {
                    AndroidBuilder.packageCodeSplitApk(
                            params.input,
                            ImmutableSet.of(),
                            SigningConfigMetadata.Companion.load(params.signingConfigFile),
                            params.outputFile,
                            params.tempDir,
                            ApkCreatorFactories.fromProjectProperties(
                                    params.keepTimestampsInApk, true),
                            params.createdBy);
                }
            } catch (IOException | KeytoolException | PackagerException e) {
                throw new BuildException("Exception while creating resources split APK", e);
            }
        }
    }

    private static class ApkBuilderParams extends BuildElementsTransformParams {
        @NonNull private final File input;
        @NonNull private final File outputFile;
        @NonNull private final File tempDir;
        @Nullable private final File signingConfigFile;
        private final String createdBy;
        private final boolean keepTimestampsInApk;

        ApkBuilderParams(ApkData apkInfo, @NonNull File input, InstantRunResourcesApkBuilder task) {
            this.input = input;
            outputFile =
                    new File(
                            task.outputDirectory,
                            mangleApkName(apkInfo) + SdkConstants.DOT_ANDROID_PACKAGE);
            tempDir = new File(task.supportDirectory, "package_" + mangleApkName(apkInfo));
            signingConfigFile = SigningConfigMetadata.Companion.getOutputFile(task.signingConf);
            createdBy = task.androidBuilder.getCreatedBy();
            keepTimestampsInApk = AndroidGradleOptions.keepTimestampsInApk(task.getProject());
        }

        @NonNull
        @Override
        public File getOutput() {
            return outputFile;
        }
    }

    @VisibleForTesting
    protected BuildElements getResInputBuildArtifacts() {
        return ExistingBuildElements.from(resInputType, resources);
    }

    static String mangleApkName(ApkData apkData) {
        return APK_FILE_NAME + "-" + apkData.getBaseName();
    }

    public static class CreationAction extends TaskCreationAction<InstantRunResourcesApkBuilder> {

        protected final VariantScope variantScope;
        private final InternalArtifactType resInputType;

        public CreationAction(
                @NonNull InternalArtifactType resInputType, @NonNull VariantScope scope) {
            this.resInputType = resInputType;
            this.variantScope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("processInstantRun", "ResourcesApk");
        }

        @NonNull
        @Override
        public Class<InstantRunResourcesApkBuilder> getType() {
            return InstantRunResourcesApkBuilder.class;
        }

        @Override
        public void configure(@NonNull InstantRunResourcesApkBuilder resourcesApkBuilder) {
            resourcesApkBuilder.setVariantName(variantScope.getFullVariantName());
            resourcesApkBuilder.resInputType = resInputType;
            resourcesApkBuilder.supportDirectory = variantScope.getIncrementalDir(getName());
            resourcesApkBuilder.androidBuilder = variantScope.getGlobalScope().getAndroidBuilder();
            resourcesApkBuilder.signingConf = variantScope.getSigningConfigFileCollection();
            resourcesApkBuilder.instantRunBuildContext = variantScope.getInstantRunBuildContext();
            resourcesApkBuilder.resources =
                    variantScope.getArtifacts().getFinalArtifactFiles(resInputType);
            resourcesApkBuilder.outputDirectory = variantScope.getInstantRunResourceApkFolder();
        }
    }
}
