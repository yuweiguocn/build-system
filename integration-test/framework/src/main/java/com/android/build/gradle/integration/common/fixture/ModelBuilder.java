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

package com.android.build.gradle.integration.common.fixture;

import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.Option;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

/**
 * Builder for actions that get the gradle model from a {@link GradleTestProject}.
 *
 * <p>Example: <code>project.model().asStudio1().fetchAndroidProjects()</code> fetches the model for
 * all subprojects as Studio 1.0 does.
 */
public class ModelBuilder extends BaseGradleExecutor<ModelBuilder> {
    private Set<String> explicitlyAllowedOptions = new HashSet<>();
    private int maxSyncIssueSeverityLevel = 0;
    private int modelLevel = AndroidProject.MODEL_LEVEL_LATEST;

    ModelBuilder(@NonNull GradleTestProject project, @NonNull ProjectConnection projectConnection) {
        super(
                projectConnection,
                project::setLastBuildResult,
                project.getTestDir().toPath(),
                project.getBuildFile().toPath(),
                project.getProfileDirectory(),
                project.getHeapSize());
    }

    public ModelBuilder(
            @NonNull ProjectConnection projectConnection,
            @NonNull Consumer<GradleBuildResult> lastBuildResultConsumer,
            @NonNull Path projectDirectory,
            @Nullable Path buildDotGradleFile,
            @NonNull GradleTestProjectBuilder.MemoryRequirement memoryRequirement) {
        super(
                projectConnection,
                lastBuildResultConsumer,
                projectDirectory,
                buildDotGradleFile,
                null /*profileDirectory*/,
                memoryRequirement);
    }

    /**
     * Do not fail if there are sync issues.
     *
     * <p>Equivalent to {@code ignoreSyncIssues(SyncIssue.SEVERITY_ERROR)}.
     */
    @NonNull
    public ModelBuilder ignoreSyncIssues() {
        return ignoreSyncIssues(SyncIssue.SEVERITY_ERROR);
    }

    /**
     * Do not fail if there are sync issues.
     *
     * <p>The severity argument is one of {@code SyncIssue.SEVERITY_ERROR} or {@code
     * SyncIssue.SEVERITY_WARNING}.
     */
    @NonNull
    public ModelBuilder ignoreSyncIssues(int severity) {
        Preconditions.checkState(
                modelLevel != AndroidProject.MODEL_LEVEL_0_ORIGINAL,
                "version 1 of Android Studio was not aware of sync issues, so it's invalid to ignore them when using MODEL_LEVEL_0_ORIGINAL");
        Preconditions.checkArgument(
                severity == SyncIssue.SEVERITY_WARNING || severity == SyncIssue.SEVERITY_ERROR,
                "incorrect severity, must be one of SyncIssue.SEVERITY_WARNING or SyncIssue.SEVERITY_ERROR");

        maxSyncIssueSeverityLevel = severity;
        return this;
    }

    @NonNull
    public ModelBuilder allowOptionWarning(@NonNull Option<?> option) {
        explicitlyAllowedOptions.add(option.getPropertyName());
        return this;
    }

    /**
     * Sets the model query with the given level.
     *
     * <p>See {@code AndroidProject.MODEL_LEVEL_*}.
     */
    @NonNull
    public ModelBuilder level(int modelLevel) {
        this.modelLevel = modelLevel;
        return this;
    }

    @NonNull
    public ModelBuilder withFullDependencies() {
        with(BooleanOption.IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES, true);
        return this;
    }

    /**
     * Fetches the project model via the tooling API
     *
     * <p>This will fail if the project is a multi-project setup, use {@link #fetchMulti(Class)}
     * instead.
     *
     * <p>This will fail if the requested model is {@link AndroidProject}. Use {@link
     * #fetchAndroidProjects()} instead
     *
     * @param modelClass the class of the model to query
     * @param <T> the type of the model
     */
    @NonNull
    public <T> T fetch(@NonNull Class<T> modelClass) throws IOException {
        Preconditions.checkArgument(
                modelClass != AndroidProject.class, "please use fetchAndroidProjects() instead");

        ModelContainer<T> container = buildModel(new GetAndroidModelAction<>(modelClass));

        // ensure there was only one project
        Preconditions.checkState(
                container.getModelMaps().size() == 1,
                "attempted to fetch() with multi-project settings");

        return container.getOnlyModel();
    }

    /**
     * Fetches AndroidProject and its Variants via Tooling API and schedule IDE setup tasks to run
     *
     * @param modelConsumer validations to be run against the fetched model
     * @return the build result
     */
    @NonNull
    public GradleBuildResult fetchAndroidModelAndGenerateSources(
            Consumer<ParameterizedAndroidProject> modelConsumer) throws IOException {
        BuildActionExecuter<Void> executor =
                projectConnection
                        .action()
                        .projectsLoaded(
                                new GetAndroidModelAction<>(
                                        ParameterizedAndroidProject.class, true),
                                models -> modelConsumer.accept(models.getOnlyModel()))
                        .build()
                        .forTasks(Collections.emptyList());

        return buildModel(executor).getSecond();
    }

    /** Fetches the multi-project Android models via the tooling API. */
    @NonNull
    public ModelContainer<AndroidProject> fetchAndroidProjects() throws IOException {
        return assertNoSyncIssues(doQueryMultiContainer(AndroidProject.class));
    }

    /**
     * Fetches the multi=project models via the tooling API and return them as a map of (path,
     * model).
     *
     * <p>This will fail if the requested model is {@link AndroidProject}. Use {@link
     * #fetchAndroidProjects()} instead
     *
     * @param modelClass the class of the model
     * @param <T> the type of the model
     */
    @NonNull
    public <T> Map<String, T> fetchMulti(@NonNull Class<T> modelClass) throws IOException {
        Preconditions.checkArgument(
                modelClass != AndroidProject.class, "please use fetchAndroidProjects() instead");

        final ModelContainer<T> modelContainer = doQueryMultiContainer(modelClass);

        if (modelContainer.getModelMaps().size() > 1) {
            throw new RuntimeException(
                    "Can't call fetchMulti(Class) with included builds, use fetchContainer");
        }

        return modelContainer.getRootBuildModelMap();
    }

    /**
     * Fetches the multi=project models via the tooling API and return them as a {@link
     * ModelContainer}
     *
     * <p>This will fail if the requested model is {@link AndroidProject}. Use {@link
     * #fetchAndroidProjects()} instead
     *
     * @param modelClass the class of the model
     * @param <T> the type of the model
     */
    @NonNull
    public <T> ModelContainer<T> fetchContainer(@NonNull Class<T> modelClass) throws IOException {
        Preconditions.checkArgument(
                modelClass != AndroidProject.class, "please use fetchAndroidProjects() instead");

        final ModelContainer<T> modelContainer = doQueryMultiContainer(modelClass);

        return modelContainer;
    }

    @NonNull
    private <T> ModelContainer<T> doQueryMultiContainer(@NonNull Class<T> modelClass)
            throws IOException {
        return buildModel(new GetAndroidModelAction<>(modelClass));
    }

    /** Return a list of all task names of the project. */
    @NonNull
    public List<String> fetchTaskList() throws IOException {
        return getProject()
                .getTasks()
                .stream()
                .map(GradleTask::getName)
                .collect(Collectors.toList());
    }

    private GradleProject getProject() throws IOException {
        return projectConnection.model(GradleProject.class).withArguments(getArguments()).get();
    }

    /**
     * Returns a project model for each sub-project;
     *
     * @param action the build action to gather the model
     */
    @NonNull
    private <T> ModelContainer<T> buildModel(@NonNull BuildAction<ModelContainer<T>> action)
            throws IOException {
        BuildActionExecuter<ModelContainer<T>> executor = projectConnection.action(action);
        return buildModel(executor).getFirst();
    }

    /**
     * Returns a project model container and the build result.
     *
     * <p>Can be used both when just fetching models or when also scheduling tasks to be run.
     */
    @NonNull
    private <T> Pair<T, GradleBuildResult> buildModel(@NonNull BuildActionExecuter<T> executor)
            throws IOException {
        with(BooleanOption.IDE_BUILD_MODEL_ONLY, true);
        with(BooleanOption.IDE_INVOKED_FROM_IDE, true);

        switch (modelLevel) {
            case AndroidProject.MODEL_LEVEL_0_ORIGINAL:
                // nothing.
                break;
            case AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD:
            case AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL:
                with(IntegerOption.IDE_BUILD_MODEL_ONLY_VERSION, modelLevel);
                // intended fall-through
            case AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE:
                with(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED, true);
                break;
            default:
                throw new RuntimeException("Unsupported ModelLevel: " + modelLevel);
        }

        setJvmArguments(executor);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        setStandardOut(executor, stdout);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        setStandardError(executor, stderr);

        CollectingProgressListener progressListener = new CollectingProgressListener();
        executor.addProgressListener(progressListener, OperationType.TASK);

        try {
            T model = executor.withArguments(getArguments()).run();
            GradleBuildResult buildResult =
                    new GradleBuildResult(stdout, stderr, progressListener.getEvents(), null);

            lastBuildResultConsumer.accept(buildResult);

            return Pair.of(model, buildResult);
        } catch (GradleConnectionException e) {
            lastBuildResultConsumer.accept(
                    new GradleBuildResult(stdout, stderr, progressListener.getEvents(), e));
            maybePrintJvmLogs(e);
            throw e;
        }
    }

    private ModelContainer<AndroidProject> assertNoSyncIssues(
            @NonNull ModelContainer<AndroidProject> container) {
        Set<String> allowedOptions = Sets.union(explicitlyAllowedOptions, getOptionPropertyNames());
        container
                .getModelMaps()
                .entrySet()
                .stream()
                .flatMap(
                        entry ->
                                entry.getValue()
                                        .entrySet()
                                        .stream()
                                        .map(
                                                entry2 ->
                                                        Pair.of(
                                                                entry.getKey().getRootDir()
                                                                        + "@@"
                                                                        + entry2.getKey(),
                                                                entry2.getValue())))
                .forEach(projectPair -> assertNoSyncIssues(projectPair, allowedOptions));
        return container;
    }

    private void assertNoSyncIssues(
            @NonNull Pair<String, AndroidProject> projectPair,
            @NonNull Set<String> allowedOptions) {
        List<SyncIssue> issues =
                projectPair
                        .getSecond()
                        .getSyncIssues()
                        .stream()
                        .filter(syncIssue -> syncIssue.getSeverity() > maxSyncIssueSeverityLevel)
                        .filter(syncIssue -> syncIssue.getType() != SyncIssue.TYPE_DEPRECATED_DSL)
                        .filter(
                                syncIssue ->
                                        syncIssue.getType()
                                                        != SyncIssue
                                                                .TYPE_UNSUPPORTED_PROJECT_OPTION_USE
                                                || !allowedOptions.contains(syncIssue.getData()))
                        .collect(Collectors.toList());

        if (!issues.isEmpty()) {
            fail(
                    "project "
                            + projectPair.getFirst()
                            + " had sync issues: "
                            + Joiner.on("\n").join(issues));
        }
    }
}
