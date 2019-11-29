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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.ProjectBuildOutput;
import com.android.ddmlib.IDevice;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ConfigurableLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.OperationType;

/** A Gradle tooling api build builder. */
public final class GradleTaskExecutor extends BaseGradleExecutor<GradleTaskExecutor> {

    @Nullable private final String buildToolsVersion;

    private boolean isExpectingFailure = false;
    private boolean queryOutputModel = false;

    GradleTaskExecutor(
            @NonNull GradleTestProject gradleTestProject,
            @NonNull ProjectConnection projectConnection) {
        super(
                projectConnection,
                gradleTestProject::setLastBuildResult,
                gradleTestProject.getTestDir().toPath(),
                gradleTestProject.getBuildFile().toPath(),
                gradleTestProject.getProfileDirectory(),
                gradleTestProject.getHeapSize());
        buildToolsVersion = gradleTestProject.getBuildToolsVersion();
    }

    /**
     * Assert that the task called fails.
     *
     * <p>The resulting exception is stored in the {@link GradleBuildResult}.
     */
    public GradleTaskExecutor expectFailure() {
        isExpectingFailure = true;
        return this;
    }

    /** Retrieve the ProjectBuildOutput models along with the build */
    public GradleTaskExecutor withOutputModelQuery() {
        queryOutputModel = true;
        return this;
    }

    /**
     * Inject the instant run arguments.
     *
     * @param androidVersion The target device version
     * @param flags additional instant run flags, see {@link OptionalCompilationStep}.
     */
    public GradleTaskExecutor withInstantRun(
            AndroidVersion androidVersion, @NonNull OptionalCompilationStep... flags) {
        setInstantRunArgs(androidVersion, null /* density */, flags);
        return this;
    }

    /**
     * Inject the instant run arguments.
     *
     * @param device The connected device.
     * @param flags additional instant run flags, see {@link OptionalCompilationStep}.
     */
    public GradleTaskExecutor withInstantRun(
            @NonNull IDevice device, @NonNull OptionalCompilationStep... flags) {
        setInstantRunArgs(
                device.getVersion(), Density.getEnum(device.getDensity()), flags);
        return this;
    }

    /**
     * Call connected check.
     *
     * <p>Uses deviceCheck in the background to support the device pool.
     */
    public GradleBuildResult executeConnectedCheck() throws IOException, InterruptedException {
        return run("deviceCheck");
    }

    /** Execute the specified tasks */
    public GradleBuildResult run(@NonNull String... tasks)
            throws IOException, InterruptedException {
        return run(ImmutableList.copyOf(tasks));
    }

    public GradleBuildResult run(@NonNull List<String> tasksList)
            throws IOException, InterruptedException {
        assertThat(tasksList).named("tasks list").isNotEmpty();

        TestUtils.waitForFileSystemTick();

        List<String> args = Lists.newArrayList();
        args.addAll(getArguments());

        if (buildToolsVersion != null) {
            args.add("-PCUSTOM_BUILDTOOLS=" + buildToolsVersion);
        }
        if (!isExpectingFailure) {
            args.add("--stacktrace");
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String message =
                "[GradleTestProject "
                        + projectDirectory
                        + "] Executing tasks: \ngradle "
                        + Joiner.on(' ').join(args)
                        + " "
                        + Joiner.on(' ').join(tasksList)
                        + "\n\n";
        stdout.write(message.getBytes());

        ConfigurableLauncher launcher;
        Supplier<ModelContainer<ProjectBuildOutput>> runBuild;
        if (queryOutputModel) {
            BuildActionExecuter<ModelContainer<ProjectBuildOutput>> actionExecutor =
                    projectConnection
                            .action(new GetAndroidModelAction<>(ProjectBuildOutput.class))
                            .forTasks(Iterables.toArray(tasksList, String.class));
            runBuild = actionExecutor::run;
            launcher = actionExecutor;
        } else {
            BuildLauncher buildLauncher =
                    projectConnection
                            .newBuild()
                            .forTasks(Iterables.toArray(tasksList, String.class));
            runBuild =
                    () -> {
                        buildLauncher.run();
                        return null;
                    };
            launcher = buildLauncher;
        }

        setJvmArguments(launcher);
        setStandardOut(launcher, stdout);
        setStandardError(launcher, stderr);

        CollectingProgressListener progressListener = new CollectingProgressListener();

        launcher.addProgressListener(progressListener, OperationType.TASK);

        launcher.withArguments(Iterables.toArray(args, String.class));

        GradleConnectionException failure = null;
        ModelContainer<ProjectBuildOutput> outputModelContainer = null;
        try {
            outputModelContainer = runBuild.get();
        } catch (GradleConnectionException e) {
            failure = e;
        }

        GradleBuildResult result =
                new GradleBuildResult(
                        stdout,
                        stderr,
                        progressListener.getEvents(),
                        failure,
                        outputModelContainer);
        lastBuildResultConsumer.accept(result);

        if (isExpectingFailure && failure == null) {
            throw new AssertionError("Expecting build to fail");
        } else if (!isExpectingFailure && failure != null) {
            maybePrintJvmLogs(failure);
            throw failure;
        }
        return result;
    }

    private void setInstantRunArgs(
            @Nullable AndroidVersion androidVersion,
            @Nullable Density density,
            @NonNull OptionalCompilationStep[] flags) {
        if (androidVersion != null) {
            with(IntegerOption.IDE_TARGET_DEVICE_API, androidVersion.getApiLevel());
            if (androidVersion.getCodename() != null) {
                with(StringOption.IDE_TARGET_DEVICE_CODENAME, androidVersion.getCodename());
            }
        }

        if (density != null) {
            with(StringOption.IDE_BUILD_TARGET_DENSITY, density.getResourceValue());
        }

        Set<OptionalCompilationStep> steps = EnumSet.of(OptionalCompilationStep.INSTANT_DEV);
        steps.addAll(Arrays.asList(flags));

        with(
                StringOption.IDE_OPTIONAL_COMPILATION_STEPS,
                steps.stream().map(OptionalCompilationStep::name).collect(Collectors.joining(",")));
    }
}
