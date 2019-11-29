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

package com.android.build.gradle.internal.tasks;

import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_RESULTS;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_TESTS;
import static com.android.builder.core.BuilderConstants.FD_FLAVORS;
import static com.android.builder.core.BuilderConstants.FD_REPORTS;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.android.sdklib.BuildToolInfo.PathId.SPLIT_SELECT;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.InstrumentationTestAnalytics;
import com.android.build.gradle.internal.test.report.CompositeTestResults;
import com.android.build.gradle.internal.test.report.ReportType;
import com.android.build.gradle.internal.test.report.TestReport;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.internal.testing.SimpleTestRunnable;
import com.android.builder.model.TestOptions;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.OnDeviceOrchestratorTestRunner;
import com.android.builder.testing.ShardedTestRunner;
import com.android.builder.testing.SimpleTestRunner;
import com.android.builder.testing.TestRunner;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.logging.ConsoleRenderer;
import org.xml.sax.SAXException;

/** Run instrumentation tests for a given variant */
public class DeviceProviderInstrumentTestTask extends AndroidBuilderTask
        implements AndroidTestTask {

    private static final Predicate<File> IS_APK =
            file -> SdkConstants.EXT_ANDROID_PACKAGE.equals(Files.getFileExtension(file.getName()));

    private interface TestRunnerFactory {
        TestRunner build(@Nullable File splitSelectExec, @NonNull ProcessExecutor processExecutor);
    }

    private DeviceProvider deviceProvider;
    private Provider<Directory> coverageDir;
    private File reportsDir;
    private File resultsDir;
    private FileCollection buddyApks;
    private FileCollection testTargetManifests;
    private ProcessExecutor processExecutor;
    private String flavorName;
    private Supplier<File> splitSelectExec;
    private AbstractTestDataImpl testData;
    private TestRunnerFactory testRunnerFactory;
    private boolean ignoreFailures;
    private boolean testFailed;

    // For analytics only
    private boolean codeCoverageEnabled;
    private TestOptions.Execution testExecution;
    private Configuration dependencies;
    
    /**
     * The workers object is of type ExecutorServiceAdapter instead of WorkerExecutorFacade to
     * assert that the object returned is of type ExecutorServiceAdapter as Gradle workers can not
     * be used here because the device tests doesn't run within gradle.
     */
    private final ExecutorServiceAdapter executor;

    @Nullable private Collection<String> installOptions;

    public DeviceProviderInstrumentTestTask() {
        executor = new ExecutorServiceAdapter(ForkJoinPool.commonPool());
    }

    @TaskAction
    protected void runTests() throws DeviceException, IOException, InterruptedException,
            TestRunner.NoAuthorizedDeviceFoundException, TestException,
            ParserConfigurationException, SAXException {
        checkForNonApks(
                buddyApks.getFiles(),
                message -> {
                    throw new InvalidUserDataException(message);
                });

        File resultsOutDir = getResultsDir();
        FileUtils.cleanOutputDir(resultsOutDir);

        File coverageOutDir = getCoverageDir();
        FileUtils.cleanOutputDir(coverageOutDir);

        // populate the TestData from the tested variant build output.
        if (!testTargetManifests.isEmpty()) {
            testData.loadFromMetadataFile(ExistingBuildElements.getMetadataFile(
                    testTargetManifests.getSingleFile()));
        }

        boolean success;
        // If there are tests to run, and the test runner returns with no results, we fail (since
        // this is most likely a problem with the device setup). If no, the task will succeed.
        if (!testsFound()) {
            getLogger().info("No tests found, nothing to do.");
            // If we don't create the coverage file, createXxxCoverageReport task will fail.
            File emptyCoverageFile = new File(coverageOutDir, SimpleTestRunnable.FILE_COVERAGE_EC);
            emptyCoverageFile.createNewFile();
            success = true;
        } else {
            deviceProvider.init();

            TestRunner testRunner =
                    testRunnerFactory.build(splitSelectExec.get(), getProcessExecutor());

            Collection<String> extraArgs =
                    installOptions == null || installOptions.isEmpty()
                            ? ImmutableList.of()
                            : installOptions;
            try {
                success =
                        testRunner.runTests(
                                getProject().getName(),
                                getFlavorName(),
                                testData,
                                buddyApks.getFiles(),
                                deviceProvider.getDevices(),
                                deviceProvider.getTimeoutInMs(),
                                extraArgs,
                                resultsOutDir,
                                coverageOutDir,
                                getILogger());
            } catch (Exception e) {
                InstrumentationTestAnalytics.recordCrashedTestRun(
                        dependencies, testExecution, codeCoverageEnabled);
                throw e;
            } finally {
                deviceProvider.terminate();
            }

        }

        // run the report from the results.
        File reportOutDir = getReportsDir();
        FileUtils.cleanOutputDir(reportOutDir);

        TestReport report = new TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir);
        CompositeTestResults results = report.generateReport();

        InstrumentationTestAnalytics.recordOkTestRun(
                dependencies, testExecution, codeCoverageEnabled, results.getTestCount());

        if (!success) {
            testFailed = true;
            String reportUrl = new ConsoleRenderer().asClickableFileUrl(
                    new File(reportOutDir, "index.html"));
            String message = "There were failing tests. See the report at: " + reportUrl;
            if (getIgnoreFailures()) {
                getLogger().warn(message);
                return;

            } else {
                throw new GradleException(message);
            }
        }

        testFailed = false;
    }

    public static void checkForNonApks(
            @NonNull Collection<File> buddyApksFiles, @NonNull Consumer<String> errorHandler) {
        List<File> nonApks =
                buddyApksFiles.stream().filter(IS_APK.negate()).collect(Collectors.toList());
        if (!nonApks.isEmpty()) {
            Collections.sort(nonApks);
            String message =
                    String.format(
                            "Not all files in %s configuration are APKs: %s",
                            SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION,
                            Joiner.on(' ').join(nonApks));
            errorHandler.accept(message);
        }
    }

    /**
     * Determines if there are any tests to run.
     *
     * @return true if there are some tests to run, false otherwise
     */
    private boolean testsFound() {
        // For now we check if there are any test sources. We could inspect the test classes and
        // apply JUnit logic to see if there's something to run, but that would not catch the case
        // where user makes a typo in a test name or forgets to inherit from a JUnit class
        return !getProject().files(testData.getTestDirectories()).getAsFileTree().isEmpty();
    }

    public File getReportsDir() {
        return reportsDir;
    }

    public void setReportsDir(File reportsDir) {
        this.reportsDir = reportsDir;
    }

    @Override
    public File getResultsDir() {
        return resultsDir;
    }

    public void setResultsDir(File resultsDir) {
        this.resultsDir = resultsDir;
    }

    @OutputDirectory
    public File getCoverageDir() {
        return coverageDir.get().getAsFile();
    }

    @Deprecated
    public void setCoverageDir(File coverageDir) {
        getLogger()
                .info(
                        "DeviceProviderInstrumentTestTask.setCoverageDir is deprecated and has no effect.");
    }

    public String getFlavorName() {
        return flavorName;
    }

    public void setFlavorName(String flavorName) {
        this.flavorName = flavorName;
    }

    public Collection<String> getInstallOptions() {
        return installOptions;
    }

    public void setInstallOptions(Collection<String> installOptions) {
        this.installOptions = installOptions;
    }

    public DeviceProvider getDeviceProvider() {
        return deviceProvider;
    }

    public void setDeviceProvider(DeviceProvider deviceProvider) {
        this.deviceProvider = deviceProvider;
    }

    public AbstractTestDataImpl getTestData() {
        return testData;
    }

    public void setTestData(@NonNull AbstractTestDataImpl testData) {
        this.testData = testData;
    }

    @InputFile
    public File getSplitSelectExec() {
        return splitSelectExec.get();
    }

    public ProcessExecutor getProcessExecutor() {
        return processExecutor;
    }

    public void setProcessExecutor(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    @Override
    public boolean getTestFailed() {
        return testFailed;
    }

    /**
     * Indirectly used through the TestData, declare it as a dependency so the wiring is done
     * correctly.
     *
     * @return tested variant metadata file.
     */
    @InputFiles
    public FileCollection getTestTargetManifests() {
        return testTargetManifests;
    }

    @InputFiles
    public FileCollection getBuddyApks() {
        return buddyApks;
    }

    @InputFiles
    public BuildableArtifact getTestApkDir() {
        return testData.getTestApkDir();
    }

    @InputFiles
    @Optional
    public BuildableArtifact getTestedApksDir() {
        return testData.getTestedApksDir();
    }

    @InputFiles
    @Optional
    public FileCollection getTestedApksFromBundle() {
        return testData.getTestedApksFromBundle();
    }

    public static class CreationAction
            extends VariantTaskCreationAction<DeviceProviderInstrumentTestTask> {

        @NonNull
        private final DeviceProvider deviceProvider;
        @NonNull private final AbstractTestDataImpl testData;
        @NonNull private final FileCollection testTargetManifests;
        private Provider<Directory> coverageDir;

        public CreationAction(
                @NonNull VariantScope scope,
                @NonNull DeviceProvider deviceProvider,
                @NonNull AbstractTestDataImpl testData,
                @NonNull FileCollection testTargetManifests) {
            super(scope);
            this.deviceProvider = deviceProvider;
            this.testData = testData;
            this.testTargetManifests = testTargetManifests;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName(deviceProvider.getName());
        }

        @NonNull
        @Override
        public Class<DeviceProviderInstrumentTestTask> getType() {
            return DeviceProviderInstrumentTestTask.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            if (deviceProvider instanceof ConnectedDeviceProvider) {
                coverageDir =
                        getVariantScope()
                                .getArtifacts()
                                .createDirectory(
                                        InternalArtifactType.CODE_COVERAGE,
                                        taskName,
                                        deviceProvider.getName());
            } else {
                coverageDir =
                        getVariantScope()
                                .getArtifacts()
                                .createDirectory(
                                        InternalArtifactType.DEVICE_PROVIDER_CODE_COVERAGE,
                                        BuildArtifactsHolder.OperationType.APPEND,
                                        taskName,
                                        deviceProvider.getName());
            }
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends DeviceProviderInstrumentTestTask> taskProvider) {
            super.handleProvider(taskProvider);
            VariantScope scope = getVariantScope();
            if (scope.getVariantData() instanceof TestVariantData) {
                if (deviceProvider instanceof ConnectedDeviceProvider) {
                    scope.getTaskContainer().setConnectedTestTask(taskProvider);
                    // possible redundant with setConnectedTestTask?
                    scope.getTaskContainer().setConnectedTask(taskProvider);
                } else {
                    scope.getTaskContainer().getProviderTestTaskList().add(taskProvider);
                }
            }
        }

        @Override
        public void configure(@NonNull DeviceProviderInstrumentTestTask task) {
            super.configure(task);

            VariantScope scope = getVariantScope();
            Project project = scope.getGlobalScope().getProject();
            ProjectOptions projectOptions = scope.getGlobalScope().getProjectOptions();

            final boolean connected = deviceProvider instanceof ConnectedDeviceProvider;


            BaseVariantData testedVariantData = scope.getTestedVariantData();

            String variantName =
                    testedVariantData != null
                            ? testedVariantData.getName()
                            : scope.getVariantData().getName();
            if (connected) {
                task.setDescription("Installs and runs the tests for " + variantName +
                        " on connected devices.");
            } else {
                task.setDescription(
                        StringHelper.appendCapitalized(
                                "Installs and runs the tests for "
                                        + variantName
                                        + " using provider: ",
                                deviceProvider.getName()));
            }
            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            task.setTestData(testData);
            task.setFlavorName(testData.getFlavorName());
            task.setDeviceProvider(deviceProvider);
            task.testTargetManifests = testTargetManifests;
            task.setInstallOptions(
                    scope.getGlobalScope().getExtension().getAdbOptions().getInstallOptions());
            task.setProcessExecutor(
                    scope.getGlobalScope().getAndroidBuilder().getProcessExecutor());

            boolean shardBetweenDevices = projectOptions.get(BooleanOption.ENABLE_TEST_SHARDING);

            final TestOptions.Execution executionEnum =
                    scope.getGlobalScope().getExtension().getTestOptions().getExecutionEnum();
            switch (executionEnum) {
                case ANDROID_TEST_ORCHESTRATOR:
                case ANDROIDX_TEST_ORCHESTRATOR:
                    Preconditions.checkArgument(
                            !shardBetweenDevices,
                            "Sharding is not supported with Android Test Orchestrator.");
                    task.testRunnerFactory =
                            (splitSelect, processExecutor) ->
                                    new OnDeviceOrchestratorTestRunner(
                                            splitSelect,
                                            processExecutor,
                                            executionEnum,
                                            task.executor);
                    break;
                case HOST:
                    if (shardBetweenDevices) {
                        Integer numShards =
                                projectOptions.get(IntegerOption.ANDROID_TEST_SHARD_COUNT);
                        task.testRunnerFactory =
                                (splitSelect, processExecutor) ->
                                        new ShardedTestRunner(
                                                splitSelect,
                                                processExecutor,
                                                numShards,
                                                task.executor);
                    } else {
                        task.testRunnerFactory =
                                (splitSelect, processExecutor) ->
                                        new SimpleTestRunner(
                                                splitSelect, processExecutor, task.executor);
                    }
                    break;
                default:
                    throw new AssertionError("Unknown value " + executionEnum);
            }
            task.codeCoverageEnabled = scope.getVariantConfiguration().isTestCoverageEnabled();
            task.dependencies = scope.getVariantDependencies().getRuntimeClasspath();
            task.testExecution = executionEnum;

            String flavorFolder = testData.getFlavorName();
            if (!flavorFolder.isEmpty()) {
                flavorFolder = FD_FLAVORS + "/" + flavorFolder;
            }
            String providerFolder = connected ? CONNECTED : DEVICE + "/" + deviceProvider.getName();
            final String subFolder = "/" + providerFolder + "/" + flavorFolder;

            task.splitSelectExec = TaskInputHelper.memoize(() -> {
                // SDK is loaded somewhat dynamically, plus we don't want to do all this logic
                // if the task is not going to run, so use a supplier.
                final TargetInfo info = scope.getGlobalScope().getAndroidBuilder()
                        .getTargetInfo();
                String path = info == null ? null : info.getBuildTools().getPath(SPLIT_SELECT);
                if (path != null) {
                    File splitSelectExe = new File(path);
                    return splitSelectExe.exists() ? splitSelectExe : null;
                } else {
                    return null;
                }
            });

            String rootLocation = scope.getGlobalScope().getExtension().getTestOptions()
                    .getResultsDir();
            if (rootLocation == null) {
                rootLocation = scope.getGlobalScope().getBuildDir() + "/" +
                        FD_OUTPUTS + "/" + FD_ANDROID_RESULTS;
            }
            task.resultsDir = project.file(rootLocation + subFolder);

            rootLocation = scope.getGlobalScope().getExtension().getTestOptions().getReportDir();
            if (rootLocation == null) {
                rootLocation = scope.getGlobalScope().getBuildDir() + "/" +
                        FD_REPORTS + "/" + FD_ANDROID_TESTS;
            }
            task.reportsDir = project.file(rootLocation + subFolder);

            task.coverageDir = coverageDir;

            // The configuration is not created by the experimental plugin, so just create an empty
            // FileCollection in this case.
            task.buddyApks =
                    MoreObjects.firstNonNull(
                            project.getConfigurations()
                                    .findByName(
                                            SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION),
                            project.files());

            task.setEnabled(deviceProvider.isConfigured());

            // outputs are never up-to-date
            task.getOutputs().upToDateWhen(t -> false);

            // FIXME these should be task inputs!
            // depends on the test APK
            task.dependsOn(scope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.APK));

            if (testedVariantData != null) {
                task.dependsOn(
                        testedVariantData
                                .getScope()
                                .getArtifacts()
                                .getFinalArtifactFiles(InternalArtifactType.APK));

            } else {
                // this is a separate the test project, we should consume the BA for the tested
                // app
                // FIXME handle separate test project BA

            }
        }
    }
}
