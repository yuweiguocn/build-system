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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.VariantFilter;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.SourceSetSourceProviderWrapper;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.ExternalNativeBuild;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.LibraryRequest;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.SourceProvider;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.repository.Revision;
import com.android.resources.Density;
import com.android.sdklib.BuildToolInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;

/**
 * Base extension for all Android plugins.
 *
 * <p>You don't use this plugin directly. Instead, use one of the following:
 *
 * <ul>
 *   <li>{@link AppExtension}: outputs the {@code com.android.application} plugin you use to create
 *       an Android app module.
 *   <li>{@link LibraryExtension}: outputs the {@code com.android.library} plugin you use to <a
 *       href="https://developer.android.com/studio/projects/android-library.html">create an Android
 *       library module</a>.
 *   <li>{@link TestExtension}: outputs the {@code com.android.test} plugin you use to create an
 *       Android test module.
 *   <li>{@link FeatureExtension}: outputs the {@code com.android.feature} plugin you use to create
 *       a feature module for your <a href="https://d.android.com/instant-apps">Android Instant
 *       Apps</a>.
 * </ul>
 *
 * <p>The following applies the Android plugin to an app's module-level <code>build.gradle</code>
 * file:
 *
 * <pre>
 * // Applies the application plugin and makes the 'android' block available to specify
 * // Android-specific build options.
 * apply plugin: 'com.android.application'
 * </pre>
 *
 * <p>To learn more about creating and organizing Android projects, read <a
 * href="https://developer.android.com/studio/projects/index.html">Projects Overview</a>.
 */
// All the public methods are meant to be exposed in the DSL. We can't use lambdas in this class
// (yet), because the DSL reference generator doesn't understand them.
@SuppressWarnings({"UnnecessaryInheritDoc", "WeakerAccess", "unused", "Convert2Lambda"})
public abstract class BaseExtension implements AndroidConfig {

    /** Secondary dependencies for the custom transform. */
    private final List<List<Object>> transformDependencies = Lists.newArrayList();

    protected final GlobalScope globalScope;

    private final SdkHandler sdkHandler;

    private final DefaultConfig defaultConfig;

    private final AaptOptions aaptOptions;

    private final LintOptions lintOptions;

    private final ExternalNativeBuild externalNativeBuild;

    private final DexOptions dexOptions;

    private final TestOptions testOptions;

    private final CompileOptions compileOptions;

    private final PackagingOptions packagingOptions;

    private final JacocoOptions jacoco;

    private final Splits splits;

    private final AdbOptions adbOptions;

    private final NamedDomainObjectContainer<ProductFlavor> productFlavors;

    private final NamedDomainObjectContainer<BuildType> buildTypes;

    private final NamedDomainObjectContainer<SigningConfig> signingConfigs;

    private final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs;

    private final List<DeviceProvider> deviceProviderList = Lists.newArrayList();

    private final List<TestServer> testServerList = Lists.newArrayList();

    private final List<Transform> transforms = Lists.newArrayList();

    private final DataBindingOptions dataBinding;

    private final SourceSetManager sourceSetManager;

    private String target;

    @NonNull private Revision buildToolsRevision;

    private List<LibraryRequest> libraryRequests = Lists.newArrayList();

    private List<String> flavorDimensionList;

    private String resourcePrefix;

    private ExtraModelInfo extraModelInfo;

    private String defaultPublishConfig = "release";

    private Action<VariantFilter> variantFilter;

    protected Logger logger;

    private boolean isWritable = true;

    protected Project project;

    private final ProjectOptions projectOptions;

    private final boolean isBaseModule;

    @Nullable private String ndkVersion;

    BaseExtension(
            @NonNull final Project project,
            @NonNull final ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo,
            boolean isBaseModule) {
        this.globalScope = globalScope;
        this.sdkHandler = sdkHandler;
        this.buildTypes = buildTypes;
        //noinspection unchecked
        this.productFlavors = productFlavors;
        this.signingConfigs = signingConfigs;
        this.extraModelInfo = extraModelInfo;
        this.buildOutputs = buildOutputs;
        this.project = project;
        this.projectOptions = projectOptions;
        this.sourceSetManager = sourceSetManager;
        this.isBaseModule = isBaseModule;

        logger = Logging.getLogger(this.getClass());

        ObjectFactory objectFactory = project.getObjects();

        defaultConfig =
                objectFactory.newInstance(
                        DefaultConfig.class,
                        BuilderConstants.MAIN,
                        project,
                        objectFactory,
                        extraModelInfo.getDeprecationReporter(),
                        project.getLogger());

        aaptOptions =
                objectFactory.newInstance(
                        AaptOptions.class,
                        projectOptions.get(BooleanOption.ENABLE_RESOURCE_NAMESPACING_DEFAULT));
        dexOptions =
                objectFactory.newInstance(
                        DexOptions.class, extraModelInfo.getDeprecationReporter());
        lintOptions = objectFactory.newInstance(LintOptions.class);
        externalNativeBuild =
                objectFactory.newInstance(ExternalNativeBuild.class, objectFactory, project);
        testOptions = objectFactory.newInstance(TestOptions.class, objectFactory);
        compileOptions = objectFactory.newInstance(CompileOptions.class);
        packagingOptions = objectFactory.newInstance(PackagingOptions.class);
        jacoco = objectFactory.newInstance(JacocoOptions.class);
        adbOptions = objectFactory.newInstance(AdbOptions.class);
        splits = objectFactory.newInstance(Splits.class, objectFactory);
        dataBinding = objectFactory.newInstance(DataBindingOptions.class);

        // Create the "special" configuration for test buddy APKs. It will be resolved by the test
        // running task, so that we can install all the found APKs before running tests.
        createAndroidTestUtilConfiguration();

        sourceSetManager.setUpSourceSet(defaultConfig.getName());
        buildToolsRevision = AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION;
        setDefaultConfigValues();
    }

    private void setDefaultConfigValues() {
        Set<Density> densities = Density.getRecommendedValuesForDevice();
        Set<String> strings = Sets.newHashSetWithExpectedSize(densities.size());
        for (Density density : densities) {
            strings.add(density.getResourceValue());
        }
        defaultConfig.getVectorDrawables().setGeneratedDensities(strings);
        defaultConfig.getVectorDrawables().setUseSupportLibrary(false);
    }

    /**
     * Disallow further modification on the extension.
     */
    public void disableWrite() {
        isWritable = false;
    }

    protected void checkWritability() {
        if (!isWritable) {
            throw new GradleException(
                    "Android tasks have already been created.\n" +
                            "This happens when calling android.applicationVariants,\n" +
                            "android.libraryVariants or android.testVariants.\n" +
                            "Once these methods are called, it is not possible to\n" +
                            "continue configuring the model.");
        }
    }
    private void createAndroidTestUtilConfiguration() {
        String name = SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION;
        logger.info("Creating configuration {}", name);
        Configuration configuration = project.getConfigurations().maybeCreate(name);
        configuration.setVisible(false);
        configuration.setDescription("Additional APKs used during instrumentation testing.");
        configuration.setCanBeConsumed(false);
        configuration.setCanBeResolved(true);
    }

    /** @see #getCompileSdkVersion() */
    public void compileSdkVersion(String version) {
        checkWritability();
        this.target = version;
    }

    /** @see #getCompileSdkVersion() */
    public void compileSdkVersion(int apiLevel) {
        compileSdkVersion("android-" + apiLevel);
    }

    public void setCompileSdkVersion(int apiLevel) {
        compileSdkVersion(apiLevel);
    }

    public void setCompileSdkVersion(String target) {
        compileSdkVersion(target);
    }

    /**
     * Includes the specified library to the classpath.
     *
     * <p>You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * <pre>
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * </pre>
     *
     * <p>To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories, <a
     * href="https://developer.android.com/studio/build/dependencies.html">add the libraries as
     * dependencies</a> in the <code>dependencies</code> block. Note that Android plugin 3.0.0 and
     * later introduce <a
     * href="https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations">new
     * dependency configurations</a>. To learn more about Gradle dependencies, read <a
     * href="https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html">Dependency
     * Management Basics</a>.
     *
     * @param name the name of the library.
     */
    public void useLibrary(String name) {
        useLibrary(name, true);
    }

    /**
     * /** Includes the specified library to the classpath.
     *
     * <p>You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * <pre>
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * </pre>
     *
     * <p>To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories, <a
     * href="https://developer.android.com/studio/build/dependencies.html">add the libraries as
     * dependencies</a> in the <code>dependencies</code> block. Note that Android plugin 3.0.0 and
     * later introduce <a
     * href="https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations">new
     * dependency configurations</a>. To learn more about Gradle dependencies, read <a
     * href="https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html">Dependency
     * Management Basics</a>.
     *
     * @param name the name of the library.
     * @param required if using the library requires a manifest entry, the entry will indicate that
     *     the library is not required.
     */
    public void useLibrary(String name, boolean required) {
        libraryRequests.add(new LibraryRequest(name, required));
    }

    public void buildToolsVersion(String version) {
        checkWritability();
        //The underlying Revision class has the maven artifact semantic,
        // so 20 is not the same as 20.0. For the build tools revision this
        // is not the desired behavior, so normalize e.g. to 20.0.0.
        buildToolsRevision = Revision.parseRevision(version, Revision.Precision.MICRO);
    }

    /** {@inheritDoc} */
    @Override
    public String getNdkVersion() {
        return ndkVersion;
    }

    public void setNdkVersion(@Nullable String version) {
        ndkVersion = version;
    }

    /** {@inheritDoc} */
    @Override
    public String getBuildToolsVersion() {
        return buildToolsRevision.toString();
    }

    public void setBuildToolsVersion(String version) {
        buildToolsVersion(version);
    }

    /**
     * Encapsulates all build type configurations for this project.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * BuildType}.
     */
    public void buildTypes(Action<? super NamedDomainObjectContainer<BuildType>> action) {
        checkWritability();
        action.execute(buildTypes);
    }

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * ProductFlavor}
     */
    public void productFlavors(Action<? super NamedDomainObjectContainer<ProductFlavor>> action) {
        checkWritability();
        action.execute(productFlavors);
    }

    /**
     * Encapsulates signing configurations that you can apply to {@link
     * com.android.build.gradle.internal.dsl.BuildType} and {@link ProductFlavor} configurations.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * SigningConfig}
     */
    public void signingConfigs(Action<? super NamedDomainObjectContainer<SigningConfig>> action) {
        checkWritability();
        action.execute(signingConfigs);
    }

    /**
     * Specifies the names of product flavor dimensions for this project.
     *
     * <p>When configuring product flavors with Android plugin 3.0.0 and higher, you must specify at
     * least one flavor dimension, using the <a
     * href="com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[])">
     * <code>flavorDimensions</code></a> property, and then assign each flavor to a dimension.
     * Otherwise, you will get the following build error:
     *
     * <pre>
     * Error:All flavors must now belong to a named flavor dimension.
     * The flavor 'flavor_name' is not assigned to a flavor dimension.
     * </pre>
     *
     * <p>By default, when you specify only one dimension, all flavors you configure automatically
     * belong to that dimension. If you specify more than one dimension, you need to manually assign
     * each flavor to a dimension, as shown in the sample below.
     *
     * <p>Flavor dimensions allow you to create groups of product flavors that you can combine with
     * flavors from other flavor dimensions. For example, you can have one dimension that includes a
     * 'free' and 'paid' version of your app, and another dimension for flavors that support
     * different API levels, such as 'minApi21' and 'minApi24'. The Android plugin can then combine
     * flavors from these dimensions—including their settings, code, and resources—to create
     * variants such as 'debugFreeMinApi21' and 'releasePaidMinApi24', and so on. The sample below
     * shows you how to specify flavor dimensions and add product flavors to them.
     *
     * <pre>
     * android {
     *     ...
     *     // Specifies the flavor dimensions you want to use. The order in which you
     *     // list each dimension determines its priority, from highest to lowest,
     *     // when Gradle merges variant sources and configurations. You must assign
     *     // each product flavor you configure to one of the flavor dimensions.
     *     flavorDimensions 'api', 'version'
     *
     *     productFlavors {
     *       demo {
     *         // Assigns this product flavor to the 'version' flavor dimension.
     *         dimension 'version'
     *         ...
     *     }
     *
     *       full {
     *         dimension 'version'
     *         ...
     *       }
     *
     *       minApi24 {
     *         // Assigns this flavor to the 'api' dimension.
     *         dimension 'api'
     *         minSdkVersion '24'
     *         versionNameSuffix "-minApi24"
     *         ...
     *       }
     *
     *       minApi21 {
     *         dimension "api"
     *         minSdkVersion '21'
     *         versionNameSuffix "-minApi21"
     *         ...
     *       }
     *    }
     * }
     * </pre>
     *
     * <p>To learn more, read <a
     * href="https://developer.android.com/studio/build/build-variants.html#flavor-dimensions">
     * Combine multiple flavors</a>.
     */
    public void flavorDimensions(String... dimensions) {
        checkWritability();
        flavorDimensionList = Arrays.asList(dimensions);
    }

    /**
     * Encapsulates source set configurations for all variants.
     *
     * <p>Note that the Android plugin uses its own implementation of source sets. For more
     * information about the properties you can configure in this block, see {@link
     * AndroidSourceSet}.
     */
    public void sourceSets(Action<NamedDomainObjectContainer<AndroidSourceSet>> action) {
        checkWritability();
        sourceSetManager.executeAction(action);
    }

    /** {@inheritDoc} */
    @Override
    public NamedDomainObjectContainer<AndroidSourceSet> getSourceSets() {
        return sourceSetManager.getSourceSetsContainer();
    }

    /**
     * All build outputs for all variants, can be used by users to customize a build output.
     *
     * @return a container for build outputs.
     */
    @Override
    public NamedDomainObjectContainer<BaseVariantOutput> getBuildOutputs() {
        return buildOutputs;
    }

    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * <p>You can override any <code>defaultConfig</code> property when <a
     * href="https://developer.android.com/studio/build/build-variants.html#product-flavors">
     * configuring product flavors</a>.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * ProductFlavor}.
     */
    public void defaultConfig(Action<DefaultConfig> action) {
        checkWritability();
        action.execute(defaultConfig);
    }

    /**
     * Specifies options for the Android Asset Packaging Tool (AAPT).
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * AaptOptions}.
     */
    public void aaptOptions(Action<AaptOptions> action) {
        checkWritability();
        action.execute(aaptOptions);
    }

    /**
     * Specifies options for the DEX tool, such as enabling library pre-dexing.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * DexOptions}.
     */
    public void dexOptions(Action<DexOptions> action) {
        checkWritability();
        action.execute(dexOptions);
    }

    /**
     * Specifies options for the lint tool.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * LintOptions}.
     */
    public void lintOptions(Action<LintOptions> action) {
        checkWritability();
        action.execute(lintOptions);
    }

    /**
     * Configures external native build using <a href="https://cmake.org/">CMake</a> or <a
     * href="https://developer.android.com/ndk/guides/build.html">ndk-build</a>.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * ExternalNativeBuild}.
     */
    public void externalNativeBuild(Action<ExternalNativeBuild> action) {
        checkWritability();
        action.execute(externalNativeBuild);
    }

    /**
     * Specifies options for how the Android plugin should run local and instrumented tests.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * TestOptions}.
     */
    public void testOptions(Action<TestOptions> action) {
        checkWritability();
        action.execute(testOptions);
    }

    /**
     * Specifies Java compiler options, such as the language level of the Java source code and
     * generated bytecode.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * com.android.build.gradle.internal.CompileOptions}.
     */
    public void compileOptions(Action<CompileOptions> action) {
        checkWritability();
        action.execute(compileOptions);
    }

    /**
     * Specifies options and rules that determine which files the Android plugin packages into your
     * APK.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * PackagingOptions}.
     */
    public void packagingOptions(Action<PackagingOptions> action) {
        checkWritability();
        action.execute(packagingOptions);
    }

    /**
     * Configure JaCoCo version that is used for offline instrumentation and coverage report.
     *
     * <p>To specify the version of JaCoCo you want to use, add the following to <code>build.gradle
     * </code> file:
     *
     * <pre>
     * android {
     *     jacoco {
     *         version "&lt;jacoco-version&gt;"
     *     }
     * }
     * </pre>
     */
    public void jacoco(Action<JacocoOptions> action) {
        checkWritability();
        action.execute(jacoco);
    }

    /**
     * Specifies options for the <a
     * href="https://developer.android.com/studio/command-line/adb.html">Android Debug Bridge
     * (ADB)</a>, such as APK installation options.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * AdbOptions}.
     */
    public void adbOptions(Action<AdbOptions> action) {
        checkWritability();
        action.execute(adbOptions);
    }

    /**
     * Specifies configurations for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html">building multiple
     * APKs</a> or APK splits.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * Splits}.
     */
    public void splits(Action<Splits> action) {
        checkWritability();
        action.execute(splits);
    }

    /**
     * Specifies options for the <a
     * href="https://developer.android.com/topic/libraries/data-binding/index.html">Data Binding
     * Library</a>.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * DataBindingOptions}.
     */
    public void dataBinding(Action<DataBindingOptions> action) {
        checkWritability();
        action.execute(dataBinding);
    }

    /** {@inheritDoc} */
    @Override
    public DataBindingOptions getDataBinding() {
        return dataBinding;
    }

    public void deviceProvider(DeviceProvider deviceProvider) {
        checkWritability();
        deviceProviderList.add(deviceProvider);
    }

    @Override
    @NonNull
    public List<DeviceProvider> getDeviceProviders() {
        return deviceProviderList;
    }

    public void testServer(TestServer testServer) {
        checkWritability();
        testServerList.add(testServer);
    }

    @Override
    @NonNull
    public List<TestServer> getTestServers() {
        return testServerList;
    }

    public void registerTransform(@NonNull Transform transform, Object... dependencies) {
        transforms.add(transform);
        transformDependencies.add(Arrays.asList(dependencies));
    }

    @Override
    @NonNull
    public List<Transform> getTransforms() {
        return ImmutableList.copyOf(transforms);
    }

    @Override
    @NonNull
    public List<List<Object>> getTransformsDependencies() {
        return ImmutableList.copyOf(transformDependencies);
    }

    /** {@inheritDoc} */
    @Override
    public NamedDomainObjectContainer<ProductFlavor> getProductFlavors() {
        return productFlavors;
    }

    /** {@inheritDoc} */
    @Override
    public NamedDomainObjectContainer<BuildType> getBuildTypes() {
        return buildTypes;
    }

    /** {@inheritDoc} */
    @Override
    public NamedDomainObjectContainer<SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public void defaultPublishConfig(String value) {
        setDefaultPublishConfig(value);
    }

    /** {@inheritDoc} */
    @Override
    public String getDefaultPublishConfig() {
        return defaultPublishConfig;
    }

    public void setDefaultPublishConfig(String value) {
        defaultPublishConfig = value;
    }

    public void setPublishNonDefault(boolean publishNonDefault) {
        logger.warn("publishNonDefault is deprecated and has no effect anymore. All variants are now published.");
    }

    public void variantFilter(Action<VariantFilter> filter) {
        setVariantFilter(filter);
    }

    public void setVariantFilter(Action<VariantFilter> filter) {
        variantFilter = filter;
    }

    /** {@inheritDoc} */
    @Override
    public Action<VariantFilter> getVariantFilter() {
        return variantFilter;
    }

    /** {@inheritDoc} */
    @Override
    public AdbOptions getAdbOptions() {
        return adbOptions;
    }

    /** {@inheritDoc} */
    @Override
    public String getResourcePrefix() {
        return resourcePrefix;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getFlavorDimensionList() {
        return flavorDimensionList;
    }

    /** {@inheritDoc} */
    @Incubating
    @Override
    public boolean getGeneratePureSplits() {
        return generatePureSplits;
    }

    public void resourcePrefix(String prefix) {
        resourcePrefix = prefix;
    }

    public abstract void addVariant(BaseVariant variant);

    public void registerArtifactType(@NonNull String name,
            boolean isTest,
            int artifactType) {
        extraModelInfo.registerArtifactType(name, isTest, artifactType);
    }

    public void registerBuildTypeSourceProvider(
            @NonNull String name,
            @NonNull BuildType buildType,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerBuildTypeSourceProvider(name, buildType, sourceProvider);
    }

    public void registerProductFlavorSourceProvider(
            @NonNull String name,
            @NonNull ProductFlavor productFlavor,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerProductFlavorSourceProvider(name, productFlavor, sourceProvider);
    }

    public void registerJavaArtifact(
            @NonNull String name,
            @NonNull BaseVariant variant,
            @NonNull String assembleTaskName,
            @NonNull String javaCompileTaskName,
            @NonNull Collection<File> generatedSourceFolders,
            @NonNull Iterable<String> ideSetupTaskNames,
            @NonNull Configuration configuration,
            @NonNull File classesFolder,
            @NonNull File javaResourceFolder,
            @Nullable SourceProvider sourceProvider) {
        extraModelInfo.registerJavaArtifact(name, variant, assembleTaskName,
                javaCompileTaskName, generatedSourceFolders, ideSetupTaskNames,
                configuration, classesFolder, javaResourceFolder, sourceProvider);
    }

    public void registerMultiFlavorSourceProvider(
            @NonNull String name,
            @NonNull String flavorName,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerMultiFlavorSourceProvider(name, flavorName, sourceProvider);
    }

    @NonNull
    public static SourceProvider wrapJavaSourceSet(@NonNull SourceSet sourceSet) {
        return new SourceSetSourceProviderWrapper(sourceSet);
    }

    /** {@inheritDoc} */
    @Override
    public String getCompileSdkVersion() {
        return target;
    }

    /** {@inheritDoc} */
    @Internal
    @NonNull
    @Override
    public Revision getBuildToolsRevision() {
        return buildToolsRevision;
    }

    @Override
    public Collection<LibraryRequest> getLibraryRequests() {
        return libraryRequests;
    }

    /**
     * Returns the path to the Android SDK that Gradle uses for this project.
     *
     * <p>To learn more about downloading and installing the Android SDK, read <a
     * href="https://developer.android.com/studio/intro/update.html#sdk-manager">Update Your Tools
     * with the SDK Manager</a>.
     */
    public File getSdkDirectory() {
        return sdkHandler.getSdkFolder();
    }

    /**
     * Returns the path to the <a href="https://developer.android.com/ndk/index.html">Android
     * NDK</a> that Gradle uses for this project.
     *
     * <p>You can install the Android NDK by either <a
     * href="https://developer.android.com/studio/intro/update.html#sdk-manager">using the SDK
     * manager</a> or downloading <a href="https://developer.android.com/ndk/downloads/index.html">
     * the standalone NDK package</a>.
     */
    public File getNdkDirectory() {
        return sdkHandler.getNdkFolder();
    }

    @Override
    public List<File> getBootClasspath() {
        if (!ensureTargetSetup()) {
            // In sync mode where the SDK could not be installed.
            return ImmutableList.of();
        }

        boolean usingJava8 = compileOptions.getTargetCompatibility().isJava8Compatible();
        List<File> bootClasspath = Lists.newArrayListWithExpectedSize(usingJava8 ? 2 : 1);
        bootClasspath.addAll(globalScope.getAndroidBuilder().getBootClasspath(false));

        if (usingJava8) {
            bootClasspath.add(
                    new File(
                            globalScope
                                    .getAndroidBuilder()
                                    .getBuildToolInfo()
                                    .getPath(BuildToolInfo.PathId.CORE_LAMBDA_STUBS)));
        }

        return bootClasspath;
    }

    /**
     * Returns a path to the <a
     * href="https://developer.android.com/studio/command-line/adb.html">Android Debug Bridge
     * (ADB)</a> executable from the Android SDK.
     */
    public File getAdbExecutable() {
        return sdkHandler.getSdkInfo().getAdb();
    }

    /** This property is deprecated. Instead, use {@link #getAdbExecutable()}. */
    @Deprecated
    public File getAdbExe() {
        return getAdbExecutable();
        // test
    }

    public File getDefaultProguardFile(String name) {
        if (!ProguardFiles.KNOWN_FILE_NAMES.contains(name)) {
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportError(
                            EvalIssueReporter.Type.GENERIC,
                            new EvalIssueException(ProguardFiles.UNKNOWN_FILENAME_MESSAGE));
        }
        return ProguardFiles.getDefaultProguardFile(name, project);
    }

    // ---------------
    // TEMP for compatibility

    // by default, we do not generate pure splits
    boolean generatePureSplits = false;

    public void generatePureSplits(boolean flag) {
        setGeneratePureSplits(flag);
    }

    public void setGeneratePureSplits(boolean flag) {
        this.generatePureSplits = flag;
    }

    /** {@inheritDoc} */
    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    /** {@inheritDoc} */
    @Override
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    /** {@inheritDoc} */
    @Override
    public CompileOptions getCompileOptions() {
        return compileOptions;
    }

    /** {@inheritDoc} */
    @Override
    public DexOptions getDexOptions() {
        return dexOptions;
    }

    /** {@inheritDoc} */
    @Override
    public JacocoOptions getJacoco() {
        return jacoco;
    }

    /** {@inheritDoc} */
    @Override
    public LintOptions getLintOptions() {
        return lintOptions;
    }

    /** {@inheritDoc} */
    @Override
    public ExternalNativeBuild getExternalNativeBuild() {
        return externalNativeBuild;
    }

    /** {@inheritDoc} */
    @Override
    public PackagingOptions getPackagingOptions() {
        return packagingOptions;
    }

    /** {@inheritDoc} */
    @Override
    public Splits getSplits() {
        return splits;
    }

    /** {@inheritDoc} */
    @Override
    public TestOptions getTestOptions() {
        return testOptions;
    }

    private boolean ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = globalScope.getAndroidBuilder().getTargetInfo();
        if (targetInfo == null) {
            return sdkHandler.initTarget(
                    getCompileSdkVersion(),
                    buildToolsRevision,
                    libraryRequests,
                    globalScope.getAndroidBuilder(),
                    SdkHandler.useCachedSdk(projectOptions));
        }
        return true;
    }

    // For compatibility with LibraryExtension.
    @Override
    public Boolean getPackageBuildConfig() {
        throw new GradleException("packageBuildConfig is not supported.");
    }

    @Override
    public Collection<String> getAidlPackageWhiteList() {
        throw new GradleException("aidlPackageWhiteList is not supported.");
    }

    // For compatibility with FeatureExtension.
    @Override
    public Boolean getBaseFeature() {
        return isBaseModule;
    }
}
