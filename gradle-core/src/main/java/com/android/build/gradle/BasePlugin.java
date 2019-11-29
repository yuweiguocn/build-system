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

package com.android.build.gradle;

import static com.google.common.base.Preconditions.checkState;
import static java.io.File.separator;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.api.AndroidBasePlugin;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApiObjectFactory;
import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.ClasspathVerifier;
import com.android.build.gradle.internal.DependencyResolutionChecks;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NonFinalPluginExpiry;
import com.android.build.gradle.internal.PluginInitializer;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2;
import com.android.build.gradle.internal.crash.CrashReporting;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.BuildTypeFactory;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.ProductFlavorFactory;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.SigningConfigFactory;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.errors.DeprecationReporterImpl;
import com.android.build.gradle.internal.ide.ModelBuilder;
import com.android.build.gradle.internal.ide.NativeModelBuilder;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.plugin.PluginDelegate;
import com.android.build.gradle.internal.plugin.ProjectWrapper;
import com.android.build.gradle.internal.plugin.TypedPluginDelegate;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.profile.ProfilerInitializer;
import com.android.build.gradle.internal.scope.DelayedActionsExecutor;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.utils.GradlePluginUtils;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant2.DslScopeImpl;
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.options.SyncOptions;
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode;
import com.android.build.gradle.tasks.LintBaseTask;
import com.android.build.gradle.tasks.factory.AbstractCompilesUtil;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Version;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.builder.sdk.SdkLibData;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.dx.command.dexer.Main;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.Downloader;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.downloader.LocalFileAwareDownloader;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.tools.lint.gradle.api.ToolingRegistryProvider;
import com.android.utils.ILogger;
import com.google.common.base.CharMatcher;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.io.File;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Base class for all Android plugins */
public abstract class BasePlugin<E extends BaseExtension2>
        implements Plugin<Project>, ToolingRegistryProvider {

    @VisibleForTesting
    public static final GradleVersion GRADLE_MIN_VERSION =
            GradleVersion.parse(SdkConstants.GRADLE_MINIMUM_VERSION);

    private BaseExtension extension;

    private VariantManager variantManager;

    protected TaskManager taskManager;

    protected Project project;

    protected ProjectOptions projectOptions;

    private GlobalScope globalScope;

    private SdkHandler sdkHandler;

    private DataBindingBuilder dataBindingBuilder;

    private VariantFactory variantFactory;

    private SourceSetManager sourceSetManager;

    private ToolingModelBuilderRegistry registry;

    private LoggerWrapper loggerWrapper;

    protected ExtraModelInfo extraModelInfo;

    private String creator;

    private Recorder threadRecorder;

    private boolean hasCreatedTasks = false;

    private ExecutorService nativeJsonGenExecutor = null;

    BasePlugin(@NonNull ToolingModelBuilderRegistry registry) {
        ClasspathVerifier.checkClasspathSanity();
        this.registry = registry;
        creator = "Android Gradle " + Version.ANDROID_GRADLE_PLUGIN_VERSION;
        NonFinalPluginExpiry.verifyRetirementAge();
    }

    @NonNull
    protected abstract BaseExtension createExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo);

    @NonNull
    protected abstract GradleBuildProject.PluginType getAnalyticsPluginType();

    @NonNull
    protected abstract VariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig androidConfig);

    @NonNull
    protected abstract TaskManager createTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig androidConfig,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder);

    protected abstract int getProjectType();

    @VisibleForTesting
    public VariantManager getVariantManager() {
        return variantManager;
    }

    public BaseExtension getExtension() {
        return extension;
    }

    @VisibleForTesting
    AndroidBuilder getAndroidBuilder() {
        return globalScope.getAndroidBuilder();
    }

    private ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.getLogger());
        }

        return loggerWrapper;
    }

    @Override
    public final void apply(@NonNull Project project) {
        CrashReporting.runAction(
                () -> {
                    basePluginApply(project);
                    pluginSpecificApply(project);
                });
    }

    private void basePluginApply(@NonNull Project project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        this.project = project;
        this.projectOptions = new ProjectOptions(project);
        checkGradleVersion(project, getLogger(), projectOptions);
        DependencyResolutionChecks.registerDependencyCheck(project, projectOptions);

        project.getPluginManager().apply(AndroidBasePlugin.class);

        checkPathForErrors();
        checkModulesForErrors();

        PluginInitializer.initialize(project);
        ProfilerInitializer.init(project, projectOptions);
        threadRecorder = ThreadRecorder.get();

        // initialize our workers using the project's options.
        Workers.INSTANCE.initFromProject(
                projectOptions,
                // possibly, in the future, consider using a pool with a dedicated size
                // using the gradle parallelism settings.
                ForkJoinPool.commonPool());

        ProcessProfileWriter.getProject(project.getPath())
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(getAnalyticsPluginType())
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
                .setOptions(AnalyticsUtil.toProto(projectOptions));

        if (!projectOptions.get(BooleanOption.ENABLE_NEW_DSL_AND_API)) {

            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                    project.getPath(),
                    null,
                    this::configureProject);

            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                    project.getPath(),
                    null,
                    this::configureExtension);

            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                    project.getPath(),
                    null,
                    this::createTasks);
        } else {
            // Apply the Java plugin
            project.getPlugins().apply(JavaBasePlugin.class);

            // create the delegate
            ProjectWrapper projectWrapper = new ProjectWrapper(project);
            PluginDelegate<E> delegate =
                    new PluginDelegate<>(
                            project.getPath(),
                            project.getObjects(),
                            project.getExtensions(),
                            project.getConfigurations(),
                            projectWrapper,
                            projectWrapper,
                            project.getLogger(),
                            projectOptions,
                            getTypedDelegate());

            delegate.prepareForEvaluation();

            // after evaluate callbacks
            project.afterEvaluate(
                    CrashReporting.afterEvaluate(
                            p -> {
                                threadRecorder.record(
                                        ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                        p.getPath(),
                                        null,
                                        delegate::afterEvaluate);
                            }));
        }
    }

    protected abstract void pluginSpecificApply(@NonNull Project project);

    /**
     * Returns the typed plugin delegate.
     *
     * <p>This is the delegate that is specific to the actual plugin that is applied (app, lib,
     * etc...)
     *
     * <p>In the long term when the old code path is removed this can be passed via the constructor.
     *
     * @return the typed delegate
     */
    protected abstract TypedPluginDelegate<E> getTypedDelegate();

    private void configureProject() {
        final Gradle gradle = project.getGradle();
        ObjectFactory objectFactory = project.getObjects();

        extraModelInfo = new ExtraModelInfo(project.getPath(), projectOptions, project.getLogger());

        sdkHandler = new SdkHandler(project, getLogger());
        if (!gradle.getStartParameter().isOffline()
                && projectOptions.get(BooleanOption.ENABLE_SDK_DOWNLOAD)) {
            SdkLibData sdkLibData = SdkLibData.download(getDownloader(), getSettingsController());
            sdkHandler.setSdkLibData(sdkLibData);
        }

        AndroidBuilder androidBuilder =
                new AndroidBuilder(
                        project == project.getRootProject() ? project.getName() : project.getPath(),
                        creator,
                        new GradleProcessExecutor(project),
                        new GradleJavaProcessExecutor(project),
                        extraModelInfo.getSyncIssueHandler(),
                        extraModelInfo.getMessageReceiver(),
                        getLogger());
        dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(
                SyncOptions.getErrorFormatMode(projectOptions) == ErrorFormatMode.MACHINE_PARSABLE);

        if (projectOptions.hasRemovedOptions()) {
            androidBuilder
                    .getIssueReporter()
                    .reportWarning(Type.GENERIC, projectOptions.getRemovedOptionsErrorMessage());
        }

        if (projectOptions.hasDeprecatedOptions()) {
            extraModelInfo
                    .getDeprecationReporter()
                    .reportDeprecatedOptions(projectOptions.getDeprecatedOptions());
        }

        if (!projectOptions.getExperimentalOptions().isEmpty()) {
            projectOptions
                    .getExperimentalOptions()
                    .forEach(extraModelInfo.getDeprecationReporter()::reportExperimentalOption);
        }

        // Enforce minimum versions of certain plugins
        GradlePluginUtils.enforceMinimumVersionsOfPlugins(
                project, androidBuilder.getIssueReporter());

        // Apply the Java plugin
        project.getPlugins().apply(JavaBasePlugin.class);

        DslScopeImpl dslScope =
                new DslScopeImpl(
                        extraModelInfo.getSyncIssueHandler(),
                        extraModelInfo.getDeprecationReporter(),
                        objectFactory);

        @Nullable
        FileCache buildCache = BuildCacheUtils.createBuildCacheIfEnabled(project, projectOptions);

        globalScope =
                new GlobalScope(
                        project,
                        new ProjectWrapper(project),
                        projectOptions,
                        dslScope,
                        androidBuilder,
                        sdkHandler,
                        registry,
                        buildCache);

        project.getTasks()
                .getByName("assemble")
                .setDescription(
                        "Assembles all variants of all applications and secondary packages.");

        // call back on execution. This is called after the whole build is done (not
        // after the current project is done).
        // This is will be called for each (android) projects though, so this should support
        // being called 2+ times.
        gradle.addBuildListener(
                new BuildListener() {
                    @Override
                    public void buildStarted(@NonNull Gradle gradle) {}

                    @Override
                    public void settingsEvaluated(@NonNull Settings settings) {}

                    @Override
                    public void projectsLoaded(@NonNull Gradle gradle) {}

                    @Override
                    public void projectsEvaluated(@NonNull Gradle gradle) {}

                    @Override
                    public void buildFinished(@NonNull BuildResult buildResult) {
                        // Do not run buildFinished for included project in composite build.
                        if (buildResult.getGradle().getParent() != null) {
                            return;
                        }
                        ModelBuilder.clearCaches();
                        sdkHandler.unload();
                        threadRecorder.record(
                                ExecutionType.BASE_PLUGIN_BUILD_FINISHED,
                                project.getPath(),
                                null,
                                () -> {
                                    WorkerActionServiceRegistry.INSTANCE
                                            .shutdownAllRegisteredServices(
                                                    ForkJoinPool.commonPool());
                                    Main.clearInternTables();
                                });
                        DeprecationReporterImpl.Companion.clean();
                    }
                });

        createLintClasspathConfiguration(project);
    }

    /** Creates a lint class path Configuration for the given project */
    public static void createLintClasspathConfiguration(@NonNull Project project) {
        Configuration config = project.getConfigurations().create(LintBaseTask.LINT_CLASS_PATH);
        config.setVisible(false);
        config.setTransitive(true);
        config.setCanBeConsumed(false);
        config.setDescription("The lint embedded classpath");

        project.getDependencies().add(config.getName(), "com.android.tools.lint:lint-gradle:" +
                Version.ANDROID_TOOLS_BASE_VERSION);
    }

    private void configureExtension() {
        ObjectFactory objectFactory = project.getObjects();
        final NamedDomainObjectContainer<BuildType> buildTypeContainer =
                project.container(
                        BuildType.class,
                        new BuildTypeFactory(
                                objectFactory,
                                project,
                                extraModelInfo.getSyncIssueHandler(),
                                extraModelInfo.getDeprecationReporter()));
        final NamedDomainObjectContainer<ProductFlavor> productFlavorContainer =
                project.container(
                        ProductFlavor.class,
                        new ProductFlavorFactory(
                                objectFactory,
                                project,
                                extraModelInfo.getDeprecationReporter(),
                                project.getLogger()));
        final NamedDomainObjectContainer<SigningConfig> signingConfigContainer =
                project.container(
                        SigningConfig.class,
                        new SigningConfigFactory(
                                objectFactory,
                                GradleKeystoreHelper.getDefaultDebugKeystoreLocation()));

        final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs =
                project.container(BaseVariantOutput.class);

        project.getExtensions().add("buildOutputs", buildOutputs);

        sourceSetManager =
                new SourceSetManager(
                        project,
                        isPackagePublished(),
                        globalScope.getDslScope(),
                        new DelayedActionsExecutor());

        extension =
                createExtension(
                        project,
                        projectOptions,
                        globalScope,
                        sdkHandler,
                        buildTypeContainer,
                        productFlavorContainer,
                        signingConfigContainer,
                        buildOutputs,
                        sourceSetManager,
                        extraModelInfo);

        globalScope.setExtension(extension);

        variantFactory = createVariantFactory(globalScope, extension);

        taskManager =
                createTaskManager(
                        globalScope,
                        project,
                        projectOptions,
                        dataBindingBuilder,
                        extension,
                        sdkHandler,
                        variantFactory,
                        registry,
                        threadRecorder);

        variantManager =
                new VariantManager(
                        globalScope,
                        project,
                        projectOptions,
                        extension,
                        variantFactory,
                        taskManager,
                        sourceSetManager,
                        threadRecorder);

        registerModels(registry, globalScope, variantManager, extension, extraModelInfo);

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded(variantManager::addSigningConfig);

        buildTypeContainer.whenObjectAdded(
                buildType -> {
                    if (!this.getClass().isAssignableFrom(DynamicFeaturePlugin.class)) {
                        SigningConfig signingConfig =
                                signingConfigContainer.findByName(BuilderConstants.DEBUG);
                        buildType.init(signingConfig);
                    } else {
                        // initialize it without the signingConfig for dynamic-features.
                        buildType.init();
                    }
                    variantManager.addBuildType(buildType);
                });

        productFlavorContainer.whenObjectAdded(variantManager::addProductFlavor);

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved(
                new UnsupportedAction("Removing signingConfigs is not supported."));
        buildTypeContainer.whenObjectRemoved(
                new UnsupportedAction("Removing build types is not supported."));
        productFlavorContainer.whenObjectRemoved(
                new UnsupportedAction("Removing product flavors is not supported."));

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(
                buildTypeContainer, productFlavorContainer, signingConfigContainer);
    }

    protected void registerModels(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantManager variantManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo) {
        // Register a builder for the custom tooling model
        registerModelBuilder(registry, globalScope, variantManager, config, extraModelInfo);

        // Register a builder for the native tooling model
        NativeModelBuilder nativeModelBuilder = new NativeModelBuilder(globalScope, variantManager);
        registry.register(nativeModelBuilder);
    }

    /** Registers a builder for the custom tooling model. */
    protected void registerModelBuilder(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantManager variantManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo) {
        registry.register(
                new ModelBuilder<>(
                        globalScope,
                        variantManager,
                        taskManager,
                        config,
                        extraModelInfo,
                        getProjectType(),
                        AndroidProject.GENERATION_ORIGINAL));
    }

    private static class UnsupportedAction implements Action<Object> {

        private final String message;

        UnsupportedAction(String message) {
            this.message = message;
        }

        @Override
        public void execute(@NonNull Object o) {
            throw new UnsupportedOperationException(message);
        }
    }

    private void createTasks() {
        threadRecorder.record(
                ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(),
                null,
                () -> taskManager.createTasksBeforeEvaluate());

        project.afterEvaluate(
                CrashReporting.afterEvaluate(
                        p -> {
                            sourceSetManager.runBuildableArtifactsActions();

                            threadRecorder.record(
                                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                    project.getPath(),
                                    null,
                                    this::createAndroidTasks);
                        }));
    }


    static void checkGradleVersion(
            @NonNull Project project,
            @NonNull ILogger logger,
            @NonNull ProjectOptions projectOptions) {
        String currentVersion = project.getGradle().getGradleVersion();
        if (GRADLE_MIN_VERSION.compareTo(currentVersion) > 0) {
            File file = new File("gradle" + separator + "wrapper" + separator +
                    "gradle-wrapper.properties");
            String errorMessage =
                    String.format(
                            "Minimum supported Gradle version is %s. Current version is %s. "
                                    + "If using the gradle wrapper, try editing the distributionUrl in %s "
                                    + "to gradle-%s-all.zip",
                            GRADLE_MIN_VERSION,
                            currentVersion,
                            file.getAbsolutePath(),
                            GRADLE_MIN_VERSION);
            if (projectOptions.get(BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY)) {
                logger.warning(errorMessage);
                logger.warning(
                        "As %s is set, continuing anyway.",
                        BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.getPropertyName());
            } else {
                throw new RuntimeException(errorMessage);
            }
        }
    }

    @VisibleForTesting
    final void createAndroidTasks() {
        // Make sure unit tests set the required fields.
        checkState(extension.getBuildToolsRevision() != null,
                "buildToolsVersion is not specified.");
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");

        globalScope.getNdkHandler().setCompileSdkVersion(extension.getCompileSdkVersion());
        extension
                .getCompileOptions()
                .setDefaultJavaVersion(
                        AbstractCompilesUtil.getDefaultJavaVersion(
                                extension.getCompileSdkVersion()));

        // get current plugins and look for the default Java plugin.
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.");
        }

        if (project.getPlugins().hasPlugin("me.tatarka.retrolambda")) {
            String warningMsg =
                    "One of the plugins you are using supports Java 8 "
                            + "language features. To try the support built into"
                            + " the Android plugin, remove the following from "
                            + "your build.gradle:\n"
                            + "    apply plugin: 'me.tatarka.retrolambda'\n"
                            + "To learn more, go to https://d.android.com/r/"
                            + "tools/java-8-support-message.html\n";
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(EvalIssueReporter.Type.GENERIC, warningMsg);
        }

        boolean targetSetupSuccess = ensureTargetSetup();
        sdkHandler.ensurePlatformToolsIsInstalledWarnOnFailure(
                extraModelInfo.getSyncIssueHandler());
        // Stop trying to configure the project if the SDK is not ready.
        // Sync issues will already have been collected at this point in sync.
        if (!targetSetupSuccess) {
            project.getLogger()
                    .warn("Aborting configuration as SDK is missing components in sync mode.");
            return;
        }

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if ((!project.getState().getExecuted() || project.getState().getFailure() != null)
                && SdkHandler.sTestSdkFolder == null) {
            return;
        }

        if (hasCreatedTasks) {
            return;
        }
        hasCreatedTasks = true;

        extension.disableWrite();

        taskManager.configureCustomLintChecks();

        ProcessProfileWriter.getProject(project.getPath())
                .setCompileSdk(extension.getCompileSdkVersion())
                .setBuildToolsVersion(extension.getBuildToolsRevision().toString())
                .setSplits(AnalyticsUtil.toProto(extension.getSplits()));

        String kotlinPluginVersion = getKotlinPluginVersion();
        if (kotlinPluginVersion != null) {
            ProcessProfileWriter.getProject(project.getPath())
                    .setKotlinPluginVersion(kotlinPluginVersion);
        }

        // setup SDK repositories.
        if (projectOptions.get(BooleanOption.INJECT_SDK_MAVEN_REPOS)) {
            sdkHandler.addLocalRepositories(project);
        }

        List<VariantScope> variantScopes = variantManager.createAndroidTasks();

        ApiObjectFactory apiObjectFactory =
                new ApiObjectFactory(
                        globalScope.getAndroidBuilder(),
                        extension,
                        variantFactory,
                        project.getObjects());
        for (VariantScope variantScope : variantScopes) {
            BaseVariantData variantData = variantScope.getVariantData();
            apiObjectFactory.create(variantData);
        }

        // Make sure no SourceSets were added through the DSL without being properly configured
        // Only do it if we are not restricting to a single variant (with Instant
        // Run or we can find extra source set
        if (projectOptions.get(StringOption.IDE_RESTRICT_VARIANT_NAME) == null) {
            sourceSetManager.checkForUnconfiguredSourceSets();
        }

        // must run this after scopes are created so that we can configure kotlin
        // kapt tasks
        taskManager.addDataBindingDependenciesIfNecessary(
                extension.getDataBinding(), variantManager.getVariantScopes());


        // create the global lint task that depends on all the variants
        taskManager.configureGlobalLintTask(variantManager.getVariantScopes());

        int flavorDimensionCount = 0;
        if (extension.getFlavorDimensionList() != null) {
            flavorDimensionCount = extension.getFlavorDimensionList().size();
        }

        taskManager.createAnchorAssembleTasks(
                variantScopes,
                extension.getProductFlavors().size(),
                flavorDimensionCount,
                variantFactory.getVariantConfigurationTypes().size());

        // now publish all variant artifacts.
        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            variantManager.publishBuildArtifacts(variantScope);
        }

        checkSplitConfiguration();
        variantManager.setHasCreatedTasks(true);
    }

    private void checkSplitConfiguration() {
        String configApkUrl = "https://d.android.com/topic/instant-apps/guides/config-splits.html";

        boolean isFeatureModule = project.getPlugins().hasPlugin(FeaturePlugin.class);
        boolean generatePureSplits = extension.getGeneratePureSplits();
        Splits splits = extension.getSplits();
        boolean splitsEnabled =
                splits.getDensity().isEnable()
                        || splits.getAbi().isEnable()
                        || splits.getLanguage().isEnable();

        // The Play Store doesn't allow Pure splits
        if (!isFeatureModule && generatePureSplits) {
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(
                            Type.GENERIC,
                            "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false. For more information, go to "
                                    + configApkUrl);
        }

        if (!isFeatureModule && !generatePureSplits && splits.getLanguage().isEnable()) {
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(
                            Type.GENERIC,
                            "Per-language APKs are supported only when building Android Instant Apps. For more information, go to "
                                    + configApkUrl);
        }

        if (isFeatureModule && !generatePureSplits && splitsEnabled) {
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(
                            Type.GENERIC,
                            "Configuration APKs targeting different device configurations are "
                                    + "automatically built when splits are enabled for a feature module.\n"
                                    + "To suppress this warning, remove \"generatePureSplits false\" "
                                    + "from your build.gradle file.\n"
                                    + "To learn more, see "
                                    + configApkUrl);
        }
    }

    private boolean ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = globalScope.getAndroidBuilder().getTargetInfo();
        // noinspection VariableNotUsedInsideIf Directly checking if initialized.
        if (targetInfo != null) {
            return true;
        }
        if (extension.getCompileOptions() == null) {
            throw new GradleException("Calling getBootClasspath before compileSdkVersion");
        }

        try {
            return sdkHandler.initTarget(
                    extension.getCompileSdkVersion(),
                    extension.getBuildToolsRevision(),
                    extension.getLibraryRequests(),
                    globalScope.getAndroidBuilder(),
                    SdkHandler.useCachedSdk(projectOptions));

        } catch (SdkHandler.MissingSdkException e) {
            String filePath =
                    new File(project.getRootDir(), SdkConstants.FN_LOCAL_PROPERTIES)
                            .getAbsolutePath();
            String message =
                    "SDK location not found. Define location with an ANDROID_SDK_ROOT environment "
                            + "variable or by setting the sdk.dir path in your project's local "
                            + "properties file at '"
                            + filePath
                            + "'.";
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportError(Type.SDK_NOT_SET, new EvalIssueException(message, filePath, null));
            return false;
        }
    }

    /**
     * Check the sub-projects structure :
     * So far, checks that 2 modules do not have the same identification (group+name).
     */
    private void checkModulesForErrors() {
        Project rootProject = project.getRootProject();
        Map<String, Project> subProjectsById = new HashMap<>();
        for (Project subProject : rootProject.getAllprojects()) {
            String id = subProject.getGroup().toString() + ":" + subProject.getName();
            if (subProjectsById.containsKey(id)) {
                String message = String.format(
                        "Your project contains 2 or more modules with the same " +
                                "identification %1$s\n" +
                                "at \"%2$s\" and \"%3$s\".\n" +
                                "You must use different identification (either name or group) for " +
                                "each modules.",
                        id,
                        subProjectsById.get(id).getPath(),
                        subProject.getPath() );
                throw new StopExecutionException(message);
            } else {
                subProjectsById.put(id, subProject);
            }
        }
    }

    private void checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")) {
            return;
        }

        // See if the user disabled the check:
        if (projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY)) {
            return;
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ascii().matchesAllOf(project.getRootDir().getAbsolutePath())) {
            return;
        }

        String message =
                "Your project path contains non-ASCII characters. This will most likely "
                        + "cause the build to fail on Windows. Please move your project to a different "
                        + "directory. See http://b.android.com/95744 for details. "
                        + "This warning can be disabled by adding the line '"
                        + BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY.getPropertyName()
                        + "=true' to gradle.properties file in the project directory.";

        throw new StopExecutionException(message);
    }

    @NonNull
    @Override
    public ToolingModelBuilderRegistry getModelBuilderRegistry() {
        return registry;
    }

    private SettingsController getSettingsController() {
        Proxy proxy = createProxy(System.getProperties(), getLogger());
        return new SettingsController() {
            @Override
            public boolean getForceHttp() {
                return false;
            }

            @Override
            public void setForceHttp(boolean force) {
                // Default, doesn't allow to set force HTTP.
            }

            @Override
            public boolean getDisableSdkPatches() {
                return true;
            }

            @Override
            public void setDisableSdkPatches(boolean disable) {
                // Default, doesn't allow to enable SDK patches, since this is an IDEA thing.
            }

            @Nullable
            @Override
            public Channel getChannel() {
                Integer channel = projectOptions.get(IntegerOption.ANDROID_SDK_CHANNEL);
                if (channel != null) {
                    return Channel.create(channel);
                } else {
                    return Channel.DEFAULT;
                }
            }

            @NonNull
            @Override
            public Proxy getProxy() {
                return proxy;
            }
        };
    }

    @VisibleForTesting
    static Proxy createProxy(@NonNull Properties properties, @NonNull ILogger logger) {
        String host = properties.getProperty("https.proxyHost");
        int port = 443;
        if (host != null) {
            String maybePort = properties.getProperty("https.proxyPort");
            if (maybePort != null) {
                try {
                    port = Integer.parseInt(maybePort);
                } catch (NumberFormatException e) {
                    logger.lifecycle(
                            "Invalid https.proxyPort '" + maybePort + "', using default 443");
                }
            }
        }
        else {
            host = properties.getProperty("http.proxyHost");
            //noinspection VariableNotUsedInsideIf
            if (host != null) {
                port = 80;
                String maybePort = properties.getProperty("http.proxyPort");
                if (maybePort != null) {
                    try {
                        port = Integer.parseInt(maybePort);
                    } catch (NumberFormatException e) {
                        logger.lifecycle(
                                "Invalid http.proxyPort '" + maybePort + "', using default 80");
                    }
                }
            }
        }
        if (host != null) {
            InetSocketAddress proxyAddr = createAddress(host, port);
            if (proxyAddr != null) {
                return new Proxy(Proxy.Type.HTTP, proxyAddr);
            }
        }
        return Proxy.NO_PROXY;

    }

    private static InetSocketAddress createAddress(String proxyHost, int proxyPort) {
        try {
            InetAddress address = InetAddress.getByName(proxyHost);
            return new InetSocketAddress(address, proxyPort);
        } catch (UnknownHostException e) {
            new ConsoleProgressIndicator().logWarning("Failed to parse host " + proxyHost);
            return null;
        }
    }

    private Downloader getDownloader() {
        return new LocalFileAwareDownloader(
                new LegacyDownloader(FileOpUtils.create(), getSettingsController()));
    }

    /**
     * returns the kotlin plugin version, or null if plugin is not applied to this project, or
     * "unknown" if plugin is applied but version can't be determined.
     */
    @Nullable
    private String getKotlinPluginVersion() {
        Plugin plugin = project.getPlugins().findPlugin("kotlin-android");
        if (plugin == null) {
            return null;
        }
        try {
            // No null checks below because we're catching all exceptions.
            @SuppressWarnings("JavaReflectionMemberAccess")
            Method method = plugin.getClass().getMethod("getKotlinPluginVersion");
            method.setAccessible(true);
            return method.invoke(plugin).toString();
        } catch (Throwable e) {
            // Defensively catch all exceptions because we don't want it to crash
            // if kotlin plugin code changes unexpectedly.
            return "unknown";
        }
    }

    /**
     * If overridden in a subclass to return "true," the package Configuration will be named
     * "publish" instead of "apk"
     */
    protected boolean isPackagePublished() {
        return false;
    }
}
