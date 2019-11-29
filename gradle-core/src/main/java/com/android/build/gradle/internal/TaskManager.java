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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_ANDROID_APIS;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_LINTCHECKS;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_LINTPUBLISH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APKS_FROM_BUNDLE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CONSUMER_PROGUARD_RULES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAVA_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_JAVA_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;
import static com.android.build.gradle.internal.scope.ArtifactPublishingUtil.publishArtifactToConfiguration;
import static com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING;
import static com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG;
import static com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PUBLISH_JAR;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JNI_LIBS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NOT_COMPILED_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.PROCESSED_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB;
import static com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_CLASSES;
import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.FeatureExtension;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunAnchorTaskCreationAction;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask;
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask;
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask;
import com.android.build.gradle.internal.res.namespaced.NamespacedResourcesTaskManager;
import com.android.build.gradle.internal.scope.AnchorOutputType;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.CheckProguardFiles;
import com.android.build.gradle.internal.tasks.DependencyReportTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.DexMergingAction;
import com.android.build.gradle.internal.tasks.DexMergingTask;
import com.android.build.gradle.internal.tasks.ExtractProguardFiles;
import com.android.build.gradle.internal.tasks.ExtractTryWithResourcesSupportJar;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.InstallVariantTask;
import com.android.build.gradle.internal.tasks.JacocoTask;
import com.android.build.gradle.internal.tasks.LintCompile;
import com.android.build.gradle.internal.tasks.MergeAaptProguardFilesCreationAction;
import com.android.build.gradle.internal.tasks.PackageForUnitTest;
import com.android.build.gradle.internal.tasks.PrepareLintJar;
import com.android.build.gradle.internal.tasks.PrepareLintJarForPublish;
import com.android.build.gradle.internal.tasks.ProcessJavaResTask;
import com.android.build.gradle.internal.tasks.RecalculateStackFramesTask;
import com.android.build.gradle.internal.tasks.SigningConfigWriterTask;
import com.android.build.gradle.internal.tasks.SigningReportTask;
import com.android.build.gradle.internal.tasks.SourceSetsTask;
import com.android.build.gradle.internal.tasks.TestServerTask;
import com.android.build.gradle.internal.tasks.UninstallTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeBaseClassLogTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeDependencyArtifactsTask;
import com.android.build.gradle.internal.tasks.factory.PreConfigAction;
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.build.gradle.internal.tasks.factory.TaskFactory;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitUtils;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.BundleTestDataImpl;
import com.android.build.gradle.internal.test.TestDataImpl;
import com.android.build.gradle.internal.transforms.CustomClassTransform;
import com.android.build.gradle.internal.transforms.D8MainDexListTransform;
import com.android.build.gradle.internal.transforms.DesugarTransform;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransformBuilder;
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.build.gradle.internal.transforms.DexSplitterTransform;
import com.android.build.gradle.internal.transforms.ExternalLibsMergerTransform;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.build.gradle.internal.transforms.MergeClassesTransform;
import com.android.build.gradle.internal.transforms.MergeJavaResourcesTransform;
import com.android.build.gradle.internal.transforms.ProGuardTransform;
import com.android.build.gradle.internal.transforms.ProguardConfigurable;
import com.android.build.gradle.internal.transforms.R8Transform;
import com.android.build.gradle.internal.transforms.ShrinkBundleResourcesTask;
import com.android.build.gradle.internal.transforms.ShrinkResourcesTransform;
import com.android.build.gradle.internal.transforms.StripDebugSymbolTransform;
import com.android.build.gradle.internal.variant.AndroidArtifactVariantData;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.AndroidJavaCompile;
import com.android.build.gradle.tasks.BuildArtifactReportTask;
import com.android.build.gradle.tasks.CleanBuildCache;
import com.android.build.gradle.tasks.CompatibleScreensManifest;
import com.android.build.gradle.tasks.CopyOutputs;
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.ExternalNativeCleanTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.GenerateSplitAbiRes;
import com.android.build.gradle.tasks.GenerateTestConfig;
import com.android.build.gradle.tasks.InstantRunResourcesApkBuilder;
import com.android.build.gradle.tasks.JavaPreCompileTask;
import com.android.build.gradle.tasks.LintFixTask;
import com.android.build.gradle.tasks.LintGlobalTask;
import com.android.build.gradle.tasks.LintPerVariantTask;
import com.android.build.gradle.tasks.MainApkListPersistence;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.PackageSplitAbi;
import com.android.build.gradle.tasks.PackageSplitRes;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessAnnotationsTask;
import com.android.build.gradle.tasks.ProcessApplicationManifest;
import com.android.build.gradle.tasks.ProcessLibraryManifest;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DesugarProcessArgs;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.builder.utils.FileCache;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Manages tasks creation. */
public abstract class TaskManager {

    public static final String DIR_BUNDLES = "bundles";
    public static final String INSTALL_GROUP = "Install";
    public static final String BUILD_GROUP = BasePlugin.BUILD_GROUP;
    public static final String ANDROID_GROUP = "Android";
    public static final String FEATURE_SUFFIX = "Feature";

    // Task names. These cannot be AndroidTasks as in the component model world there is nothing to
    // force generateTasksBeforeEvaluate to happen before the variant tasks are created.
    public static final String MAIN_PREBUILD = "preBuild";
    public static final String UNINSTALL_ALL = "uninstallAll";
    public static final String DEVICE_CHECK = "deviceCheck";
    public static final String DEVICE_ANDROID_TEST = DEVICE + VariantType.ANDROID_TEST_SUFFIX;
    public static final String CONNECTED_CHECK = "connectedCheck";
    public static final String CONNECTED_ANDROID_TEST = CONNECTED + VariantType.ANDROID_TEST_SUFFIX;
    public static final String ASSEMBLE_ANDROID_TEST = "assembleAndroidTest";
    public static final String LINT = "lint";
    public static final String LINT_FIX = "lintFix";
    public static final String EXTRACT_PROGUARD_FILES = "extractProguardFiles";

    @NonNull protected final Project project;
    @NonNull protected final ProjectOptions projectOptions;
    @NonNull protected final DataBindingBuilder dataBindingBuilder;
    @NonNull protected final SdkHandler sdkHandler;
    @NonNull protected final AndroidConfig extension;
    @NonNull private final VariantFactory variantFactory;
    @NonNull protected final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final Recorder recorder;
    @NonNull private final Logger logger;
    @Nullable private final FileCache buildCache;
    @NonNull protected final TaskFactory taskFactory;

    // Tasks. TODO: remove the mutable state from here.
    public Task createMockableJar;

    public TaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        this.globalScope = globalScope;
        this.project = project;
        this.projectOptions = projectOptions;
        this.dataBindingBuilder = dataBindingBuilder;
        this.sdkHandler = sdkHandler;
        this.extension = extension;
        this.variantFactory = variantFactory;
        this.toolingRegistry = toolingRegistry;
        this.recorder = recorder;
        this.logger = Logging.getLogger(this.getClass());

        // It's too early to materialize the project-level cache, we'll need to get it from
        // globalScope later on.
        this.buildCache = globalScope.getBuildCache();

        taskFactory = new TaskFactoryImpl(project.getTasks());
    }

    @NonNull
    public TaskFactory getTaskFactory() {
        return taskFactory;
    }

    @NonNull
    public DataBindingBuilder getDataBindingBuilder() {
        return dataBindingBuilder;
    }

    /** Creates the tasks for a given BaseVariantData. */
    public abstract void createTasksForVariantScope(@NonNull VariantScope variantScope);

    /**
     * Override to configure NDK data in the scope.
     */
    public void configureScopeForNdk(@NonNull VariantScope scope) {
        final BaseVariantData variantData = scope.getVariantData();
        File objFolder = new File(scope.getGlobalScope().getIntermediatesDir(),
                "ndk/" + variantData.getVariantConfiguration().getDirName() + "/obj");
        for (Abi abi : NdkHandler.getAbiList()) {
            scope.addNdkDebuggableLibraryFolders(abi,
                    new File(objFolder, "local/" + abi.getName()));
        }

    }

    /**
     * Create tasks before the evaluation (on plugin apply). This is useful for tasks that could be
     * referenced by custom build logic.
     */
    public void createTasksBeforeEvaluate() {
        taskFactory.register(
                UNINSTALL_ALL,
                uninstallAllTask -> {
                    uninstallAllTask.setDescription("Uninstall all applications.");
                    uninstallAllTask.setGroup(INSTALL_GROUP);
                });

        taskFactory.register(
                DEVICE_CHECK,
                deviceCheckTask -> {
                    deviceCheckTask.setDescription(
                            "Runs all device checks using Device Providers and Test Servers.");
                    deviceCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        taskFactory.register(
                CONNECTED_CHECK,
                connectedCheckTask -> {
                    connectedCheckTask.setDescription(
                            "Runs all device checks on currently connected devices.");
                    connectedCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        // Make sure MAIN_PREBUILD runs first:
        taskFactory.register(MAIN_PREBUILD);

        taskFactory.register(
                EXTRACT_PROGUARD_FILES,
                ExtractProguardFiles.class,
                task -> task.dependsOn(MAIN_PREBUILD));

        taskFactory.register(new SourceSetsTask.CreationAction(extension));

        taskFactory.register(
                ASSEMBLE_ANDROID_TEST,
                assembleAndroidTestTask -> {
                    assembleAndroidTestTask.setGroup(BasePlugin.BUILD_GROUP);
                    assembleAndroidTestTask.setDescription("Assembles all the Test applications.");
                });

        taskFactory.register(new LintCompile.CreationAction(globalScope));

        // Lint task is configured in afterEvaluate, but created upfront as it is used as an
        // anchor task.
        createGlobalLintTask();
        configureCustomLintChecksConfig();

        globalScope.setAndroidJarConfig(createAndroidJarConfig(project));

        if (buildCache != null) {
            taskFactory.register(new CleanBuildCache.CreationAction(globalScope));
        }

        // for testing only.
        taskFactory.register(
                "resolveConfigAttr", ConfigAttrTask.class, task -> task.resolvable = true);
        taskFactory.register(
                "consumeConfigAttr", ConfigAttrTask.class, task -> task.consumable = true);
    }

    private void configureCustomLintChecksConfig() {
        // create a single configuration to point to a project or a local file that contains
        // the lint.jar for this project.
        // This is not the configuration that consumes lint.jar artifacts from normal dependencies,
        // or publishes lint.jar to consumers. These are handled at the variant level.
        globalScope.setLintChecks(createCustomLintChecksConfig(project));
        globalScope.setLintPublish(createCustomLintPublishConfig(project));
    }

    @NonNull
    public static Configuration createCustomLintChecksConfig(@NonNull Project project) {
        Configuration lintChecks = project.getConfigurations().maybeCreate(CONFIG_NAME_LINTCHECKS);
        lintChecks.setVisible(false);
        lintChecks.setDescription("Configuration to apply external lint check jar");
        lintChecks.setCanBeConsumed(false);
        return lintChecks;
    }

    @NonNull
    public static Configuration createCustomLintPublishConfig(@NonNull Project project) {
        Configuration lintChecks = project.getConfigurations().maybeCreate(CONFIG_NAME_LINTPUBLISH);
        lintChecks.setVisible(false);
        lintChecks.setDescription("Configuration to publish external lint check jar");
        lintChecks.setCanBeConsumed(false);
        return lintChecks;
    }

    // this is call before all the variants are created since they are all going to depend
    // on the global LINT_JAR and LINT_PUBLISH_JAR task output
    public void configureCustomLintChecks() {
        // setup the task that reads the config and put the lint jar in the intermediate folder
        // so that the bundle tasks can copy it, and the inter-project publishing can publish it
        taskFactory.register(new PrepareLintJar.CreationAction(globalScope));
        taskFactory.register(new PrepareLintJarForPublish.CreationAction(globalScope));
    }

    public void createGlobalLintTask() {
        taskFactory.register(LINT, LintGlobalTask.class, task -> {});
        taskFactory.configure(JavaBasePlugin.CHECK_TASK_NAME, it -> it.dependsOn(LINT));
        taskFactory.register(LINT_FIX, LintFixTask.class, task -> {});
    }

    // this is run after all the variants are created.
    public void configureGlobalLintTask(@NonNull final Collection<VariantScope> variants) {
        // we only care about non testing and non feature variants
        List<VariantScope> filteredVariants =
                variants.stream().filter(TaskManager::isLintVariant).collect(Collectors.toList());

        if (filteredVariants.isEmpty()) {
            return;
        }

        // configure the global lint tasks.
        taskFactory.configure(
                LINT,
                LintGlobalTask.class,
                task ->
                        new LintGlobalTask.GlobalCreationAction(globalScope, filteredVariants)
                                .configure(task));
        taskFactory.configure(
                LINT_FIX,
                LintFixTask.class,
                task ->
                        new LintFixTask.GlobalCreationAction(globalScope, filteredVariants)
                                .configure(task));

        // publish the local lint.jar to all the variants. This is not for the task output itself
        // but for the artifact publishing.
        BuildableArtifact lintJar =
                globalScope.getArtifacts().getFinalArtifactFiles(LINT_PUBLISH_JAR);
        for (VariantScope scope : variants) {
            scope.getArtifacts()
                    .createBuildableArtifact(
                            InternalArtifactType.LINT_PUBLISH_JAR,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            lintJar);
        }
    }

    // This is for config attribute debugging
    public static class ConfigAttrTask extends DefaultTask {
        boolean consumable = false;
        boolean resolvable = false;
        @TaskAction
        public void run() {
            for (Configuration config : getProject().getConfigurations()) {
                AttributeContainer attributes = config.getAttributes();
                if ((consumable && config.isCanBeConsumed())
                        || (resolvable && config.isCanBeResolved())) {
                    System.out.println(config.getName());
                    System.out.println("\tcanBeResolved: " + config.isCanBeResolved());
                    System.out.println("\tcanBeConsumed: " + config.isCanBeConsumed());
                    for (Attribute<?> attr : attributes.keySet()) {
                        System.out.println(
                                "\t" + attr.getName() + ": " + attributes.getAttribute(attr));
                    }
                    if (consumable && config.isCanBeConsumed()) {
                        for (PublishArtifact artifact : config.getArtifacts()) {
                            System.out.println("\tArtifact: " + artifact.getName() + " (" + artifact.getFile().getName() + ")");
                        }
                        for (ConfigurationVariant cv : config.getOutgoing().getVariants()) {
                            System.out.println("\tConfigurationVariant: " + cv.getName());
                            for (PublishArtifact pa : cv.getArtifacts()) {
                                System.out.println("\t\tArtifact: " + pa.getFile());
                                System.out.println("\t\tType:" + pa.getType());
                            }
                        }
                    }
                }
            }
        }
    }

    public void createMockableJarTask() {
        project.getDependencies()
                .add(
                        CONFIG_NAME_ANDROID_APIS,
                        project.files(
                                globalScope
                                        .getAndroidBuilder()
                                        .getTarget()
                                        .getPath(IAndroidTarget.ANDROID_JAR)));

        // Adding this task to help the IDE find the mockable JAR.
        createMockableJar = project.getTasks().create("createMockableJar");
        createMockableJar.dependsOn(globalScope.getMockableJarArtifact());
    }

    @NonNull
    public static Configuration createAndroidJarConfig(@NonNull Project project) {
        Configuration androidJarConfig =
                project.getConfigurations().maybeCreate(CONFIG_NAME_ANDROID_APIS);
        androidJarConfig.setDescription(
                "Configuration providing various types of Android JAR file");
        androidJarConfig.setCanBeConsumed(false);
        return androidJarConfig;
    }

    protected void createDependencyStreams(@NonNull final VariantScope variantScope) {
        // Since it's going to chance the configurations, we need to do it before
        // we start doing queries to fill the streams.
        handleJacocoDependencies(variantScope);

        TransformManager transformManager = variantScope.getTransformManager();

        // This might be consumed by RecalculateFixedStackFrames if that's created
        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, EXTERNAL, CLASSES))
                        .build());

        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-res-plus-native")
                        .addContentTypes(
                                DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, EXTERNAL, JAVA_RES))
                        .build());

        // and the android AAR also have a specific jni folder
        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-native")
                        .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, EXTERNAL, JNI))
                        .build());

        // for the sub modules, new intermediary classes artifact has its own stream
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, MODULE, CLASSES))
                        .build());

        // same for the resources which can be java-res or jni
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-res-plus-native")
                        .addContentTypes(
                                DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, MODULE, JAVA_RES))
                        .build());

        // and the android library sub-modules also have a specific jni folder
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-native")
                        .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(RUNTIME_CLASSPATH, MODULE, JNI))
                        .build());

        // if variantScope.consumesFeatureJars(), add streams of classes and java resources from
        // features or dynamic-features.
        // The main dex list calculation for the bundle also needs the feature classes for reference
        // only
        if (variantScope.consumesFeatureJars() || variantScope.getNeedsMainDexListForBundle()) {
            transformManager.addStream(
                    OriginalStream.builder(project, "metadata-classes")
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(InternalScope.FEATURES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            METADATA_VALUES, MODULE, METADATA_CLASSES))
                            .build());
        }
        if (variantScope.consumesFeatureJars()) {
            transformManager.addStream(
                    OriginalStream.builder(project, "metadata-java-res")
                            .addContentTypes(TransformManager.CONTENT_RESOURCES)
                            .addScope(InternalScope.FEATURES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            METADATA_VALUES, MODULE, METADATA_JAVA_RES))
                            .build());
        }

        // provided only scopes.
        transformManager.addStream(
                OriginalStream.builder(project, "provided-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.PROVIDED_ONLY)
                        .setFileCollection(variantScope.getProvidedOnlyClasspath())
                        .build());

        if (variantScope.getTestedVariantData() != null) {
            final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

            VariantScope testedVariantScope = testedVariantData.getScope();

            PublishingSpecs.VariantSpec testedSpec =
                    testedVariantScope
                            .getPublishingSpec()
                            .getTestingSpec(variantScope.getVariantConfiguration().getType());

            // get the OutputPublishingSpec from the ArtifactType for this particular variant spec
            PublishingSpecs.OutputSpec taskOutputSpec =
                    testedSpec.getSpec(
                            AndroidArtifacts.ArtifactType.CLASSES,
                            AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS);
            // now get the output type
            com.android.build.api.artifact.ArtifactType testedOutputType =
                    taskOutputSpec.getOutputType();

            FileCollection testedCodeClasses =
                    testedVariantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(testedOutputType)
                            .get();

            variantScope.getArtifacts().createBuildableArtifact(
                    InternalArtifactType.TESTED_CODE_CLASSES,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    testedCodeClasses);

            // create two streams of different types.
            transformManager.addStream(
                    OriginalStream.builder(project, "tested-code-classes")
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setFileCollection(testedCodeClasses)
                            .build());

            transformManager.addStream(
                    OriginalStream.builder(project, "tested-code-deps")
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setArtifactCollection(
                                    testedVariantScope.getArtifactCollection(
                                            RUNTIME_CLASSPATH, ALL, CLASSES))
                            .build());
        }
    }

    public void createBuildArtifactReportTask(@NonNull VariantScope scope) {
        taskFactory.register(new BuildArtifactReportTask.BuildArtifactReportCreationAction(scope));
    }

    public void createSourceSetArtifactReportTask(@NonNull GlobalScope scope) {
        for (AndroidSourceSet sourceSet : scope.getExtension().getSourceSets()) {
            if (sourceSet instanceof DefaultAndroidSourceSet) {
                taskFactory.register(
                        new BuildArtifactReportTask.SourceSetReportCreationAction(
                                scope, (DefaultAndroidSourceSet) sourceSet));
            }
        }
    }

    public void createMergeApkManifestsTask(@NonNull VariantScope variantScope) {
        AndroidArtifactVariantData androidArtifactVariantData =
                (AndroidArtifactVariantData) variantScope.getVariantData();
        Set<String> screenSizes = androidArtifactVariantData.getCompatibleScreens();

        taskFactory.register(
                new CompatibleScreensManifest.CreationAction(variantScope, screenSizes));

        TaskProvider<? extends ManifestProcessorTask> processManifestTask =
                createMergeManifestTask(variantScope);

        final MutableTaskContainer taskContainer = variantScope.getTaskContainer();
        if (taskContainer.getMicroApkTask() != null) {
            TaskFactoryUtils.dependsOn(processManifestTask, taskContainer.getMicroApkTask());
        }
    }

    @NonNull
    private static List<String> getAdvancedProfilingTransforms(@NonNull ProjectOptions options) {
        String string = options.get(StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS);
        if (string == null) {
            return ImmutableList.of();
        }
        return Splitter.on(',').splitToList(string);
    }

    /** Creates the merge manifests task. */
    @NonNull
    protected TaskProvider<? extends ManifestProcessorTask> createMergeManifestTask(
            @NonNull VariantScope variantScope) {
        return taskFactory.register(
                new ProcessApplicationManifest.CreationAction(
                        variantScope, !getAdvancedProfilingTransforms(projectOptions).isEmpty()));
    }

    public TaskProvider<? extends ManifestProcessorTask> createMergeLibManifestsTask(
            @NonNull VariantScope scope) {

        return taskFactory.register(new ProcessLibraryManifest.CreationAction(scope));
    }

    protected void createProcessTestManifestTask(
            @NonNull VariantScope scope,
            @NonNull VariantScope testedScope) {

        taskFactory.register(
                new ProcessTestManifest.CreationAction(
                        scope, testedScope.getArtifacts().getFinalProduct(MERGED_MANIFESTS)));
    }

    public void createRenderscriptTask(@NonNull VariantScope scope) {
        final MutableTaskContainer taskContainer = scope.getTaskContainer();

        TaskProvider<RenderscriptCompile> rsTask =
                taskFactory.register(new RenderscriptCompile.CreationAction(scope));

        GradleVariantConfiguration config = scope.getVariantConfiguration();

        TaskFactoryUtils.dependsOn(taskContainer.getResourceGenTask(), rsTask);
        // only put this dependency if rs will generate Java code
        if (!config.getRenderscriptNdkModeEnabled()) {
            TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), rsTask);
        }
    }

    public TaskProvider<MergeResources> createMergeResourcesTask(
            @NonNull VariantScope scope,
            boolean processResources,
            ImmutableSet<MergeResources.Flag> flags) {

        boolean unitTestRawResources =
                globalScope
                                .getExtension()
                                .getTestOptions()
                                .getUnitTests()
                                .isIncludeAndroidResources()
                        && !projectOptions.get(BooleanOption.ENABLE_UNIT_TEST_BINARY_RESOURCES);

        boolean alsoOutputNotCompiledResources =
                scope.getType().isApk()
                        && !scope.getType().isForTesting()
                        && (scope.useResourceShrinker() || unitTestRawResources);

        return basicCreateMergeResourcesTask(
                scope,
                MergeType.MERGE,
                null /*outputLocation*/,
                true /*includeDependencies*/,
                processResources,
                alsoOutputNotCompiledResources,
                flags,
                null /*preConfigCallback*/,
                null /*configCallback*/);
    }

    /** Defines the merge type for {@link #basicCreateMergeResourcesTask} */
    public enum MergeType {
        /**
         * Merge all resources with all the dependencies resources.
         */
        MERGE {
            @Override
            public InternalArtifactType getOutputType() {
                return InternalArtifactType.MERGED_RES;
            }
        },
        /**
         * Merge all resources without the dependencies resources for an aar.
         */
        PACKAGE {
            @Override
            public InternalArtifactType getOutputType() {
                return InternalArtifactType.PACKAGED_RES;
            }
        };

        public abstract InternalArtifactType getOutputType();
    }

    public TaskProvider<MergeResources> basicCreateMergeResourcesTask(
            @NonNull VariantScope scope,
            @NonNull MergeType mergeType,
            @Nullable File outputLocation,
            final boolean includeDependencies,
            final boolean processResources,
            boolean alsoOutputNotCompiledResources,
            @NonNull ImmutableSet<MergeResources.Flag> flags,
            @Nullable PreConfigAction preConfigCallback,
            @Nullable TaskConfigAction<MergeResources> configCallback) {

        File mergedOutputDir = MoreObjects
                .firstNonNull(outputLocation, scope.getDefaultMergeResourcesOutputDir());

        String taskNamePrefix = mergeType.name().toLowerCase(Locale.ENGLISH);

        File mergedNotCompiledDir =
                alsoOutputNotCompiledResources
                        ? new File(
                                globalScope.getIntermediatesDir()
                                        + "/merged-not-compiled-resources/"
                                        + scope.getVariantConfiguration().getDirName())
                        : null;

        TaskProvider<MergeResources> mergeResourcesTask =
                taskFactory.register(
                        new MergeResources.CreationAction(
                                scope,
                                mergeType,
                                taskNamePrefix,
                                mergedOutputDir,
                                mergedNotCompiledDir,
                                includeDependencies,
                                processResources,
                                flags),
                        preConfigCallback,
                        configCallback,
                        null);

        scope.getArtifacts()
                .appendArtifact(
                        mergeType.getOutputType(),
                        ImmutableList.of(mergedOutputDir),
                        mergeResourcesTask.getName());

        if (alsoOutputNotCompiledResources) {
            scope.getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.MERGED_NOT_COMPILED_RES,
                            ImmutableList.of(mergedNotCompiledDir),
                            mergeResourcesTask.getName());
        }

        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            TaskFactoryUtils.dependsOn(
                    scope.getTaskContainer().getCompileTask(), mergeResourcesTask);
        }

        return mergeResourcesTask;
    }

    public void createMergeAssetsTask(@NonNull VariantScope scope) {
        taskFactory.register(new MergeSourceSetFolders.MergeAppAssetCreationAction(scope));
    }

    @NonNull
    public Optional<TaskProvider<TransformTask>> createMergeJniLibFoldersTasks(
            @NonNull final VariantScope variantScope) {
        // merge the source folders together using the proper priority.
        TaskProvider<MergeSourceSetFolders> mergeJniLibFoldersTask =
                taskFactory.register(
                        new MergeSourceSetFolders.MergeJniLibFoldersCreationAction(variantScope));
        final MutableTaskContainer taskContainer = variantScope.getTaskContainer();

        // create the stream generated from this task
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "mergedJniFolder")
                                .addContentType(ExtendedContentType.NATIVE_LIBS)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        variantScope
                                                .getArtifacts()
                                                .getFinalArtifactFiles(MERGED_JNI_LIBS)
                                                .get())
                                .build());


        // create a stream that contains the content of the local external native build
        if (taskContainer.getExternalNativeJsonGenerator() != null) {
            variantScope
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "external-native-build")
                                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(
                                            project.files(
                                                            taskContainer
                                                                    .getExternalNativeJsonGenerator()
                                                                    .getObjFolder())
                                                    .builtBy(
                                                            checkNotNull(
                                                                            taskContainer
                                                                                    .getExternalNativeBuildTask())
                                                                    .getName()))
                                    .build());
        }

        // create a stream containing the content of the renderscript compilation output
        // if support mode is enabled.
        if (variantScope.getVariantConfiguration().getRenderscriptSupportModeEnabled()) {
            ConfigurableFileCollection rsFileCollection =
                    project.files(
                            variantScope
                                    .getArtifacts()
                                    .getFinalArtifactFiles(RENDERSCRIPT_LIB)
                                    .get());

            File rsLibs =
                    variantScope.getGlobalScope().getAndroidBuilder().getSupportNativeLibFolder();
            if (rsLibs != null && rsLibs.isDirectory()) {
                rsFileCollection.from(rsLibs);
            }
            if (variantScope.getVariantConfiguration().getRenderscriptSupportModeBlasEnabled()) {
                File rsBlasLib =
                        variantScope.getGlobalScope().getAndroidBuilder().getSupportBlasLibFolder();

                if (rsBlasLib == null || !rsBlasLib.isDirectory()) {
                    throw new GradleException(
                            "Renderscript BLAS support mode is not supported "
                                    + "in BuildTools"
                                    + rsBlasLib);
                } else {
                    rsFileCollection.from(rsBlasLib);
                }
            }

            variantScope
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "rs-support-mode-output")
                                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(rsFileCollection)
                                    .build());
        }

        // compute the scopes that need to be merged.
        Set<? super Scope> mergeScopes = getResMergingScopes(variantScope);
        // Create the merge transform
        MergeJavaResourcesTransform mergeTransform = new MergeJavaResourcesTransform(
                variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                mergeScopes, ExtendedContentType.NATIVE_LIBS, "mergeJniLibs", variantScope);

        return variantScope
                .getTransformManager()
                .addTransform(taskFactory, variantScope, mergeTransform);
    }

    public void createBuildConfigTask(@NonNull VariantScope scope) {
        TaskProvider<GenerateBuildConfig> generateBuildConfigTask =
                taskFactory.register(new GenerateBuildConfig.CreationAction(scope));

        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getSourceGenTask(), generateBuildConfigTask);
    }

    public void createGenerateResValuesTask(
            @NonNull VariantScope scope) {
        TaskProvider<GenerateResValues> generateResValuesTask =
                taskFactory.register(new GenerateResValues.CreationAction(scope));
        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getResourceGenTask(), generateResValuesTask);
    }

    public void createApkProcessResTask(
            @NonNull VariantScope scope) {
        VariantType variantType = scope.getVariantData().getVariantConfiguration().getType();
        InternalArtifactType packageOutputType =
                (variantType.isApk() && !variantType.isForTesting()) ? FEATURE_RESOURCE_PKG : null;

        createApkProcessResTask(scope, packageOutputType);

        if (scope.consumesFeatureJars()) {
            taskFactory.register(new MergeAaptProguardFilesCreationAction(scope));
        }
    }

    private void createApkProcessResTask(@NonNull VariantScope scope,
            InternalArtifactType packageOutputType) {
        createProcessResTask(
                scope,
                new File(
                        globalScope.getIntermediatesDir(),
                        "symbols/" + scope.getVariantData().getVariantConfiguration().getDirName()),
                packageOutputType,
                MergeType.MERGE,
                scope.getGlobalScope().getProjectBaseName());
    }

    protected boolean isLibrary() {
        return false;
    }

    public void createProcessResTask(
            @NonNull VariantScope scope,
            @NonNull File symbolLocation,
            @Nullable InternalArtifactType packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        BaseVariantData variantData = scope.getVariantData();

        variantData.calculateFilters(scope.getGlobalScope().getExtension().getSplits());

        // The manifest main dex list proguard rules are always needed for the bundle,
        // even if legacy multidex is not explicitly enabled.
        boolean useAaptToGenerateLegacyMultidexMainDexProguardRules = scope.getNeedsMainDexList();

        if (scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            new NamespacedResourcesTaskManager(globalScope, taskFactory, scope)
                    .createNamespacedResourceTasks(
                            packageOutputType,
                            baseName,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules);

            FileCollection rFiles =
                    scope.getArtifacts().getFinalArtifactFiles(RUNTIME_R_CLASS_CLASSES).get();

            scope.getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "final-r-classes")
                                    .addContentTypes(
                                            DefaultContentType.CLASSES,
                                            DefaultContentType.RESOURCES)
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(rFiles)
                                    .build());

            scope.getArtifacts().appendArtifact(AnchorOutputType.ALL_CLASSES, rFiles);
            return;
        }
        createNonNamespacedResourceTasks(
                scope,
                symbolLocation,
                packageOutputType,
                mergeType,
                baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules);
    }

    private void createNonNamespacedResourceTasks(
            @NonNull VariantScope scope,
            @NonNull File symbolDirectory,
            InternalArtifactType packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName,
            boolean useAaptToGenerateLegacyMultidexMainDexProguardRules) {
        File symbolTableWithPackageName =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        FD_RES,
                        "symbol-table-with-package",
                        scope.getVariantConfiguration().getDirName(),
                        "package-aware-r.txt");
        final TaskProvider<? extends ProcessAndroidResources> task;

        File symbolFile = new File(symbolDirectory, FN_RESOURCE_TEXT);
        BuildArtifactsHolder artifacts = scope.getArtifacts();
        if (mergeType == MergeType.PACKAGE) {
            // Simply generate the R class for a library
            task =
                    taskFactory.register(
                            new GenerateLibraryRFileTask.CreationAction(
                                    scope, symbolFile, symbolTableWithPackageName));
        } else {
            task =
                    taskFactory.register(
                            createProcessAndroidResourcesConfigAction(
                                    scope,
                                    () -> symbolDirectory,
                                    symbolTableWithPackageName,
                                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                                    mergeType,
                                    baseName));

            if (packageOutputType != null) {
                artifacts.createBuildableArtifact(
                        packageOutputType,
                        BuildArtifactsHolder.OperationType.INITIAL,
                        artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES));
            }

            // create the task that creates the aapt output for the bundle.
            taskFactory.register(new LinkAndroidResForBundleTask.CreationAction(scope));
        }
        artifacts.appendArtifact(
                InternalArtifactType.SYMBOL_LIST, ImmutableList.of(symbolFile), task.getName());

        // Needed for the IDE
        if (!projectOptions.get(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION)) {
            TaskFactoryUtils.dependsOn(scope.getTaskContainer().getSourceGenTask(), task);
        }

        // Synthetic output for AARs (see SymbolTableWithPackageNameTransform), and created in
        // process resources for local subprojects.
        artifacts.appendArtifact(
                InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                ImmutableList.of(symbolTableWithPackageName),
                task.getName());
    }

    protected VariantTaskCreationAction<LinkApplicationAndroidResourcesTask>
            createProcessAndroidResourcesConfigAction(
                    @NonNull VariantScope scope,
                    @NonNull Supplier<File> symbolLocation,
                    @NonNull File symbolWithPackageName,
                    boolean useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    @NonNull MergeType sourceArtifactType,
                    @NonNull String baseName) {

        return new LinkApplicationAndroidResourcesTask.CreationAction(
                scope,
                symbolLocation,
                symbolWithPackageName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules,
                sourceArtifactType,
                baseName,
                isLibrary());
    }

    /**
     * Creates the split resources packages task if necessary. AAPT will produce split packages for
     * all --split provided parameters. These split packages should be signed and moved unchanged to
     * the FULL_APK build output directory.
     */
    public void createSplitResourcesTasks(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();

        checkState(
                variantData.getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS),
                "Can only create split resources tasks for pure splits.");

        TaskProvider<PackageSplitRes> task =
                taskFactory.register(new PackageSplitRes.CreationAction(scope));
    }

    @Nullable
    public TaskProvider<PackageSplitAbi> createSplitAbiTasks(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();

        checkState(
                variantData.getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS),
                "split ABI tasks are only compatible with pure splits.");

        Set<String> filters = AbiSplitOptions.getAbiFilters(extension.getSplits().getAbiFilters());
        if (filters.isEmpty()) {
            return null;
        }

        List<ApkData> fullApkDatas =
                variantData.getOutputScope().getSplitsByType(OutputFile.OutputType.FULL_SPLIT);
        if (!fullApkDatas.isEmpty()) {
            throw new RuntimeException(
                    "In release 21 and later, there cannot be full splits and pure splits, "
                            + "found "
                            + Joiner.on(",").join(fullApkDatas)
                            + " and abi filters "
                            + Joiner.on(",").join(filters));
        }

        // first create the ABI specific split FULL_APK resources.
        taskFactory.register(new GenerateSplitAbiRes.CreationAction(scope));

        // then package those resources with the appropriate JNI libraries.
        TaskProvider<PackageSplitAbi> packageSplitAbiTask =
                taskFactory.register(new PackageSplitAbi.CreationAction(scope));

        return packageSplitAbiTask;
    }

    public void createSplitTasks(@NonNull VariantScope variantScope) {
        createSplitResourcesTasks(variantScope);
        createSplitAbiTasks(variantScope);
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     *
     * @param variantScope the scope of the variant being processed.
     * @return the list of scopes for which to merge the java resources.
     */
    @NonNull
    protected abstract Set<? super Scope> getResMergingScopes(@NonNull VariantScope variantScope);

    /**
     * Creates the java resources processing tasks.
     *
     * <p>The java processing will happen in two steps:
     *
     * <ul>
     *   <li>{@link Sync} task configured with {@link ProcessJavaResTask.CreationAction} will sync
     *       all source folders into a single folder identified by {@link
     *       VariantScope#getSourceFoldersJavaResDestinationDir()}
     *   <li>{@link MergeJavaResourcesTransform} will take the output of this merge plus the
     *       dependencies and will create a single merge with the {@link PackagingOptions} settings
     *       applied.
     * </ul>
     *
     * This sets up only the Sync part. The transform is setup via {@link
     * #createMergeJavaResTransform(VariantScope)}
     *
     * @param variantScope the variant scope we are operating under.
     */
    public void createProcessJavaResTask(@NonNull VariantScope variantScope) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.

        // TODO: move this file computation completely out of VariantScope.
        File destinationDir = variantScope.getSourceFoldersJavaResDestinationDir();

        TaskProvider<ProcessJavaResTask> processJavaResourcesTask =
                taskFactory.register(
                        new ProcessJavaResTask.CreationAction(variantScope, destinationDir));

        // create the task outputs for others to consume
        BuildableArtifact javaRes =
                variantScope
                        .getArtifacts()
                        .appendArtifact(
                                InternalArtifactType.JAVA_RES,
                                ImmutableList.of(destinationDir),
                                processJavaResourcesTask.getName());

        // create the stream generated from this task
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "processed-java-res")
                                .addContentType(DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(javaRes.get())
                                .build());
    }

    /**
     * Sets up the Merge Java Res transform.
     *
     * @param variantScope the variant scope we are operating under.
     * @see #createProcessJavaResTask(VariantScope)
     */
    public void createMergeJavaResTransform(@NonNull VariantScope variantScope) {
        TransformManager transformManager = variantScope.getTransformManager();

        // Compute the scopes that need to be merged.
        Set<? super Scope> mergeScopes = getResMergingScopes(variantScope);

        // Create the merge transform.
        MergeJavaResourcesTransform mergeTransform =
                new MergeJavaResourcesTransform(
                        variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                        mergeScopes,
                        DefaultContentType.RESOURCES,
                        "mergeJavaRes",
                        variantScope);
        transformManager.addTransform(
                taskFactory,
                variantScope,
                mergeTransform,
                taskName -> {
                    File mergeJavaResOutput =
                            FileUtils.join(
                                    globalScope.getIntermediatesDir(),
                                    "transforms",
                                    "mergeJavaRes",
                                    variantScope.getVariantConfiguration().getDirName(),
                                    "0.jar");
                    variantScope
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.FEATURE_AND_RUNTIME_DEPS_JAVA_RES,
                                    ImmutableList.of(mergeJavaResOutput),
                                    taskName);
                },
                null,
                null);
    }

    public TaskProvider<AidlCompile> createAidlTask(@NonNull VariantScope scope) {
        MutableTaskContainer taskContainer = scope.getTaskContainer();

        TaskProvider<AidlCompile> aidlCompileTask =
                taskFactory.register(new AidlCompile.CreationAction(scope));

        TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), aidlCompileTask);

        return aidlCompileTask;
    }

    public void createShaderTask(@NonNull VariantScope scope) {
        // merge the shader folders together using the proper priority.
        TaskProvider<MergeSourceSetFolders> mergeShadersTask =
                taskFactory.register(
                        new MergeSourceSetFolders.MergeShaderSourceFoldersCreationAction(scope));

        // compile the shaders
        TaskProvider<ShaderCompile> shaderCompileTask =
                taskFactory.register(new ShaderCompile.CreationAction(scope));

        TaskFactoryUtils.dependsOn(scope.getTaskContainer().getAssetGenTask(), shaderCompileTask);
    }

    protected abstract void postJavacCreation(@NonNull final VariantScope scope);

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    public TaskProvider<? extends JavaCompile> createJavacTask(@NonNull final VariantScope scope) {
        taskFactory.register(new JavaPreCompileTask.CreationAction(scope));

        boolean processAnnotationsTaskCreated = ProcessAnnotationsTask.taskShouldBeCreated(scope);
        if (processAnnotationsTaskCreated) {
            taskFactory.register(new ProcessAnnotationsTask.CreationAction(scope));
        }

        final TaskProvider<? extends JavaCompile> javacTask =
                taskFactory.register(
                        new AndroidJavaCompile.CreationAction(
                                scope, processAnnotationsTaskCreated));

        postJavacCreation(scope);

        return javacTask;
    }

    /**
     * Add stream of classes compiled by javac to transform manager.
     *
     * <p>This should not be called for classes that will also be compiled from source by jack.
     */
    protected void addJavacClassesStream(VariantScope scope) {
        BuildArtifactsHolder artifacts = scope.getArtifacts();
        FileCollection javaOutputs = artifacts.getFinalArtifactFiles(JAVAC).get();
        Preconditions.checkNotNull(javaOutputs);
        // create separate streams for the output of JAVAC and for the pre/post javac
        // bytecode hooks
        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "javac-output")
                                // Need both classes and resources because some annotation
                                // processors generate resources
                                .addContentTypes(
                                        DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(javaOutputs)
                                .build());

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "pre-javac-generated-bytecode")
                                .addContentTypes(
                                        DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().getAllPreJavacGeneratedBytecode())
                                .build());

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "post-javac-generated-bytecode")
                                .addContentTypes(
                                        DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().getAllPostJavacGeneratedBytecode())
                                .build());

        if (scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()
                && projectOptions.get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
            // This might be consumed by RecalculateFixedStackFrames if that's created
            scope.getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "auto-namespaced-dependencies-classes")
                                    .addContentTypes(DefaultContentType.CLASSES)
                                    .addScope(Scope.EXTERNAL_LIBRARIES)
                                    .setFileCollection(
                                            artifacts
                                                    .getFinalArtifactFiles(
                                                            InternalArtifactType
                                                                    .NAMESPACED_CLASSES_JAR)
                                                    .get())
                                    .build());
        }
    }

    protected void createCompileTask(@NonNull VariantScope variantScope) {
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);
    }


    /** Makes the given task the one used by top-level "compile" task. */
    public static void setJavaCompilerTask(
            @NonNull TaskProvider<? extends JavaCompile> javaCompilerTask,
            @NonNull VariantScope scope) {
        TaskFactoryUtils.dependsOn(scope.getTaskContainer().getCompileTask(), javaCompilerTask);
    }

    /**
     * Creates the task that will handle micro apk.
     *
     * New in 2.2, it now supports the unbundled mode, in which the apk is not bundled
     * anymore, but we still have an XML resource packaged, and a custom entry in the manifest.
     * This is triggered by passing a null {@link Configuration} object.
     *
     * @param scope the variant scope
     * @param config an optional Configuration object. if non null, this will embed the micro apk,
     *               if null this will trigger the unbundled mode.
     */
    public void createGenerateMicroApkDataTask(
            @NonNull VariantScope scope,
            @Nullable FileCollection config) {
        TaskProvider<GenerateApkDataTask> generateMicroApkTask =
                taskFactory.register(new GenerateApkDataTask.CreationAction(scope, config));

        // the merge res task will need to run after this one.
        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getResourceGenTask(), generateMicroApkTask);
    }

    public void createExternalNativeBuildJsonGenerators(@NonNull VariantScope scope) {

        CoreExternalNativeBuild externalNativeBuild = extension.getExternalNativeBuild();
        ExternalNativeBuildTaskUtils.ExternalNativeBuildProjectPathResolution pathResolution =
                ExternalNativeBuildTaskUtils.getProjectPath(externalNativeBuild);
        if (pathResolution.errorText != null) {
            globalScope
                    .getAndroidBuilder()
                    .getIssueReporter()
                    .reportError(
                            Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                            new EvalIssueException(
                                    pathResolution.errorText,
                                    scope.getVariantConfiguration().getFullName()));
            return;
        }

        if (pathResolution.makeFile == null) {
            // No project
            return;
        }

        scope.getTaskContainer()
                .setExternalNativeJsonGenerator(
                        ExternalNativeJsonGenerator.create(
                                project.getRootDir(),
                                project.getPath(),
                                project.getProjectDir(),
                                project.getBuildDir(),
                                pathResolution.externalNativeBuildDir,
                                checkNotNull(pathResolution.buildSystem),
                                pathResolution.makeFile,
                                globalScope.getAndroidBuilder(),
                                sdkHandler,
                                scope));
    }

    public void createExternalNativeBuildTasks(@NonNull VariantScope scope) {
        final MutableTaskContainer taskContainer = scope.getTaskContainer();
        ExternalNativeJsonGenerator generator = taskContainer.getExternalNativeJsonGenerator();
        if (generator == null) {
            return;
        }

        // Set up JSON generation tasks
        TaskProvider<? extends Task> generateTask =
                taskFactory.register(
                        ExternalNativeBuildJsonTask.createTaskConfigAction(generator, scope));

        ProjectOptions projectOptions = globalScope.getProjectOptions();

        String targetAbi =
                projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI)
                        ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                        : null;

        // Set up build tasks
        TaskProvider<ExternalNativeBuildTask> buildTask =
                taskFactory.register(
                        new ExternalNativeBuildTask.CreationAction(
                                targetAbi,
                                generator,
                                generateTask,
                                scope,
                                globalScope.getAndroidBuilder()));

        TaskFactoryUtils.dependsOn(taskContainer.getCompileTask(), buildTask);

        // Set up clean tasks
        TaskProvider<Task> cleanTask = taskFactory.named("clean");
        TaskFactoryUtils.dependsOn(
                cleanTask,
                taskFactory.register(new ExternalNativeCleanTask.CreationAction(generator, scope)));
    }

    /** Create transform for stripping debug symbols from native libraries before deploying. */
    public static void createStripNativeLibraryTask(
            @NonNull TaskFactory taskFactory, @NonNull VariantScope scope) {
        if (!scope.getGlobalScope().getNdkHandler().isConfigured()) {
            // We don't know where the NDK is, so we won't be stripping the debug symbols from
            // native libraries.
            return;
        }
        TransformManager transformManager = scope.getTransformManager();
        GlobalScope globalScope = scope.getGlobalScope();
        transformManager.addTransform(
                taskFactory,
                scope,
                new StripDebugSymbolTransform(
                        globalScope.getProject(),
                        globalScope.getNdkHandler(),
                        globalScope.getExtension().getPackagingOptions().getDoNotStrip(),
                        scope.getVariantConfiguration().getType().isAar(),
                        scope.consumesFeatureJars()));
    }

    /** Creates the tasks to build unit tests. */
    public void createUnitTestVariantTasks(@NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        BaseVariantData testedVariantData =
                checkNotNull(variantScope.getTestedVariantData(), "Not a unit test variant");
        VariantScope testedVariantScope = testedVariantData.getScope();

        boolean includeAndroidResources = extension.getTestOptions().getUnitTests()
                .isIncludeAndroidResources();
        boolean enableBinaryResources = includeAndroidResources
                && globalScope.getProjectOptions().get(
                        BooleanOption.ENABLE_UNIT_TEST_BINARY_RESOURCES);

        createAnchorTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // process java resources
        createProcessJavaResTask(variantScope);

        if (includeAndroidResources) {
            if (testedVariantScope.getType().isAar()) {
                // Add a task to process the manifest
                createProcessTestManifestTask(variantScope, testedVariantData.getScope());

                // Add a task to create the res values
                createGenerateResValuesTask(variantScope);

                // Add a task to merge the assets folders
                createMergeAssetsTask(variantScope);

                if (enableBinaryResources) {
                    createMergeResourcesTask(variantScope, true, ImmutableSet.of());
                    // Add a task to process the Android Resources and generate source files
                    createApkProcessResTask(variantScope, FEATURE_RESOURCE_PKG);
                    taskFactory.register(new PackageForUnitTest.CreationAction(variantScope));
                } else {
                    createMergeResourcesTask(variantScope, false, ImmutableSet.of());
                }
            } else if (testedVariantScope.getType().isApk()) {
                if (enableBinaryResources) {
                    // The IDs will have been inlined for an non-namespaced application
                    // so just re-export the artifacts here.
                    variantScope
                            .getArtifacts()
                            .createBuildableArtifact(
                                    InternalArtifactType.PROCESSED_RES,
                                    BuildArtifactsHolder.OperationType.INITIAL,
                                    testedVariantScope
                                            .getArtifacts()
                                            .getFinalArtifactFiles(PROCESSED_RES));
                    variantScope
                            .getArtifacts()
                            .createBuildableArtifact(
                                    MERGED_ASSETS,
                                    BuildArtifactsHolder.OperationType.INITIAL,
                                    testedVariantScope
                                            .getArtifacts()
                                            .getFinalArtifactFiles(MERGED_ASSETS));
                    taskFactory.register(new PackageForUnitTest.CreationAction(variantScope));
                } else {
                    // TODO: don't implicitly subtract tested component in APKs, as that only
                    // makes sense for instrumentation tests. For now, rely on the production
                    // merged resources.
                    variantScope
                            .getArtifacts()
                            .createBuildableArtifact(
                                    InternalArtifactType.MERGED_RES,
                                    BuildArtifactsHolder.OperationType.INITIAL,
                                    testedVariantScope
                                            .getArtifacts()
                                            .getFinalArtifactFiles(MERGED_NOT_COMPILED_RES));
                }
            } else {
                throw new IllegalStateException(
                        "Tested variant "
                                + testedVariantScope.getFullVariantName()
                                + " in "
                                + globalScope.getProject().getPath()
                                + " must be a library or an application to have unit tests.");
            }

            TaskProvider<GenerateTestConfig> generateTestConfig =
                    taskFactory.register(new GenerateTestConfig.CreationAction(variantScope));
            TaskFactoryUtils.dependsOn(
                    variantScope.getTaskContainer().getCompileTask(), generateTestConfig);
        }

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add
        // dependencies on tasks that prepare necessary data files.
        TaskProvider<? extends Task> compileTask = variantScope.getTaskContainer().getCompileTask();
        TaskFactoryUtils.dependsOn(
                compileTask,
                variantScope.getTaskContainer().getProcessJavaResourcesTask(),
                testedVariantScope.getTaskContainer().getProcessJavaResourcesTask());

        // Empty R class jar. TODO: Resources support for unit tests?
        variantScope
                .getArtifacts()
                .appendArtifact(
                        InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR, project.files());

        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        // This should be done automatically by the classpath
        //        TaskFactoryUtils.dependsOn(javacTask, testedVariantScope.getTaskContainer().getJavacTask());

        createMergeJavaResTransform(variantScope);

        createRunUnitTestTask(variantScope);

        // This hides the assemble unit test task from the task list.

        variantScope.getTaskContainer().getAssembleTask().configure(task -> task.setGroup(null));
    }

    /** Creates the tasks to build android tests. */
    public void createAndroidTestVariantTasks(@NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();

        createAnchorTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // persist variant's output
        taskFactory.register(new MainApkListPersistence.CreationAction(variantScope));

        // Add a task to process the manifest
        createProcessTestManifestTask(
                variantScope, checkNotNull(variantScope.getTestedVariantData()).getScope());

        // Add a task to create the res values
        createGenerateResValuesTask(variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantScope, true, ImmutableSet.of());

        // Add tasks to compile shader
        createShaderTask(variantScope);

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantScope);

        // Add a task to generate resource source files
        createApkProcessResTask(variantScope);

        // process java resources
        createProcessJavaResTask(variantScope);

        createAidlTask(variantScope);

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(variantScope);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope, MergeType.MERGE);

        // Add a task to compile the test application
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);

        // Add a task to produce the signing config file
        createValidateSigningTask(variantScope);
        taskFactory.register(new SigningConfigWriterTask.CreationAction(variantScope));

        createPackagingTask(variantScope, null /* buildInfoGeneratorTask */);

        taskFactory.configure(
                ASSEMBLE_ANDROID_TEST,
                assembleTest ->
                        assembleTest.dependsOn(
                                variantData.getTaskContainer().getAssembleTask().getName()));

        createConnectedTestForVariant(variantScope);
    }

    /** Is the given variant relevant for lint? */
    private static boolean isLintVariant(@NonNull VariantScope variantScope) {
        // Only create lint targets for variants like debug and release, not debugTest
        final VariantType variantType = variantScope.getVariantConfiguration().getType();
        return !variantType.isTestComponent() && !variantType.isHybrid();
    }

    /**
     * Add tasks for running lint on individual variants. We've already added a lint task earlier
     * which runs on all variants.
     */
    public void createLintTasks(final VariantScope scope) {
        if (!isLintVariant(scope)) {
            return;
        }

        taskFactory.register(new LintPerVariantTask.CreationAction(scope));
    }

    /** Returns the full path of a task given its name. */
    private String getTaskPath(String taskName) {
        return project.getRootProject() == project
                ? ':' + taskName
                : project.getPath() + ':' + taskName;
    }

    private void maybeCreateLintVitalTask(@NonNull ApkVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

        if (!isLintVariant(variantScope)
                || variantScope.getInstantRunBuildContext().isInInstantRunMode()
                || variantConfig.getBuildType().isDebuggable()
                || !extension.getLintOptions().isCheckReleaseBuilds()) {
            return;
        }

        TaskProvider<LintPerVariantTask> lintReleaseCheck =
                taskFactory.register(
                        new LintPerVariantTask.VitalCreationAction(variantScope),
                        null,
                        task -> task.dependsOn(variantScope.getTaskContainer().getJavacTask()),
                        null);

        TaskFactoryUtils.dependsOn(
                variantScope.getTaskContainer().getAssembleTask(), lintReleaseCheck);

        // If lint is being run, we do not need to run lint vital.
        project.getGradle()
                .getTaskGraph()
                .whenReady(
                        taskGraph -> {
                            if (taskGraph.hasTask(getTaskPath(LINT))) {
                                project.getTasks()
                                        .getByName(lintReleaseCheck.getName())
                                        .setEnabled(false);
                            }
                        });
    }

    private void createRunUnitTestTask(
            @NonNull final VariantScope variantScope) {
        TaskProvider<AndroidUnitTest> runTestsTask =
                taskFactory.register(new AndroidUnitTest.CreationAction(variantScope));

        taskFactory.configure(JavaPlugin.TEST_TASK_NAME, test -> test.dependsOn(runTestsTask));
    }

    public void createTopLevelTestTasks(boolean hasFlavors) {
        createMockableJarTask();

        final List<String> reportTasks = Lists.newArrayListWithExpectedSize(2);

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // If more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.  Otherwise, create a top level connectedAndroidTest
        // Task.

        TaskProvider<? extends Task> connectedAndroidTestTask;
        if (hasFlavors) {
            connectedAndroidTestTask =
                    taskFactory.register(
                            new AndroidReportTask.CreationAction(
                                    globalScope,
                                    AndroidReportTask.CreationAction.TaskKind.CONNECTED));
            reportTasks.add(connectedAndroidTestTask.getName());
        } else {
            connectedAndroidTestTask =
                    taskFactory.register(
                            CONNECTED_ANDROID_TEST,
                            connectedTask -> {
                                connectedTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                connectedTask.setDescription(
                                        "Installs and runs instrumentation tests "
                                                + "for all flavors on connected devices.");
                            });
        }

        taskFactory.configure(
                CONNECTED_CHECK, check -> check.dependsOn(connectedAndroidTestTask.getName()));

        TaskProvider<? extends Task> deviceAndroidTestTask;
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            deviceAndroidTestTask =
                    taskFactory.register(
                            new AndroidReportTask.CreationAction(
                                    globalScope,
                                    AndroidReportTask.CreationAction.TaskKind.DEVICE_PROVIDER));
            reportTasks.add(deviceAndroidTestTask.getName());
        } else {
            deviceAndroidTestTask =
                    taskFactory.register(
                            DEVICE_ANDROID_TEST,
                            providerTask -> {
                                providerTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                providerTask.setDescription(
                                        "Installs and runs instrumentation tests "
                                                + "using all Device Providers.");
                            });
        }

        taskFactory.configure(
                DEVICE_CHECK, check -> check.dependsOn(deviceAndroidTestTask.getName()));

        // Create top level unit test tasks.

        taskFactory.register(
                JavaPlugin.TEST_TASK_NAME,
                unitTestTask -> {
                    unitTestTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                    unitTestTask.setDescription("Run unit tests for all variants.");
                });
        taskFactory.configure(
                JavaBasePlugin.CHECK_TASK_NAME,
                check -> check.dependsOn(JavaPlugin.TEST_TASK_NAME));

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        //TODO: move to mustRunAfter once is stable.
        if (!reportTasks.isEmpty() && project.getGradle().getStartParameter()
                .isContinueOnFailure()) {
            project.getGradle()
                    .getTaskGraph()
                    .whenReady(
                            taskGraph -> {
                                for (String reportTask : reportTasks) {
                                    if (taskGraph.hasTask(getTaskPath(reportTask))) {
                                        taskFactory.configure(
                                                reportTask,
                                                task -> ((AndroidReportTask) task).setWillRun());
                                    }
                                }
                            });
        }
    }

    protected void createConnectedTestForVariant(@NonNull final VariantScope testVariantScope) {
        final BaseVariantData baseVariantData =
                checkNotNull(testVariantScope.getTestedVariantData());
        final TestVariantData testVariantData = (TestVariantData) testVariantScope.getVariantData();

        boolean isLibrary = baseVariantData.getVariantConfiguration().getType().isAar();

        AbstractTestDataImpl testData;
        if (baseVariantData.getVariantConfiguration().getType().isDynamicFeature()) {
            testData =
                    new BundleTestDataImpl(
                            testVariantData,
                            testVariantScope
                                    .getArtifacts()
                                    .getFinalArtifactFiles(InternalArtifactType.APK),
                            FeatureSplitUtils.getFeatureName(globalScope.getProject().getPath()),
                            baseVariantData
                                    .getScope()
                                    .getArtifactFileCollection(
                                            RUNTIME_CLASSPATH, MODULE, APKS_FROM_BUNDLE));
        } else {
            testData =
                    new TestDataImpl(
                            testVariantData,
                            testVariantScope
                                    .getArtifacts()
                                    .getFinalArtifactFiles(InternalArtifactType.APK),
                            isLibrary
                                    ? null
                                    : testVariantData
                                            .getTestedVariantData()
                                            .getScope()
                                            .getArtifacts()
                                            .getFinalArtifactFiles(InternalArtifactType.APK));
        }

        configureTestData(testData);

        TaskProvider<DeviceProviderInstrumentTestTask> connectedTask =
                taskFactory.register(
                        new DeviceProviderInstrumentTestTask.CreationAction(
                                testVariantScope,
                                new ConnectedDeviceProvider(
                                        sdkHandler.getSdkInfo().getAdb(),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(logger)),
                                testData,
                                project.files() /* testTargetMetadata */));

        taskFactory.configure(
                CONNECTED_ANDROID_TEST,
                connectedAndroidTest -> connectedAndroidTest.dependsOn(connectedTask));

        if (baseVariantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {

            Configuration jacocoAntConfiguration =
                    JacocoConfigurations.getJacocoAntTaskConfiguration(
                            project, JacocoTask.getJacocoVersion(testVariantScope));
            TaskProvider<JacocoReportTask> reportTask =
                    taskFactory.register(
                            new JacocoReportTask.CreationAction(
                                    testVariantScope, jacocoAntConfiguration));

            TaskFactoryUtils.dependsOn(
                    baseVariantData.getScope().getTaskContainer().getCoverageReportTask(),
                    reportTask);

            taskFactory.configure(
                    CONNECTED_ANDROID_TEST,
                    connectedAndroidTest -> connectedAndroidTest.dependsOn(reportTask));
        }

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // now the providers.
        for (DeviceProvider deviceProvider : providers) {

            final TaskProvider<DeviceProviderInstrumentTestTask> providerTask =
                    taskFactory.register(
                            new DeviceProviderInstrumentTestTask.CreationAction(
                                    testVariantData.getScope(),
                                    deviceProvider,
                                    testData,
                                    project.files() /* testTargetMetadata */));

            taskFactory.configure(
                    DEVICE_ANDROID_TEST,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(providerTask));
        }

        // now the test servers
        List<TestServer> servers = extension.getTestServers();
        for (final TestServer testServer : servers) {
            final TaskProvider<TestServerTask> serverTask =
                    taskFactory.register(
                            new TestServerTask.TestServerTaskCreationAction(
                                    testVariantScope, testServer));
            TaskFactoryUtils.dependsOn(
                    serverTask, testVariantScope.getTaskContainer().getAssembleTask());

            taskFactory.configure(
                    DEVICE_CHECK, deviceAndroidTest -> deviceAndroidTest.dependsOn(serverTask));
        }
    }

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     * These tasks create the dex file from the .class files, plus optional intermediary steps like
     * proguard and jacoco
     */
    public void createPostCompilationTasks(

            @NonNull final VariantScope variantScope) {

        checkNotNull(variantScope.getTaskContainer().getJavacTask());

        final BaseVariantData variantData = variantScope.getVariantData();
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        TransformManager transformManager = variantScope.getTransformManager();

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled =
                config.getBuildType().isTestCoverageEnabled()
                        && !config.getType().isForTesting()
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode();
        if (isTestCoverageEnabled) {
            createJacocoTask(variantScope);
        }

        maybeCreateDesugarTask(
                variantScope, config.getMinSdkVersion(), transformManager, isTestCoverageEnabled);

        AndroidConfig extension = variantScope.getGlobalScope().getExtension();

        // Merge Java Resources.
        createMergeJavaResTransform(variantScope);

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        for (int i = 0, count = customTransforms.size(); i < count; i++) {
            Transform transform = customTransforms.get(i);

            List<Object> deps = customTransformsDependencies.get(i);
            transformManager.addTransform(
                    taskFactory,
                    variantScope,
                    transform,
                    null,
                    task -> {
                        if (!deps.isEmpty()) {
                            task.dependsOn(deps);
                        }
                    },
                    taskProvider -> {
                        // if the task is a no-op then we make assemble task depend on it.
                        if (transform.getScopes().isEmpty()) {
                            TaskFactoryUtils.dependsOn(
                                    variantScope.getTaskContainer().getAssembleTask(),
                                    taskProvider);
                        }
                    });
        }

        // Add transform to create merged runtime classes if this is a feature, a dynamic-feature,
        // or a base module consuming feature jars. Merged runtime classes are needed if code
        // minification is enabled in a project with features or dynamic-features.
        if (variantData.getType().isFeatureSplit() || variantScope.consumesFeatureJars()) {
            createMergeClassesTransform(variantScope);
        }

        // ----- Android studio profiling transforms
        final VariantType type = variantData.getType();
        if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()
                && type.isApk()
                && !type.isForTesting()) {
            boolean addDependencies = !type.isFeatureSplit();
            for (String jar : getAdvancedProfilingTransforms(projectOptions)) {
                if (jar != null) {
                    transformManager.addTransform(
                            taskFactory,
                            variantScope,
                            new CustomClassTransform(jar, addDependencies));
                }
            }
        }

        // ----- Minify next -----
        CodeShrinker shrinker = maybeCreateJavaCodeShrinkerTransform(variantScope);
        if (shrinker == CodeShrinker.R8) {
            maybeCreateResourcesShrinkerTransform(variantScope);
            maybeCreateDexSplitterTransform(variantScope);
            // TODO: create JavaResSplitterTransform and call it here (http://b/77546738)
            return;
        }

        // ----- 10x support
        TaskProvider<PreColdSwapTask> preColdSwapTask = null;
        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {

            TaskProvider<? extends Task> allActionsAnchorTask =
                    createInstantRunAllActionsTasks(variantScope);
            assert variantScope.getInstantRunTaskManager() != null;
            preColdSwapTask =
                    variantScope.getInstantRunTaskManager().createPreColdswapTask(projectOptions);
            TaskFactoryUtils.dependsOn(preColdSwapTask, allActionsAnchorTask);
        }

        // ----- Multi-Dex support
        DexingType dexingType = variantScope.getDexingType();

        // Upgrade from legacy multi-dex to native multi-dex if possible when using with a device
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            if (variantScope.getVariantConfiguration().isMultiDexEnabled()
                    && variantScope
                                    .getVariantConfiguration()
                                    .getMinSdkVersionWithTargetDeviceApi()
                                    .getFeatureLevel()
                            >= 21) {
                dexingType = DexingType.NATIVE_MULTIDEX;
            }
        }

        if (variantScope.getNeedsMainDexList()) {

            // ---------
            // create the transform that's going to take the code and the proguard keep list
            // from above and compute the main class list.
            D8MainDexListTransform multiDexTransform = new D8MainDexListTransform(variantScope);

            transformManager.addTransform(
                    taskFactory,
                    variantScope,
                    multiDexTransform,
                    taskName -> {
                        File mainDexListFile =
                                variantScope
                                        .getArtifacts()
                                        .appendArtifact(
                                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST,
                                                taskName,
                                                "mainDexList.txt");
                        multiDexTransform.setMainDexListOutputFile(mainDexListFile);
                    },
                    null,
                    variantScope::addColdSwapBuildTask);
        }

        if (variantScope.getNeedsMainDexListForBundle()) {
            D8MainDexListTransform bundleMultiDexTransform =
                    new D8MainDexListTransform(variantScope, true);
            variantScope
                    .getTransformManager()
                    .addTransform(
                            taskFactory,
                            variantScope,
                            bundleMultiDexTransform,
                            taskName -> {
                                File mainDexListFile =
                                        variantScope
                                                .getArtifacts()
                                                .appendArtifact(
                                                        InternalArtifactType
                                                                .MAIN_DEX_LIST_FOR_BUNDLE,
                                                        taskName,
                                                        "mainDexList.txt");
                                bundleMultiDexTransform.setMainDexListOutputFile(mainDexListFile);
                            },
                            null,
                            null);
        }

        createDexTasks(variantScope, dexingType);

        if (preColdSwapTask != null) {
            for (TaskProvider<? extends Task> task : variantScope.getColdSwapBuildTasks()) {
                TaskFactoryUtils.dependsOn(task, preColdSwapTask);
            }
        }
        maybeCreateResourcesShrinkerTransform(variantScope);

        // TODO: support DexSplitterTransform when IR enabled (http://b/77585545)
        maybeCreateDexSplitterTransform(variantScope);
        // TODO: create JavaResSplitterTransform and call it here (http://b/77546738)
    }

    private void maybeCreateDesugarTask(
            @NonNull VariantScope variantScope,
            @NonNull AndroidVersion minSdk,
            @NonNull TransformManager transformManager,
            boolean isTestCoverageEnabled) {
        if (variantScope.getJava8LangSupportType() == Java8LangSupport.DESUGAR) {
            FileCache userCache = getUserIntermediatesCache();

            variantScope
                    .getTransformManager()
                    .consumeStreams(
                            ImmutableSet.of(Scope.EXTERNAL_LIBRARIES),
                            TransformManager.CONTENT_CLASS);

            taskFactory.register(
                    new RecalculateStackFramesTask.CreationAction(
                            variantScope, userCache, isTestCoverageEnabled));

            variantScope
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "fixed-stack-frames-classes")
                                    .addContentTypes(TransformManager.CONTENT_CLASS)
                                    .addScope(Scope.EXTERNAL_LIBRARIES)
                                    .setFileCollection(
                                            variantScope
                                                    .getArtifacts()
                                                    .getFinalArtifactFiles(
                                                            InternalArtifactType.FIXED_STACK_FRAMES)
                                                    .get()
                                                    .getAsFileTree())
                                    .build());

            DesugarTransform desugarTransform =
                    new DesugarTransform(
                            variantScope.getBootClasspath(),
                            userCache,
                            minSdk.getFeatureLevel(),
                            globalScope.getAndroidBuilder().getJavaProcessExecutor(),
                            project.getLogger().isEnabled(LogLevel.INFO),
                            projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS),
                            variantScope.getGlobalScope().getTmpFolder().toPath(),
                            getProjectVariantId(variantScope),
                            enableDesugarBugFixForJacoco(variantScope));
            transformManager.addTransform(taskFactory, variantScope, desugarTransform);

            if (minSdk.getFeatureLevel()
                    >= DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
                return;
            }

            QualifiedContent.ScopeType scopeType = Scope.EXTERNAL_LIBRARIES;
            if (variantScope.getVariantConfiguration().getType().isTestComponent()) {
                BaseVariantData testedVariant =
                        Objects.requireNonNull(variantScope.getTestedVariantData());
                if (!testedVariant.getType().isAar()) {
                    // test variants, except for library, should not package try-with-resources jar
                    // as the tested variant already contains it.
                    scopeType = Scope.PROVIDED_ONLY;
                }
            }

            // add runtime classes for try-with-resources support
            String taskName = variantScope.getTaskName(ExtractTryWithResourcesSupportJar.TASK_NAME);
            TaskProvider<ExtractTryWithResourcesSupportJar> extractTryWithResources =
                    taskFactory.register(
                            new ExtractTryWithResourcesSupportJar.CreationAction(
                                    variantScope.getTryWithResourceRuntimeSupportJar(),
                                    taskName,
                                    variantScope.getFullVariantName()));
            variantScope.getTryWithResourceRuntimeSupportJar().builtBy(extractTryWithResources);
            transformManager.addStream(
                    OriginalStream.builder(project, "runtime-deps-try-with-resources")
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(scopeType)
                            .setFileCollection(variantScope.getTryWithResourceRuntimeSupportJar())
                            .build());
        }
    }

    /**
     * Creates tasks used for DEX generation. This will use an incremental pipeline that uses dex
     * archives in order to enable incremental dexing support.
     */
    private void createDexTasks(
            @NonNull VariantScope variantScope, @NonNull DexingType dexingType) {
        TransformManager transformManager = variantScope.getTransformManager();

        DefaultDexOptions dexOptions;
        if (variantScope.getVariantData().getType().isTestComponent()) {
            // Don't use custom dx flags when compiling the test FULL_APK. They can break the test FULL_APK,
            // like --minimal-main-dex.
            dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
            dexOptions.setAdditionalParameters(ImmutableList.of());
        } else {
            dexOptions = extension.getDexOptions();
        }

        boolean minified = variantScope.getCodeShrinker() != null;
        boolean enableDexingArtifactTransform =
                globalScope.getProjectOptions().get(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM)
                        && extension.getTransforms().isEmpty()
                        && !minified
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode()
                        && variantScope.getJava8LangSupportType() == Java8LangSupport.UNUSED
                        && getAdvancedProfilingTransforms(projectOptions).isEmpty();
        FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());
        DexArchiveBuilderTransform preDexTransform =
                new DexArchiveBuilderTransformBuilder()
                        .setAndroidJarClasspath(
                                () ->
                                        variantScope
                                                .getGlobalScope()
                                                .getAndroidBuilder()
                                                .getBootClasspath(false))
                        .setDexOptions(dexOptions)
                        .setMessageReceiver(variantScope.getGlobalScope().getMessageReceiver())
                        .setUserLevelCache(userLevelCache)
                        .setMinSdkVersion(variantScope.getMinSdkVersion().getFeatureLevel())
                        .setDexer(variantScope.getDexer())
                        .setUseGradleWorkers(
                                projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS))
                        .setInBufferSize(projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE))
                        .setOutBufferSize(
                                projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE))
                        .setIsDebuggable(
                                variantScope
                                        .getVariantConfiguration()
                                        .getBuildType()
                                        .isDebuggable())
                        .setJava8LangSupportType(variantScope.getJava8LangSupportType())
                        .setProjectVariant(getProjectVariantId(variantScope))
                        .setNumberOfBuckets(
                                projectOptions.get(IntegerOption.DEXING_NUMBER_OF_BUCKETS))
                        .setIncludeFeaturesInScope(variantScope.consumesFeatureJars())
                        .setIsInstantRun(
                                variantScope.getInstantRunBuildContext().isInInstantRunMode())
                        .setEnableDexingArtifactTransform(enableDexingArtifactTransform)
                        .createDexArchiveBuilderTransform();
        transformManager
                .addTransform(taskFactory, variantScope, preDexTransform)
                .ifPresent(variantScope::addColdSwapBuildTask);

        if (projectOptions.get(BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK)) {
            taskFactory.register(new CheckDuplicateClassesTask.CreationAction(variantScope));
        }

        if (enableDexingArtifactTransform) {
            createDexMergingWithArtifactTransforms(variantScope, dexingType);
        } else {
            createDexMerging(variantScope, dexingType);
        }
    }

    private void createDexMerging(
            @NonNull VariantScope variantScope, @NonNull DexingType dexingType) {
        boolean isDebuggable = variantScope.getVariantConfiguration().getBuildType().isDebuggable();
        if (dexingType != DexingType.LEGACY_MULTIDEX
                && variantScope.getCodeShrinker() == null
                && extension.getTransforms().isEmpty()) {
            ExternalLibsMergerTransform externalLibsMergerTransform =
                    new ExternalLibsMergerTransform(
                            dexingType,
                            variantScope.getDexMerger(),
                            variantScope.getMinSdkVersion().getFeatureLevel(),
                            isDebuggable,
                            variantScope.getGlobalScope().getMessageReceiver(),
                            DexMergerTransformCallable::new);

            variantScope
                    .getTransformManager()
                    .addTransform(taskFactory, variantScope, externalLibsMergerTransform);
        }

        DexMergerTransform dexTransform =
                new DexMergerTransform(
                        dexingType,
                        dexingType == DexingType.LEGACY_MULTIDEX
                                ? variantScope
                                        .getArtifacts()
                                        .getFinalArtifactFiles(
                                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
                                : null,
                        variantScope
                                .getArtifacts()
                                .getFinalArtifactFiles(
                                        InternalArtifactType.DUPLICATE_CLASSES_CHECK),
                        variantScope.getGlobalScope().getMessageReceiver(),
                        variantScope.getDexMerger(),
                        variantScope.getMinSdkVersion().getFeatureLevel(),
                        isDebuggable,
                        variantScope.consumesFeatureJars(),
                        variantScope.getInstantRunBuildContext().isInInstantRunMode());
        variantScope
                .getTransformManager()
                .addTransform(
                        taskFactory,
                        variantScope,
                        dexTransform,
                        null,
                        null,
                        variantScope::addColdSwapBuildTask);
    }

    /**
     * Set up dex merging tasks when artifact transforms are used.
     *
     * <p>External libraries are merged in mono-dex and native multidex modes. In case of a native
     * multidex debuggable variant these dex files get packaged. In mono-dex case, we will re-merge
     * these files. Because this task will be almost always up-to-date, having a second merger run
     * over the external libraries will not cause a performance regression. In addition to that,
     * second dex merger will perform less I/O compared to reading all external library dex files
     * individually. For legacy multidex, we must merge all dex files in a single invocation in
     * order to generate correct primary dex file in presence of desugaring. See b/120039166.
     *
     * <p>When merging native multidex, debuggable variant, project's dex files are merged
     * independently. Also, the library projects' dex files are merged independently.
     *
     * <p>For all other variants (release, mono-dex, legacy-multidex), we merge all dex files in a
     * single invocation. This means that external libraries, library projects and project dex files
     * will be merged in a single task.
     */
    private void createDexMergingWithArtifactTransforms(
            @NonNull VariantScope variantScope, @NonNull DexingType dexingType) {
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            DexMergingTask.CreationAction configAction =
                    new DexMergingTask.CreationAction(
                            variantScope, DexMergingAction.MERGE_ALL, dexingType);
            taskFactory.register(configAction);
        } else {
            boolean produceSeparateOutputs =
                    dexingType == DexingType.NATIVE_MULTIDEX
                            && variantScope.getVariantConfiguration().getBuildType().isDebuggable();

            taskFactory.register(
                    new DexMergingTask.CreationAction(
                            variantScope,
                            DexMergingAction.MERGE_EXTERNAL_LIBS,
                            DexingType.NATIVE_MULTIDEX,
                            produceSeparateOutputs
                                    ? InternalArtifactType.DEX
                                    : InternalArtifactType.EXTERNAL_LIBS_DEX));

            if (produceSeparateOutputs) {
                DexMergingTask.CreationAction mergeProject =
                        new DexMergingTask.CreationAction(
                                variantScope, DexMergingAction.MERGE_PROJECT, dexingType);
                taskFactory.register(mergeProject);

                DexMergingTask.CreationAction mergeLibraries =
                        new DexMergingTask.CreationAction(
                                variantScope,
                                DexMergingAction.MERGE_LIBRARY_PROJECTS,
                                dexingType,
                                InternalArtifactType.DEX);
                taskFactory.register(mergeLibraries);
            } else {
                DexMergingTask.CreationAction configAction =
                        new DexMergingTask.CreationAction(
                                variantScope, DexMergingAction.MERGE_ALL, dexingType);
                taskFactory.register(configAction);
            }
        }

        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "final-dex")
                                .addContentTypes(ExtendedContentType.DEX)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        variantScope
                                                .getArtifacts()
                                                .getFinalArtifactFiles(InternalArtifactType.DEX)
                                                .get())
                                .build());
    }

    @NonNull
    private static String getProjectVariantId(@NonNull VariantScope variantScope) {
        return variantScope.getGlobalScope().getProject().getName()
                + ":"
                + variantScope.getFullVariantName();
    }

    @Nullable
    private FileCache getUserDexCache(boolean isMinifiedEnabled, boolean preDexLibraries) {
        if (!preDexLibraries || isMinifiedEnabled) {
            return null;
        }

        return getUserIntermediatesCache();
    }

    @Nullable
    private FileCache getUserIntermediatesCache() {
        if (globalScope
                .getProjectOptions()
                .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            return globalScope.getBuildCache();
        } else {
            return null;
        }
    }

    /** Create InstantRun related tasks that should be ran right after the java compilation task. */
    @NonNull
    private TaskProvider<? extends Task> createInstantRunAllActionsTasks(
            @NonNull VariantScope variantScope) {

        TaskProvider<? extends Task> allActionAnchorTask =
                taskFactory.register(new InstantRunAnchorTaskCreationAction(variantScope));

        TransformManager transformManager = variantScope.getTransformManager();

        ExtractJarsTransform extractJarsTransform =
                new ExtractJarsTransform(
                        ImmutableSet.of(DefaultContentType.CLASSES),
                        ImmutableSet.of(Scope.SUB_PROJECTS));
        Optional<TaskProvider<TransformTask>> extractJarsTask =
                transformManager.addTransform(taskFactory, variantScope, extractJarsTransform);

        InstantRunTaskManager instantRunTaskManager =
                new InstantRunTaskManager(
                        getLogger(),
                        variantScope,
                        variantScope.getTransformManager(),
                        taskFactory,
                        recorder);

        // setting up a fake dependency on the merged manifest to force initialization
        Provider<Directory> mergedManifests =
                variantScope.getArtifacts().getFinalProduct(MERGED_MANIFESTS);

        Provider<Directory> instantRunMergedManifests =
                variantScope.getArtifacts().getFinalProduct(INSTANT_RUN_MERGED_MANIFESTS);

        variantScope.setInstantRunTaskManager(instantRunTaskManager);
        AndroidVersion minSdkForDx = variantScope.getMinSdkVersion();
        TaskProvider<BuildInfoLoaderTask> buildInfoLoaderTask =
                instantRunTaskManager.createInstantRunAllTasks(
                        extractJarsTask.orElse(null),
                        allActionAnchorTask,
                        getResMergingScopes(variantScope),
                        mergedManifests,
                        instantRunMergedManifests,
                        true /* addResourceVerifier */,
                        minSdkForDx.getFeatureLevel(),
                        variantScope.getJava8LangSupportType() == Java8LangSupport.D8,
                        variantScope.getBootClasspath(),
                        globalScope.getAndroidBuilder().getMessageReceiver());

        TaskFactoryUtils.dependsOn(
                variantScope.getTaskContainer().getSourceGenTask(), buildInfoLoaderTask);

        return allActionAnchorTask;
    }

    protected void handleJacocoDependencies(@NonNull VariantScope variantScope) {
        GradleVariantConfiguration config = variantScope.getVariantConfiguration();
        // we add the jacoco jar if coverage is enabled, but we don't add it
        // for test apps as it's already part of the tested app.
        // For library project, since we cannot use the local jars of the library,
        // we add it as well.
        boolean isTestCoverageEnabled =
                config.getBuildType().isTestCoverageEnabled()
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode()
                        && (!config.getType().isTestComponent()
                                || (config.getTestedConfig() != null
                                        && config.getTestedConfig().getType().isAar()));
        if (isTestCoverageEnabled) {
            if (variantScope.getDexer() == DexerTool.DX) {
                globalScope
                        .getAndroidBuilder()
                        .getIssueReporter()
                        .reportWarning(
                                Type.GENERIC,
                                String.format(
                                        "Jacoco version is downgraded to %s because dx is used. "
                                                + "This is due to -P%s=false flag. See "
                                                + "https://issuetracker.google.com/37116789 for "
                                                + "more details.",
                                        JacocoConfigurations.VERSION_FOR_DX,
                                        BooleanOption.ENABLE_D8.getPropertyName()));
            }

            String jacocoAgentRuntimeDependency =
                    JacocoConfigurations.getAgentRuntimeDependency(
                            JacocoTask.getJacocoVersion(variantScope));
            project.getDependencies()
                    .add(
                            variantScope.getVariantDependencies().getRuntimeClasspath().getName(),
                            jacocoAgentRuntimeDependency);

            // we need to force the same version of Jacoco we use for instrumentation
            variantScope
                    .getVariantDependencies()
                    .getRuntimeClasspath()
                    .resolutionStrategy(r -> r.force(jacocoAgentRuntimeDependency));
        }
    }

    /**
     * If a fix in Desugar should be enabled to handle broken bytecode produced by older Jacoco, see
     * http://b/62623509.
     */
    private boolean enableDesugarBugFixForJacoco(@NonNull VariantScope scope) {
        try {
            GradleVersion current = GradleVersion.parse(JacocoTask.getJacocoVersion(scope));
            return JacocoConfigurations.MIN_WITHOUT_BROKEN_BYTECODE.compareTo(current) > 0;
        } catch (Throwable ignored) {
            // Cannot determine using version comparison, avoid passing the flag.
            return true;
        }
    }

    public void createJacocoTask(@NonNull final VariantScope variantScope) {
        variantScope
                .getTransformManager()
                .consumeStreams(
                        ImmutableSet.of(Scope.PROJECT),
                        ImmutableSet.of(DefaultContentType.CLASSES));
        taskFactory.register(new JacocoTask.CreationAction(variantScope));

        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "jacoco-instrumented-classes")
                                .addContentTypes(DefaultContentType.CLASSES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        variantScope
                                                .getArtifacts()
                                                .getFinalArtifactFiles(
                                                        InternalArtifactType
                                                                .JACOCO_INSTRUMENTED_CLASSES)
                                                .get())
                                .build());
    }

    private void createDataBindingMergeArtifactsTask(@NonNull VariantScope variantScope) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        final BaseVariantData variantData = variantScope.getVariantData();
        VariantType type = variantData.getType();
        if (type.isForTesting() && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = checkNotNull(variantScope.getTestedVariantData());
            if (!testedVariantData.getType().isAar()) {
                return;
            }
        }
        taskFactory.register(
                new DataBindingMergeDependencyArtifactsTask.CreationAction(variantScope));
    }

    private void createDataBindingMergeBaseClassesTask(@NonNull VariantScope variantScope) {
        final BaseVariantData variantData = variantScope.getVariantData();
        VariantType type = variantData.getType();
        if (type.isForTesting() && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = checkNotNull(variantScope.getTestedVariantData());
            if (!testedVariantData.getType().isAar()) {
                return;
            }
        }

        taskFactory.register(new DataBindingMergeBaseClassLogTask.CreationAction(variantScope));
    }

    protected void createDataBindingTasksIfNecessary(
            @NonNull VariantScope scope, @NonNull MergeType mergeType) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        createDataBindingMergeBaseClassesTask(scope);
        createDataBindingMergeArtifactsTask(scope);


        VariantType type = scope.getType();
        if (type.isForTesting() && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = checkNotNull(scope.getTestedVariantData());
            if (!testedVariantData.getType().isAar()) {
                return;
            }
        }

        dataBindingBuilder.setDebugLogEnabled(getLogger().isDebugEnabled());

        taskFactory.register(new DataBindingExportBuildInfoTask.CreationAction(scope));
        taskFactory.register(new DataBindingGenBaseClassesTask.CreationAction(scope));

        setDataBindingAnnotationProcessorParams(scope, mergeType);
    }

    private void setDataBindingAnnotationProcessorParams(
            @NonNull VariantScope scope, @NonNull MergeType mergeType) {
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        JavaCompileOptions javaCompileOptions = variantConfiguration.getJavaCompileOptions();
        AnnotationProcessorOptions processorOptions =
                javaCompileOptions.getAnnotationProcessorOptions();
        if (processorOptions
                instanceof com.android.build.gradle.internal.dsl.AnnotationProcessorOptions) {
            com.android.build.gradle.internal.dsl.AnnotationProcessorOptions options =
                    (com.android.build.gradle.internal.dsl.AnnotationProcessorOptions)
                            processorOptions;
            // We want to pass data binding processor's class name to the Java compiler. However, if
            // the class names of other annotation processors were not added previously, adding the
            // class name of data binding alone would disable Java compiler's automatic discovery of
            // annotation processors and the other annotation processors would not be invoked.
            // Therefore, we add data binding only if another class name was specified before.
            if (!options.getClassNames().isEmpty()
                    && !options.getClassNames().contains(DataBindingBuilder.PROCESSOR_NAME)) {
                options.className(DataBindingBuilder.PROCESSOR_NAME);
            }

            DataBindingCompilerArguments dataBindingArgs =
                    DataBindingCompilerArguments.createArguments(
                            scope,
                            getLogger().isDebugEnabled(),
                            dataBindingBuilder.getPrintMachineReadableOutput());
            options.compilerArgumentProvider(dataBindingArgs);
        } else {
            getLogger().error("Cannot setup data binding for %s because java compiler options"
                    + " is not an instance of AnnotationProcessorOptions", processorOptions);
        }
    }

    /**
     * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
     *
     * @param fullBuildInfoGeneratorTask task that generates the build-info.xml for full build.
     */
    public void createPackagingTask(
            @NonNull VariantScope variantScope,
            @Nullable TaskProvider<BuildInfoWriterTask> fullBuildInfoGeneratorTask) {
        ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();
        final MutableTaskContainer taskContainer = variantData.getScope().getTaskContainer();

        boolean signedApk = variantData.isSigned();

        /*
         * PrePackaging step class that will look if the packaging of the main FULL_APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split FULL_APK. However when a warm swap
         * is possible, it is not necessary to produce immediately the new main SPLIT since the
         * runtime use the resources.ap_ file directly. However, as soon as an incompatible change
         * forcing a cold swap is triggered, the main FULL_APK must be rebuilt (even if the
         * resources were changed in a previous build).
         */
        InternalArtifactType manifestType = variantScope.getManifestArtifactType();

        final boolean splitsArePossible =
                variantScope.getVariantData().getMultiOutputPolicy() == MultiOutputPolicy.SPLITS;

        Provider<Directory> manifests = variantScope.getArtifacts().getFinalProduct(manifestType);

        // this is where the final APKs will be located.
        File finalApkLocation = variantScope.getApkLocation();
        // if we are not dealing with possible splits, we can generate in the final folder
        // directly.
        File outputDirectory =
                splitsArePossible
                        ? variantScope.getFullApkPackagesOutputDirectory()
                        : finalApkLocation;

        InternalArtifactType taskOutputType =
                splitsArePossible ? InternalArtifactType.FULL_APK : InternalArtifactType.APK;

        boolean useSeparateApkForResources =
                variantScope.getInstantRunBuildContext().useSeparateApkForResources();

        InternalArtifactType resourceFilesInputType =
                variantScope.useResourceShrinker()
                        ? InternalArtifactType.SHRUNK_PROCESSED_RES
                        : InternalArtifactType.PROCESSED_RES;

        // Common code for both packaging tasks.
        Action<Task> configureResourcesAndAssetsDependencies =
                task -> {
                    task.dependsOn(taskContainer.getMergeAssetsTask());
                    if (taskContainer.getProcessAndroidResTask() != null) {
                        task.dependsOn(taskContainer.getProcessAndroidResTask());
                    }
                };

        TaskProvider<PackageApplication> packageApp =
                taskFactory.register(
                        new PackageApplication.StandardCreationAction(
                                variantScope,
                                outputDirectory,
                                useSeparateApkForResources
                                        ? INSTANT_RUN_MAIN_APK_RESOURCES
                                        : resourceFilesInputType,
                                manifests,
                                manifestType,
                                variantScope.getOutputScope(),
                                globalScope.getBuildCache(),
                                taskOutputType),
                        null,
                        task -> {
                            //noinspection VariableNotUsedInsideIf - we use the whole packaging scope below.

                            task.dependsOn(taskContainer.getJavacTask());

                            if (taskContainer.getPackageSplitResourcesTask() != null) {
                                task.dependsOn(taskContainer.getPackageSplitResourcesTask());
                            }
                            if (taskContainer.getPackageSplitAbiTask() != null) {
                                task.dependsOn(taskContainer.getPackageSplitAbiTask());
                            }

                            // FIX ME : Reinstate once ShrinkResourcesTransform is converted.
                            //if ( variantOutputScope.getShrinkResourcesTask() != null) {
                            //    packageApp.dependsOn( variantOutputScope.getShrinkResourcesTask());
                            //}

                            configureResourcesAndAssetsDependencies.execute(task);
                        },
                        null);

        TaskProvider<? extends Task> packageInstantRunResources = null;

        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {
            packageInstantRunResources =
                    taskFactory.register(
                            new InstantRunResourcesApkBuilder.CreationAction(
                                    resourceFilesInputType, variantScope));

            // make sure the task run even if none of the files we consume are available,
            // this is necessary so we can clean up output.
            TaskFactoryUtils.dependsOn(packageApp, packageInstantRunResources);

            if (!useSeparateApkForResources) {
                // in instantRunMode, there is no user configured splits, only one apk.
                packageInstantRunResources =
                        taskFactory.register(
                                new PackageApplication.InstantRunResourcesCreationAction(
                                        variantScope.getInstantRunResourcesFile(),
                                        variantScope,
                                        resourceFilesInputType,
                                        manifests,
                                        INSTANT_RUN_MERGED_MANIFESTS,
                                        globalScope.getBuildCache(),
                                        variantScope.getOutputScope()));
            }

            // Make sure the MAIN artifact is registered after the RESOURCES one.
            TaskFactoryUtils.dependsOn(packageApp, packageInstantRunResources);
        }

        if (packageInstantRunResources != null) {
            packageInstantRunResources.configure(configureResourcesAndAssetsDependencies);
        }

        TaskFactoryUtils.dependsOn(taskContainer.getAssembleTask(), packageApp.getName());

        if (fullBuildInfoGeneratorTask != null) {
            final TaskProvider<? extends Task> fPackageInstantRunResources =
                    packageInstantRunResources;
            fullBuildInfoGeneratorTask.configure(
                    t -> {
                        t.mustRunAfter(packageApp);
                        if (fPackageInstantRunResources != null) {
                            t.mustRunAfter(fPackageInstantRunResources);
                        }
                    });
            TaskFactoryUtils.dependsOn(taskContainer.getAssembleTask(), fullBuildInfoGeneratorTask);
        }

        if (splitsArePossible) {

            TaskProvider<CopyOutputs> copyOutputsTask =
                    taskFactory.register(
                            new CopyOutputs.CreationAction(variantScope, finalApkLocation));
            TaskFactoryUtils.dependsOn(taskContainer.getAssembleTask(), copyOutputsTask);
        }

        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            createInstallTask(variantScope);
        }

        maybeCreateLintVitalTask(variantData);

        // add an uninstall task
        final TaskProvider<UninstallTask> uninstallTask =
                taskFactory.register(new UninstallTask.CreationAction(variantScope));

        taskFactory.configure(UNINSTALL_ALL, uninstallAll -> uninstallAll.dependsOn(uninstallTask));
    }

    protected void createInstallTask(VariantScope variantScope) {
        taskFactory.register(new InstallVariantTask.CreationAction(variantScope));
    }

    @Nullable
    protected void createValidateSigningTask(@NonNull VariantScope variantScope) {
        if (variantScope.getVariantConfiguration().getSigningConfig() == null) {
            return;
        }

        // FIXME create one per signing config instead of one per variant.
        taskFactory.register(
                new ValidateSigningTask.CreationAction(
                        variantScope, GradleKeystoreHelper.getDefaultDebugKeystoreLocation()));
    }

    /**
     * Create assemble* and bundle* anchor tasks.
     *
     * <p>This does not create the variant specific version of these tasks only the ones that are
     * per build-type, per-flavor, per-flavor-combo and the main 'assemble' and 'bundle' ones.
     *
     * @param variantScopes the list of variant scopes.
     * @param flavorCount the number of flavors
     * @param flavorDimensionCount whether there are flavor dimensions at all.
     * @param variantTypeCount the number of variant types generated.
     */
    public void createAnchorAssembleTasks(
            @NonNull List<VariantScope> variantScopes,
            int flavorCount,
            int flavorDimensionCount,
            int variantTypeCount) {

        // sub anchor tasks that the main 'assemble' and 'bundle task will depend.
        List<TaskProvider<? extends Task>> subAssembleTasks = Lists.newArrayList();
        List<TaskProvider<? extends Task>> subBundleTasks = Lists.newArrayList();

        // There are 3 different scenarios:
        // 1. There are 1+ flavors. In this case the variant-specific assemble task is
        //    different from all the assemble<BuildType> or assemble<Flavor>
        // 2. There is no flavor but this is a feature plugin that has 2 different variants for
        //    the same build type (aar + feature), so we still create a specific assemble<buildType>
        //    which depends on the variant specific assemble task.
        // 3. Else, the assemble<BuildType> is the same as the variant specific assemble task.

        // Case #1
        if (flavorCount > 0) {
            // loop on the variants and record their build type/flavor usage.
            // map from build type/flavor names to the variant-specific assemble/bundle tasks
            ListMultimap<String, TaskProvider<? extends Task>> assembleMap =
                    ArrayListMultimap.create();
            ListMultimap<String, TaskProvider<? extends Task>> bundleMap =
                    ArrayListMultimap.create();

            for (VariantScope variantScope : variantScopes) {
                final VariantType variantType = variantScope.getType();
                if (!variantType.isTestComponent()) {
                    final MutableTaskContainer taskContainer = variantScope.getTaskContainer();
                    final GradleVariantConfiguration variantConfig =
                            variantScope.getVariantConfiguration();

                    final TaskProvider<? extends Task> assembleTask =
                            taskContainer.getAssembleTask();
                    assembleMap.put(variantConfig.getBuildType().getName(), assembleTask);

                    for (CoreProductFlavor flavor : variantConfig.getProductFlavors()) {
                        assembleMap.put(flavor.getName(), assembleTask);
                    }

                    // if 2+ flavor dimensions, then make an assemble for the flavor combo
                    if (flavorDimensionCount > 1) {
                        assembleMap.put(variantConfig.getFlavorName(), assembleTask);
                    }

                    // fill the bundle map only if the variant supports bundles.
                    if (variantType.isBaseModule()) {
                        TaskProvider<? extends Task> bundleTask = taskContainer.getBundleTask();

                        bundleMap.put(variantConfig.getBuildType().getName(), bundleTask);

                        for (CoreProductFlavor flavor : variantConfig.getProductFlavors()) {
                            bundleMap.put(flavor.getName(), bundleTask);
                        }

                        // if 2+ flavor dimensions, then make an assemble for the flavor combo
                        if (flavorDimensionCount > 1) {
                            bundleMap.put(variantConfig.getFlavorName(), bundleTask);
                        }
                    }
                }
            }

            // loop over the map of build-type/flavor to create tasks for each, setting a dependency
            // on the variant-specific task.
            // these keys should be the same for bundle and assemble
            Set<String> dimensionKeys = assembleMap.keySet();

            for (String dimensionKey : dimensionKeys) {
                final String dimensionName = StringHelper.capitalize(dimensionKey);

                // create the task and add it to the list
                subAssembleTasks.add(
                        taskFactory.register(
                                "assemble" + dimensionName,
                                task -> {
                                    task.setDescription(
                                            "Assembles main outputs for all "
                                                    + dimensionName
                                                    + " variants.");
                                    task.setGroup(BasePlugin.BUILD_GROUP);
                                    task.dependsOn(assembleMap.get(dimensionKey));
                                }));

                List<TaskProvider<? extends Task>> subBundleMap = bundleMap.get(dimensionKey);
                if (!subBundleMap.isEmpty()) {

                    // create the task and add it to the list
                    subBundleTasks.add(
                            taskFactory.register(
                                    "bundle" + dimensionName,
                                    task -> {
                                        task.setDescription(
                                                "Assembles bundles for all "
                                                        + dimensionName
                                                        + " variants.");
                                        task.setGroup(BasePlugin.BUILD_GROUP);
                                        task.dependsOn(subBundleMap);
                                    }));
                }
            }

        } else if (variantTypeCount > 1) {
            // Case #2

            // loop on the variants and record their build type usage.
            // map from build type to the variant-specific assemble/bundle tasks
            ListMultimap<String, TaskProvider<? extends Task>> assembleMap =
                    ArrayListMultimap.create();
            ListMultimap<String, TaskProvider<? extends Task>> bundleMap =
                    ArrayListMultimap.create();

            for (VariantScope variantScope : variantScopes) {
                final VariantType variantType = variantScope.getType();
                if (!variantType.isTestComponent()) {
                    final MutableTaskContainer taskContainer = variantScope.getTaskContainer();
                    final GradleVariantConfiguration variantConfig =
                            variantScope.getVariantConfiguration();

                    assembleMap.put(
                            variantConfig.getBuildType().getName(),
                            taskContainer.getAssembleTask());

                    // fill the bundle map only if the variant supports bundles.
                    if (variantType.isBaseModule()) {
                        TaskProvider<? extends Task> bundleTask = taskContainer.getBundleTask();

                        bundleMap.put(variantConfig.getBuildType().getName(), bundleTask);
                    }
                }
            }

            // loop over the map of build-type to create tasks for each, setting a dependency
            // on the variant-specific task.
            // these keys should be the same for bundle and assemble
            Set<String> dimensionKeys = assembleMap.keySet();

            for (String dimensionKey : dimensionKeys) {
                final String dimensionName = StringHelper.capitalize(dimensionKey);

                // create the task and add it to the list
                subAssembleTasks.add(
                        taskFactory.register(
                                "assemble" + dimensionName,
                                task -> {
                                    task.setDescription(
                                            "Assembles main outputs for all "
                                                    + dimensionName
                                                    + " variants.");
                                    task.setGroup(BasePlugin.BUILD_GROUP);
                                    task.dependsOn(assembleMap.get(dimensionKey));
                                }));

                List<TaskProvider<? extends Task>> subBundleMap = bundleMap.get(dimensionKey);
                if (!subBundleMap.isEmpty()) {

                    // create the task and add it to the list
                    subBundleTasks.add(
                            taskFactory.register(
                                    "bundle" + dimensionName,
                                    task -> {
                                        task.setDescription(
                                                "Assembles bundles for all "
                                                        + dimensionName
                                                        + " variants.");
                                        task.setGroup(BasePlugin.BUILD_GROUP);
                                        task.dependsOn(subBundleMap);
                                    }));
                }
            }
        } else {
            // Case #3

            for (VariantScope variantScope : variantScopes) {
                final VariantType variantType = variantScope.getType();
                if (!variantType.isTestComponent()) {
                    final MutableTaskContainer taskContainer = variantScope.getTaskContainer();

                    subAssembleTasks.add(taskContainer.getAssembleTask());

                    if (variantType.isBaseModule()) {
                        subBundleTasks.add(taskContainer.getBundleTask());
                    }
                }
            }
        }

        // ---
        // ok now we can create the main 'assemble' and 'bundle' tasks and make them depend on the
        // sub-tasks.

        if (!subAssembleTasks.isEmpty()) {
            // "assemble" task is already created by the java base plugin.
            taskFactory.configure(
                    "assemble",
                    task -> {
                        task.setDescription("Assemble main outputs for all the variants.");
                        task.setGroup(BasePlugin.BUILD_GROUP);
                        task.dependsOn(subAssembleTasks);
                    });
        }

        if (!subBundleTasks.isEmpty()) {
            // root bundle task
            taskFactory.register(
                    "bundle",
                    task -> {
                        task.setDescription("Assemble bundles for all the variants.");
                        task.setGroup(BasePlugin.BUILD_GROUP);
                        task.dependsOn(subBundleTasks);
                    });
        }
    }

    public void createAssembleTask(@NonNull final BaseVariantData variantData) {
        final VariantScope scope = variantData.getScope();
        taskFactory.register(
                getAssembleTaskName(scope, "assemble"),
                null /*preConfigAction*/,
                task -> {
                    task.setDescription(
                            "Assembles main output for variant "
                                    + scope.getVariantConfiguration().getFullName());
                },
                taskProvider -> scope.getTaskContainer().setAssembleTask(taskProvider));
    }

    @NonNull
    private String getAssembleTaskName(VariantScope scope, @NonNull String prefix) {
        String taskName;
        if (variantFactory.getVariantConfigurationTypes().size() == 1) {
            taskName = scope.getTaskName(prefix);
        } else {
            taskName =
                    StringHelper.appendCapitalized(
                            prefix, scope.getVariantConfiguration().computeHybridVariantName());
        }
        return taskName;
    }

    public void createBundleTask(@NonNull final BaseVariantData variantData) {
        final VariantScope scope = variantData.getScope();
        taskFactory.register(
                getAssembleTaskName(scope, "bundle"),
                null,
                task -> {
                    task.setDescription(
                            "Assembles bundle for variant "
                                    + scope.getVariantConfiguration().getFullName());
                    task.dependsOn(
                            scope.getArtifacts()
                                    .getFinalArtifactFiles(InternalArtifactType.BUNDLE));
                },
                taskProvider -> scope.getTaskContainer().setBundleTask(taskProvider));
    }

    /** Returns created shrinker type, or null if none was created. */
    @Nullable
    protected CodeShrinker maybeCreateJavaCodeShrinkerTransform(
            @NonNull final VariantScope variantScope) {
        CodeShrinker codeShrinker = variantScope.getCodeShrinker();

        if (codeShrinker != null) {
            return doCreateJavaCodeShrinkerTransform(
                    variantScope,
                    // No mapping in non-test modules.
                    codeShrinker,
                    null);
        } else {
            return null;
        }
    }

    /**
     * Actually creates the minify transform, using the given mapping configuration. The mapping is
     * only used by test-only modules. Returns a type of the {@link CodeShrinker} shrinker that was
     * created, or {@code null} if none was created.
     */
    @Nullable
    protected final CodeShrinker doCreateJavaCodeShrinkerTransform(
            @NonNull final VariantScope variantScope,
            @NonNull CodeShrinker codeShrinker,
            @Nullable FileCollection mappingFileCollection) {
        Optional<TaskProvider<TransformTask>> transformTask;
        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {
            logger.warn(
                    "{} is disabled for variant {} because it is not compatible with Instant Run. "
                            + "See http://d.android.com/r/studio-ui/shrink-code-with-ir.html "
                            + "for details on how to enable a code shrinker that's compatible with "
                            + "Instant Run.",
                    codeShrinker.name(),
                    variantScope.getVariantConfiguration().getFullName());
            return null;
        }

        CodeShrinker createdShrinker = codeShrinker;
        switch (codeShrinker) {
            case PROGUARD:
                transformTask = createProguardTransform(variantScope, mappingFileCollection);
                break;
            case R8:
                if (variantScope.getVariantConfiguration().getType().isAar()
                        && !projectOptions.get(BooleanOption.ENABLE_R8_LIBRARIES)) {
                    transformTask = createProguardTransform(variantScope, mappingFileCollection);
                    createdShrinker = CodeShrinker.PROGUARD;
                } else {
                    transformTask =
                            createR8Transform(
                                    variantScope,
                                    mappingFileCollection,
                                    (transform, taskName) -> {
                                        if (variantScope.getNeedsMainDexListForBundle()) {
                                            File mainDexListFile =
                                                    variantScope
                                                            .getArtifacts()
                                                            .appendArtifact(
                                                                    InternalArtifactType
                                                                            .MAIN_DEX_LIST_FOR_BUNDLE,
                                                                    taskName,
                                                                    "mainDexList.txt");
                                            ((R8Transform) transform)
                                                    .setMainDexListOutput(mainDexListFile);
                                        }
                                    });
                }
                break;
            default:
                throw new AssertionError("Unknown value " + codeShrinker);
        }
        if (variantScope.getPostprocessingFeatures() != null && transformTask.isPresent()) {
            TaskProvider<CheckProguardFiles> checkFilesTask =
                    taskFactory.register(new CheckProguardFiles.CreationAction(variantScope));

            TaskFactoryUtils.dependsOn(transformTask.get(), checkFilesTask);
        }

        return createdShrinker;
    }

    @NonNull
    private Optional<TaskProvider<TransformTask>> createProguardTransform(
            @NonNull VariantScope variantScope, @Nullable FileCollection mappingFileCollection) {
        final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

        ProGuardTransform transform = new ProGuardTransform(variantScope);

        FileCollection inputProguardMapping;
        if (testedVariantData != null
                && testedVariantData.getScope().getArtifacts().hasArtifact(APK_MAPPING)) {
            inputProguardMapping =
                    testedVariantData
                            .getScope()
                            .getArtifacts()
                            .getFinalArtifactFiles(APK_MAPPING)
                            .get();
        } else {
            inputProguardMapping = mappingFileCollection;
        }
        transform.applyTestedMapping(inputProguardMapping);

        return applyProguardRules(
                variantScope,
                inputProguardMapping,
                transform.getMappingFile(),
                testedVariantData,
                transform,
                null);
    }

    private interface ProGuardTransformCallback {
        void execute(@NonNull ProguardConfigurable transform, @NonNull String taskName);
    }

    @NonNull
    private Optional<TaskProvider<TransformTask>> applyProguardRules(
            @NonNull VariantScope variantScope,
            @Nullable FileCollection inputProguardMapping,
            @Nullable File outputProguardMapping,
            BaseVariantData testedVariantData,
            @NonNull ProguardConfigurable transform,
            @Nullable ProGuardTransformCallback callback) {

        if (testedVariantData != null) {
            final VariantScope testedScope = testedVariantData.getScope();
            // This is an androidTest variant inside an app/library.
            applyProguardDefaultsForTest(transform);

            // All -dontwarn rules for test dependencies should go in here:
            final ConfigurableFileCollection configurationFiles =
                    project.files(
                            (Callable<Collection<File>>) testedScope::getTestProguardFiles,
                            variantScope.getArtifactFileCollection(
                                    RUNTIME_CLASSPATH, ALL, CONSUMER_PROGUARD_RULES));
            maybeAddFeatureProguardRules(variantScope, configurationFiles);
            transform.setConfigurationFiles(configurationFiles);
        } else if (variantScope.getType().isForTesting()
                && !variantScope.getType().isTestComponent()) {
            // This is a test-only module and the app being tested was obfuscated with ProGuard.
            applyProguardDefaultsForTest(transform);

            // All -dontwarn rules for test dependencies should go in here:
            final ConfigurableFileCollection configurationFiles =
                    project.files(
                            (Callable<Collection<File>>) variantScope::getTestProguardFiles,
                            variantScope.getArtifactFileCollection(
                                    RUNTIME_CLASSPATH, ALL, CONSUMER_PROGUARD_RULES));
            maybeAddFeatureProguardRules(variantScope, configurationFiles);
            transform.setConfigurationFiles(configurationFiles);
        } else {
            // This is a "normal" variant in an app/library.
            applyProguardConfigForNonTest(transform, variantScope);
        }

        return variantScope
                .getTransformManager()
                .addTransform(
                        taskFactory,
                        variantScope,
                        transform,
                        taskName -> {
                            variantScope
                                    .getArtifacts()
                                    .appendArtifact(
                                            InternalArtifactType.APK_MAPPING,
                                            ImmutableList.of(checkNotNull(outputProguardMapping)),
                                            taskName);

                            if (callback != null) {
                                callback.execute(transform, taskName);
                            }
                        },
                        t -> {
                            if (inputProguardMapping != null) {
                                t.dependsOn(inputProguardMapping);
                            }

                            if (testedVariantData != null) {
                                // We need the mapping file for the app code to exist by the time we run.
                                // FIXME consume the BA!
                                t.dependsOn(testedVariantData.getTaskContainer().getAssembleTask());
                            }
                        },
                        null);
    }

    private static void applyProguardDefaultsForTest(ProguardConfigurable transform) {
        // Don't remove any code in tested app.
        // We can't call dontobfuscate for Proguard, since that makes it ignore the mapping file.
        // R8 does not have that issue, so we disable obfuscation when running R8.
        boolean obfuscate = transform instanceof ProGuardTransform;
        transform.setActions(new PostprocessingFeatures(false, obfuscate, false));

        transform.keep("class * {*;}");
        transform.keep("interface * {*;}");
        transform.keep("enum * {*;}");
        transform.keepattributes();
    }

    private void applyProguardConfigForNonTest(ProguardConfigurable transform, VariantScope scope) {
        GradleVariantConfiguration variantConfig = scope.getVariantConfiguration();

        PostprocessingFeatures postprocessingFeatures = scope.getPostprocessingFeatures();
        if (postprocessingFeatures != null) {
            transform.setActions(postprocessingFeatures);
        }

        Callable<Collection<File>> proguardConfigFiles = scope::getProguardFiles;

        final InternalArtifactType aaptProguardFileType =
                scope.consumesFeatureJars()
                        ? InternalArtifactType.MERGED_AAPT_PROGUARD_FILE
                        : InternalArtifactType.AAPT_PROGUARD_FILE;

        final ConfigurableFileCollection configurationFiles =
                project.files(
                        proguardConfigFiles,
                        scope.getArtifacts().getFinalArtifactFiles(aaptProguardFileType),
                        scope.getArtifactFileCollection(
                                RUNTIME_CLASSPATH, ALL, CONSUMER_PROGUARD_RULES));

        if (scope.getType().isHybrid() && scope.getType().isBaseModule()) {
            Callable<Collection<File>> consumerProguardFiles = scope::getConsumerProguardFiles;
            configurationFiles.from(consumerProguardFiles);
        }

        maybeAddFeatureProguardRules(scope, configurationFiles);
        transform.setConfigurationFiles(configurationFiles);

        if (scope.getVariantData().getType().isAar()) {
            transform.keep("class **.R");
            transform.keep("class **.R$*");
        }

        if (variantConfig.isTestCoverageEnabled()) {
            // when collecting coverage, don't remove the JaCoCo runtime
            transform.keep("class com.vladium.** {*;}");
            transform.keep("class org.jacoco.** {*;}");
            transform.keep("interface org.jacoco.** {*;}");
            transform.dontwarn("org.jacoco.**");
        }
    }

    private void maybeAddFeatureProguardRules(
            @NonNull VariantScope variantScope,
            @NonNull ConfigurableFileCollection configurationFiles) {
        if (variantScope.consumesFeatureJars()) {
            configurationFiles.from(
                    variantScope.getArtifactFileCollection(
                            METADATA_VALUES, MODULE, CONSUMER_PROGUARD_RULES));
        }
    }

    @NonNull
    private Optional<TaskProvider<TransformTask>> createR8Transform(
            @NonNull VariantScope variantScope,
            @Nullable FileCollection mappingFileCollection,
            @Nullable ProGuardTransformCallback callback) {
        final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

        File multiDexKeepProguard =
                variantScope.getVariantConfiguration().getMultiDexKeepProguard();
        FileCollection userMainDexListProguardRules;
        if (multiDexKeepProguard != null) {
            userMainDexListProguardRules = project.files(multiDexKeepProguard);
        } else {
            userMainDexListProguardRules = project.files();
        }

        File multiDexKeepFile = variantScope.getVariantConfiguration().getMultiDexKeepFile();
        FileCollection userMainDexListFiles;
        if (multiDexKeepFile != null) {
            userMainDexListFiles = project.files(multiDexKeepFile);
        } else {
            userMainDexListFiles = project.files();
        }

        FileCollection inputProguardMapping;
        if (testedVariantData != null
                && testedVariantData.getScope().getArtifacts().hasArtifact(APK_MAPPING)) {
            inputProguardMapping =
                    testedVariantData
                            .getScope()
                            .getArtifacts()
                            .getFinalArtifactFiles(APK_MAPPING)
                            .get();
        } else {
            inputProguardMapping = MoreObjects.firstNonNull(mappingFileCollection, project.files());
        }

        R8Transform transform =
                new R8Transform(
                        variantScope,
                        userMainDexListFiles,
                        userMainDexListProguardRules,
                        inputProguardMapping,
                        variantScope.getOutputProguardMappingFile());

        return applyProguardRules(
                variantScope,
                inputProguardMapping,
                variantScope.getOutputProguardMappingFile(),
                testedVariantData,
                transform,
                callback);
    }

    private void maybeCreateDexSplitterTransform(@NonNull VariantScope variantScope) {
        if (!variantScope.consumesFeatureJars()) {
            return;
        }

        File dexSplitterOutput =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "dex-splitter",
                        variantScope.getVariantConfiguration().getDirName());
        FileCollection featureJars =
                variantScope.getArtifactFileCollection(METADATA_VALUES, MODULE, METADATA_CLASSES);
        BuildableArtifact baseJars =
                variantScope
                        .getArtifacts()
                        .getFinalArtifactFiles(
                                InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES);
        BuildableArtifact mappingFileSrc =
                variantScope.getArtifacts().hasArtifact(InternalArtifactType.APK_MAPPING)
                        ? variantScope
                                .getArtifacts()
                                .getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)
                        : null;

        DexSplitterTransform transform =
                new DexSplitterTransform(dexSplitterOutput, featureJars, baseJars, mappingFileSrc);

        Optional<TaskProvider<TransformTask>> transformTask =
                variantScope
                        .getTransformManager()
                        .addTransform(
                                taskFactory,
                                variantScope,
                                transform,
                                taskName ->
                                        variantScope
                                                .getArtifacts()
                                                .appendArtifact(
                                                        InternalArtifactType.FEATURE_DEX,
                                                        ImmutableList.of(dexSplitterOutput),
                                                        taskName),
                                null,
                                null);

        if (transformTask.isPresent()) {
            publishFeatureDex(variantScope);
        } else {
            globalScope
                    .getAndroidBuilder()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            new EvalIssueException(
                                    "Internal error, could not add the DexSplitterTransform"));
        }
    }

    /**
     * We have a separate method for publishing the classes.dex files back to the features (instead
     * of using the typical PublishingSpecs pipeline) because multiple artifacts are published per
     * BuildableArtifact in this case.
     *
     * <p>This method is similar to VariantScopeImpl.publishIntermediateArtifact, and some of the
     * code was pulled from there. Once there's support for publishing multiple artifacts per
     * BuildableArtifact in the PublishingSpecs pipeline, we can get rid of this method.
     */
    private void publishFeatureDex(@NonNull VariantScope variantScope) {
        // first calculate the list of module paths
        final Collection<String> modulePaths;
        final AndroidConfig extension = globalScope.getExtension();
        if (extension instanceof BaseAppModuleExtension) {
            modulePaths = ((BaseAppModuleExtension) extension).getDynamicFeatures();
        } else if (extension instanceof FeatureExtension) {
            modulePaths = FeatureModelBuilder.getDynamicFeatures(globalScope);
        } else {
            return;
        }

        Configuration configuration =
                variantScope.getVariantData().getVariantDependency().getElements(RUNTIME_ELEMENTS);
        Preconditions.checkNotNull(
                configuration,
                "Publishing to Runtime Element with no Runtime Elements configuration object. "
                        + "VariantType: "
                        + variantScope.getType());
        BuildableArtifact artifact =
                variantScope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.FEATURE_DEX);
        for (String modulePath : modulePaths) {
            Provider<File> file =
                    project.provider(
                            () ->
                                    new File(
                                            Iterables.getOnlyElement(artifact.getFiles()),
                                            getFeatureFileName(modulePath, null)));
            Map<Attribute<String>, String> attributeMap =
                    ImmutableMap.of(MODULE_PATH, project.absoluteProjectPath(modulePath));
            publishArtifactToConfiguration(
                    configuration,
                    file,
                    artifact,
                    AndroidArtifacts.ArtifactType.FEATURE_DEX,
                    attributeMap);
        }
    }

    private void createMergeClassesTransform(@NonNull VariantScope variantScope) {

        File outputJar = variantScope.getMergedClassesJarFile();

        MergeClassesTransform transform = new MergeClassesTransform(outputJar);

        Optional<TaskProvider<TransformTask>> transformTask =
                variantScope
                        .getTransformManager()
                        .addTransform(
                                taskFactory,
                                variantScope,
                                transform,
                                taskName -> {
                                    variantScope
                                            .getArtifacts()
                                            .appendArtifact(
                                                    InternalArtifactType
                                                            .MODULE_AND_RUNTIME_DEPS_CLASSES,
                                                    ImmutableList.of(outputJar),
                                                    taskName);
                                },
                                null,
                                null);

        if (!transformTask.isPresent()) {
            globalScope
                    .getAndroidBuilder()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            new EvalIssueException(
                                    "Internal error, could not add the MergeClassesTransform"));
        }
    }

    /**
     * Method to reliably generate matching feature file names when dex splitter is used.
     *
     * @param modulePath the gradle module path for the feature
     * @param fileExtension the desired file extension (e.g., ".jar"), or null if no file extension
     *     (e.g., for a folder)
     * @return name of file
     */
    public static String getFeatureFileName(
            @NonNull String modulePath, @Nullable String fileExtension) {
        final String featureName = FeatureSplitUtils.getFeatureName(modulePath);
        final String sanitizedFeatureName = ":".equals(featureName) ? "" : featureName;
        // Prepend "feature-" to fileName in case a non-base module has module path ":base".
        return "feature-" + sanitizedFeatureName + nullToEmpty(fileExtension);
    }

    /**
     * Checks if {@link ShrinkResourcesTransform} should be added to the build pipeline and either
     * adds it or registers a {@link SyncIssue} with the reason why it was skipped.
     */
    protected void maybeCreateResourcesShrinkerTransform(@NonNull VariantScope scope) {
        if (!scope.useResourceShrinker()) {
            return;
        }

        // if resources are shrink, insert a no-op transform per variant output
        // to transform the res package into a stripped res package
        File shrinkerOutput =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "res_stripped",
                        scope.getVariantConfiguration().getDirName());

        ShrinkResourcesTransform shrinkResTransform =
                new ShrinkResourcesTransform(
                        scope.getVariantData(),
                        scope.getArtifacts()
                                .getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES),
                        shrinkerOutput,
                        logger);

        Optional<TaskProvider<TransformTask>> shrinkTask =
                scope.getTransformManager()
                        .addTransform(
                                taskFactory,
                                scope,
                                shrinkResTransform,
                                taskName ->
                                        scope.getArtifacts()
                                                .appendArtifact(
                                                        InternalArtifactType.SHRUNK_PROCESSED_RES,
                                                        ImmutableList.of(shrinkerOutput),
                                                        taskName),
                                null,
                                null);

        if (!shrinkTask.isPresent()) {
            globalScope
                    .getAndroidBuilder()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            new EvalIssueException(
                                    "Internal error, could not add the ShrinkResourcesTransform"));
        }

        // And for the bundle
        taskFactory.register(new ShrinkBundleResourcesTask.CreationAction(scope));
    }

    public void createReportTasks(final List<VariantScope> variantScopes) {
        taskFactory.register(
                "androidDependencies",
                DependencyReportTask.class,
                task -> {
                    task.setDescription("Displays the Android dependencies of the project.");
                    task.setVariants(variantScopes);
                    task.setGroup(ANDROID_GROUP);
                });


        List<VariantScope> signingReportScopes =
                variantScopes
                        .stream()
                        .filter(
                                variantScope ->
                                        variantScope.getType().isForTesting()
                                                || variantScope.getType().isBaseModule())
                        .collect(Collectors.toList());
        if (!signingReportScopes.isEmpty()) {
            taskFactory.register(
                    "signingReport",
                    SigningReportTask.class,
                    task -> {
                        task.setDescription(
                                "Displays the signing info for the base and test modules");
                        task.setVariants(signingReportScopes);
                        task.setGroup(ANDROID_GROUP);
                    });
        }
    }

    public void createAnchorTasks(@NonNull VariantScope scope) {
        createVariantPreBuildTask(scope);

        // also create sourceGenTask
        final BaseVariantData variantData = scope.getVariantData();
        scope.getTaskContainer()
                .setSourceGenTask(
                        taskFactory.register(
                                scope.getTaskName("generate", "Sources"),
                                task -> {
                                    task.dependsOn(PrepareLintJar.NAME);
                                    task.dependsOn(variantData.getExtraGeneratedResFolders());
                                }));
        // and resGenTask
        scope.getTaskContainer()
                .setResourceGenTask(
                        taskFactory.register(scope.getTaskName("generate", "Resources")));

        scope.getTaskContainer()
                .setAssetGenTask(taskFactory.register(scope.getTaskName("generate", "Assets")));

        if (!variantData.getType().isForTesting()
                && variantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {
            scope.getTaskContainer()
                    .setCoverageReportTask(
                            taskFactory.register(
                                    scope.getTaskName("create", "CoverageReport"),
                                    task -> {
                                        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                        task.setDescription(
                                                String.format(
                                                        "Creates test coverage reports for the %s variant.",
                                                        variantData.getName()));
                                    }));
        }

        // and compile task
        createCompileAnchorTask(scope);
    }

    protected void createVariantPreBuildTask(@NonNull VariantScope scope) {
        // default pre-built task.
        createDefaultPreBuildTask(scope);
    }

    protected void createDefaultPreBuildTask(@NonNull VariantScope scope) {
        taskFactory.register(new PreBuildCreationAction(scope));
    }

    public abstract static class AbstractPreBuildCreationAction<T extends Task>
            extends TaskCreationAction<T> {

        protected final VariantScope variantScope;

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("pre", "Build");
        }

        public AbstractPreBuildCreationAction(VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends T> taskProvider) {
            super.handleProvider(taskProvider);
            variantScope.getTaskContainer().setPreBuildTask(taskProvider);
        }

        @Override
        public void configure(@NonNull T task) {
            task.dependsOn(MAIN_PREBUILD);

            if (variantScope.getCodeShrinker() != null) {
                task.dependsOn(EXTRACT_PROGUARD_FILES);
            }
        }
    }

    private static class PreBuildCreationAction extends AbstractPreBuildCreationAction<Task> {
        public PreBuildCreationAction(VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public Class<Task> getType() {
            return Task.class;
        }
    }

    private void createCompileAnchorTask(@NonNull final VariantScope scope) {
        scope.getTaskContainer()
                .setCompileTask(
                        taskFactory.register(
                                scope.getTaskName("compile", "Sources"),
                                task -> task.setGroup(BUILD_GROUP)));

        // FIXME is that really needed?
        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getAssembleTask(),
                scope.getTaskContainer().getCompileTask());
    }

    public void createCheckManifestTask(@NonNull VariantScope scope) {
        taskFactory.register(getCheckManifestConfig(scope));
    }

    protected CheckManifest.CreationAction getCheckManifestConfig(@NonNull VariantScope scope) {
        return new CheckManifest.CreationAction(scope, false);
    }

    @NonNull
    protected Logger getLogger() {
        return logger;
    }

    public void addDataBindingDependenciesIfNecessary(
            DataBindingOptions options, List<VariantScope> variantScopes) {
        if (!options.isEnabled()) {
            return;
        }
        boolean useAndroidX = globalScope.getProjectOptions().get(BooleanOption.USE_ANDROID_X);
        String version = MoreObjects.firstNonNull(options.getVersion(),
                dataBindingBuilder.getCompilerVersion());
        String baseLibArtifact =
                useAndroidX
                        ? SdkConstants.ANDROIDX_DATA_BINDING_BASELIB_ARTIFACT
                        : SdkConstants.DATA_BINDING_BASELIB_ARTIFACT;
        project.getDependencies()
                .add(
                        "api",
                        baseLibArtifact + ":" + dataBindingBuilder.getBaseLibraryVersion(version));
        project.getDependencies()
                .add(
                        "annotationProcessor",
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version);
        // TODO load config name from source sets
        if (options.isEnabledForTests()
                || this instanceof LibraryTaskManager
                || this instanceof MultiTypeTaskManager) {
            project.getDependencies()
                    .add(
                            "androidTestAnnotationProcessor",
                            SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT
                                    + ":"
                                    + version);
        }
        if (options.getAddDefaultAdapters()) {
            String libArtifact =
                    useAndroidX
                            ? SdkConstants.ANDROIDX_DATA_BINDING_LIB_ARTIFACT
                            : SdkConstants.DATA_BINDING_LIB_ARTIFACT;
            String adaptersArtifact =
                    useAndroidX
                            ? SdkConstants.ANDROIDX_DATA_BINDING_ADAPTER_LIB_ARTIFACT
                            : SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT;
            project.getDependencies()
                    .add("api", libArtifact + ":" + dataBindingBuilder.getLibraryVersion(version));
            project.getDependencies()
                    .add(
                            "api",
                            adaptersArtifact
                                    + ":"
                                    + dataBindingBuilder.getBaseAdaptersVersion(version));
        }
        project.getPluginManager()
                .withPlugin(
                        "org.jetbrains.kotlin.kapt",
                        appliedPlugin -> {
                            configureKotlinKaptTasksForDataBinding(project, variantScopes, version);
                        });
    }

    private void configureKotlinKaptTasksForDataBinding(
            Project project, List<VariantScope> variantScopes, String version) {
        DependencySet kaptDeps = project.getConfigurations().getByName("kapt").getAllDependencies();
        kaptDeps.forEach(
                (Dependency dependency) -> {
                    // if it is a data binding compiler dependency w/ a different version, report error
                    if (Objects.equals(
                                    dependency.getGroup() + ":" + dependency.getName(),
                                    SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT)
                            && !Objects.equals(dependency.getVersion(), version)) {
                        String depString =
                                dependency.getGroup()
                                        + ":"
                                        + dependency.getName()
                                        + ":"
                                        + dependency.getVersion();
                        globalScope
                                .getAndroidBuilder()
                                .getIssueReporter()
                                .reportError(
                                        Type.GENERIC,
                                        new EvalIssueException(
                                                "Data Binding annotation processor version needs to match the"
                                                        + " Android Gradle Plugin version. You can remove the kapt"
                                                        + " dependency "
                                                        + depString
                                                        + " and Android Gradle Plugin will inject"
                                                        + " the right version."));
                    }
                });
        project.getDependencies()
                .add(
                        "kapt",
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version);
        Class<? extends Task> kaptTaskClass = null;
        try {
            //noinspection unchecked
            kaptTaskClass =
                    (Class<? extends Task>)
                            Class.forName("org.jetbrains.kotlin.gradle.internal.KaptTask");
        } catch (ClassNotFoundException e) {
            logger.error(
                    "Kotlin plugin is applied to the project "
                            + project.getPath()
                            + " but we cannot find the KaptTask. Make sure you apply the"
                            + " kotlin-kapt plugin because it is necessary to use kotlin"
                            + " with data binding.");
        }
        if (kaptTaskClass == null) {
            return;
        }
        // create a map from kapt task name to variant scope
        Map<String, VariantScope> kaptTaskLookup =
                variantScopes
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        variantScope ->
                                                variantScope
                                                        .getVariantData()
                                                        .getTaskName("kapt", "Kotlin"),
                                        variantScope -> variantScope));
        project.getTasks()
                .withType(
                        kaptTaskClass,
                        (Action<Task>)
                                kaptTask -> {
                                    // find matching scope.
                                    VariantScope matchingScope =
                                            kaptTaskLookup.get(kaptTask.getName());
                                    if (matchingScope != null) {
                                        configureKaptTaskInScope(matchingScope, kaptTask);
                                    }
                                });
    }

    private static void configureKaptTaskInScope(
            @NonNull VariantScope scope, @NonNull Task kaptTask) {
        // The data binding artifact is created through annotation processing, which is invoked
        // by the Kapt task (when the Kapt plugin is used). Therefore, we register Kapt as the
        // generating task. (This will overwrite the registration of JavaCompile as the generating
        // task that took place earlier before this method is called).
        scope.getArtifacts()
                .createBuildableArtifact(
                        InternalArtifactType.DATA_BINDING_ARTIFACT,
                        BuildArtifactsHolder.OperationType.APPEND,
                        ImmutableList.of(scope.getBundleArtifactFolderForDataBinding()),
                        kaptTask.getName());
    }

    protected void configureTestData(AbstractTestDataImpl testData) {
        testData.setAnimationsDisabled(extension.getTestOptions().getAnimationsDisabled());
        testData.setExtraInstrumentationTestRunnerArgs(
                projectOptions.getExtraInstrumentationTestRunnerArgs());
    }
}
