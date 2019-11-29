/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_APP_INFO_OUTPUT_FILE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskFactory;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.transforms.InstantRunDex;
import com.android.build.gradle.internal.transforms.InstantRunTransform;
import com.android.build.gradle.internal.transforms.InstantRunVerifierTransform;
import com.android.build.gradle.internal.transforms.NoChangesVerifierTransform;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.CheckManifestInInstantRunMode;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.build.gradle.tasks.ir.FastDeployRuntimeExtractorTask;
import com.android.build.gradle.tasks.ir.GenerateInstantRunAppInfoTask;
import com.android.build.gradle.tasks.ir.InstantRunMainApkResourcesBuilder;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.profile.Recorder;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.TaskState;

/**
 * Task Manager for InstantRun related transforms configuration and tasks handling.
 */
public class InstantRunTaskManager {

    @Nullable private TaskProvider<TransformTask> verifierTask;
    @Nullable private TaskProvider<TransformTask> reloadDexTask;
    @Nullable private TaskProvider<BuildInfoLoaderTask> buildInfoLoaderTask;

    @NonNull
    private final Logger logger;

    @NonNull private final VariantScope variantScope;

    @NonNull
    private final TransformManager transformManager;

    @NonNull private final TaskFactory taskFactory;
    @NonNull private final Recorder recorder;

    public InstantRunTaskManager(
            @NonNull Logger logger,
            @NonNull VariantScope instantRunVariantScope,
            @NonNull TransformManager transformManager,
            @NonNull TaskFactory taskFactory,
            @NonNull Recorder recorder) {
        this.logger = logger;
        this.variantScope = instantRunVariantScope;
        this.transformManager = transformManager;
        this.taskFactory = taskFactory;
        this.recorder = recorder;
    }

    public TaskProvider<BuildInfoLoaderTask> createInstantRunAllTasks(
            @Nullable TaskProvider<? extends Task> preTask,
            TaskProvider<? extends Task> anchorTask,
            Set<? super QualifiedContent.Scope> resMergingScopes,
            Provider<Directory> mergedManifests,
            Provider<Directory> instantRunMergedManifests,
            boolean addDependencyChangeChecker,
            int minSdkForDx,
            boolean enableDesugaring,
            FileCollection bootClasspath,
            MessageReceiver messageReceiver) {
        final Project project = variantScope.getGlobalScope().getProject();

        buildInfoLoaderTask =
                taskFactory.register(new BuildInfoLoaderTask.CreationAction(variantScope, logger));

        // always run the verifier first, since if it detects incompatible changes, we
        // should skip bytecode enhancements of the changed classes.
        InstantRunVerifierTransform verifierTransform =
                new InstantRunVerifierTransform(variantScope, recorder);
        verifierTask =
                transformManager
                        .addTransform(
                                taskFactory,
                                variantScope,
                                verifierTransform,
                                null,
                                task -> {
                                    if (preTask != null) {
                                        task.dependsOn(preTask);
                                    }
                                },
                                null)
                        .orElse(null);

        NoChangesVerifierTransform javaResourcesVerifierTransform =
                new NoChangesVerifierTransform(
                        "javaResourcesVerifier",
                        variantScope.getInstantRunBuildContext(),
                        ImmutableSet.of(
                                QualifiedContent.DefaultContentType.RESOURCES,
                                ExtendedContentType.NATIVE_LIBS),
                        resMergingScopes,
                        InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED);

        Optional<TaskProvider<TransformTask>> javaResourcesVerifierTask =
                transformManager.addTransform(
                        taskFactory,
                        variantScope,
                        javaResourcesVerifierTransform,
                        null,
                        task -> {
                            if (verifierTask != null) {
                                task.dependsOn(verifierTask);
                            }
                        },
                        null);

        // create the manifest file change checker. This task should always run even if the
        // processAndroidResources task did not run. It is possible (through an IDE sync mainly)
        // that the processAndroidResources task ran in a previous non InstantRun enabled
        // invocation.
        TaskProvider<CheckManifestInInstantRunMode> checkManifestTask =
                taskFactory.register(
                        new CheckManifestInInstantRunMode.CreationAction(variantScope));

        InstantRunTransform instantRunTransform = new InstantRunTransform(
                WaitableExecutor.useGlobalSharedThreadPool(),
                variantScope);

        Optional<TaskProvider<TransformTask>> instantRunTask =
                transformManager.addTransform(
                        taskFactory,
                        variantScope,
                        instantRunTransform,
                        null,
                        task ->
                                task.dependsOn(
                                        buildInfoLoaderTask,
                                        verifierTask,
                                        javaResourcesVerifierTask.orElse(null),
                                        checkManifestTask),
                        null);

        InternalArtifactType resourceFilesInputType =
                variantScope.useResourceShrinker()
                        ? InternalArtifactType.SHRUNK_PROCESSED_RES
                        : InternalArtifactType.PROCESSED_RES;

        if (variantScope.getInstantRunBuildContext().useSeparateApkForResources()) {
            // add a task to create an empty resource with the merged manifest file that
            // will eventually get packaged in the main APK.
            // We need to pass the published resource type as the generated manifest can use
            // a String resource for its version name (so AAPT can check for resources existence).
            taskFactory.register(
                    new InstantRunMainApkResourcesBuilder.CreationAction(
                            variantScope, resourceFilesInputType));
        }


        if (addDependencyChangeChecker) {
            NoChangesVerifierTransform dependenciesVerifierTransform =
                    new NoChangesVerifierTransform(
                            "dependencyChecker",
                            variantScope.getInstantRunBuildContext(),
                            ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                            Sets.immutableEnumSet(QualifiedContent.Scope.EXTERNAL_LIBRARIES),
                            InstantRunVerifierStatus.DEPENDENCY_CHANGED);
            Optional<TaskProvider<TransformTask>> dependenciesVerifierTask =
                    transformManager.addTransform(
                            taskFactory, variantScope, dependenciesVerifierTransform);

            dependenciesVerifierTask.ifPresent(
                    t -> {
                        if (verifierTask != null) {
                            TaskFactoryUtils.dependsOn(t, verifierTask);
                        }
                    });
            instantRunTask.ifPresent(
                    IRTask ->
                            dependenciesVerifierTask.ifPresent(
                                    depVerifyTask ->
                                            TaskFactoryUtils.dependsOn(IRTask, depVerifyTask)));
        }


        TaskProvider<FastDeployRuntimeExtractorTask> extractorTask =
                taskFactory.register(
                        new FastDeployRuntimeExtractorTask.CreationAction(
                                variantScope, buildInfoLoaderTask));

        // also add a new stream for the extractor task output.
        transformManager.addStream(
                OriginalStream.builder(project, "main-split-from-extractor")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(InternalScope.MAIN_SPLIT)
                        .setFileCollection(
                                project.files(variantScope.getIncrementalRuntimeSupportJar())
                                        .builtBy(extractorTask))
                        .build());

        // create the AppInfo.class for this variant.
        taskFactory.register(
                new GenerateInstantRunAppInfoTask.CreationAction(
                        variantScope, variantScope, mergedManifests, instantRunMergedManifests));

        // also add a new stream for the injector task output.
        transformManager.addStream(
                OriginalStream.builder(project, "main-split-from-injector")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(InternalScope.MAIN_SPLIT)
                        .setFileCollection(
                                variantScope
                                        .getArtifacts()
                                        .getFinalArtifactFiles(INSTANT_RUN_APP_INFO_OUTPUT_FILE)
                                        .get()) // FIXME the file collection returned does not survives Artifact transform/append b/110709212
                        .build());

        instantRunTask.ifPresent(provider -> TaskFactoryUtils.dependsOn(anchorTask, provider));

        // we always produce the reload.dex irrespective of the targeted version,
        // and if we are not in incremental mode, we need to still need to clean our output state.
        InstantRunDex reloadDexTransform =
                new InstantRunDex(
                        variantScope,
                        minSdkForDx,
                        enableDesugaring,
                        bootClasspath,
                        messageReceiver);

        reloadDexTask =
                transformManager
                        .addTransform(taskFactory, variantScope, reloadDexTransform)
                        .orElse(null);
        if (reloadDexTask != null) {
            TaskFactoryUtils.dependsOn(anchorTask, reloadDexTask);
        }

        return buildInfoLoaderTask;
    }

    /** Creates all InstantRun related transforms after compilation. */
    @NonNull
    public TaskProvider<PreColdSwapTask> createPreColdswapTask(
            @NonNull ProjectOptions projectOptions) {

        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();
        InstantRunBuildContext context = variantScope.getInstantRunBuildContext();

        if (transformVariantScope.getGlobalScope().isActive(OptionalCompilationStep.FULL_APK)) {
            context.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        } else if (transformVariantScope.getGlobalScope().isActive(
                OptionalCompilationStep.RESTART_ONLY)) {
            context.setVerifierStatus(InstantRunVerifierStatus.COLD_SWAP_REQUESTED);
        }

        return taskFactory.register(
                new PreColdSwapTask.CreationAction(
                        "preColdswap", transformVariantScope, variantScope, verifierTask));
    }

    /**
     * Configures the task to save the build-info.xml and sets its dependencies for instant run.
     *
     * <p>This task does not depend on other tasks, so if previous tasks fails it will still run.
     * Instead the read build info task is {@link Task#finalizedBy(Object...)} the write build info
     * task, so whenever the read task runs the write task must also run.
     *
     * <p>It also {@link Task#mustRunAfter(Object...)} the various build types so that it runs after
     * those tasks, but runs even if those tasks fail. Using {@link Task#dependsOn(Object...)} would
     * not run the task if a previous task failed.
     *
     * @param buildInfoWriterTask the task instance.
     */
    public void configureBuildInfoWriterTask(
            @NonNull TaskProvider<BuildInfoWriterTask> buildInfoWriterTask, Task... dependencies) {
        Preconditions.checkNotNull(buildInfoLoaderTask,
                "createInstantRunAllTasks() should have been called first ");
        buildInfoLoaderTask.configure(t -> t.finalizedBy(buildInfoWriterTask));

        buildInfoWriterTask.configure(
                t -> {
                    if (reloadDexTask != null) {
                        t.mustRunAfter(reloadDexTask);
                    }
                    if (dependencies != null) {
                        for (Task dependency : dependencies) {
                            t.mustRunAfter(dependency);
                        }
                    }
                });

        // Register a task execution listener to allow the writer task to write the temp build info
        // on build failure, which will get merged into the next build.
        variantScope
                .getGlobalScope()
                .getProject()
                .getGradle()
                .getTaskGraph()
                .addTaskExecutionListener(
                        new TaskExecutionAdapter() {
                            @Override
                            public void afterExecute(Task task, TaskState state) {
                                //noinspection ThrowableResultOfMethodCallIgnored
                                if (state.getFailure() != null) {
                                    variantScope.getInstantRunBuildContext().setBuildHasFailed();
                                }
                            }
                        });
    }

}
