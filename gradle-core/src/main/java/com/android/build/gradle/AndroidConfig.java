/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.VariantFilter;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.builder.core.LibraryRequest;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.SigningConfig;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.repository.Revision;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.tasks.Internal;

/**
 * User configuration settings for all android plugins.
 */
public interface AndroidConfig {


    /**
     * Specifies the version of the <a
     * href="https://developer.android.com/studio/releases/build-tools.html">SDK Build Tools</a> to
     * use when building your project.
     *
     * <p>When using Android plugin 3.0.0 or later, configuring this property is optional. By
     * default, the plugin uses the minimum version of the build tools required by the <a
     * href="https://developer.android.com/studio/releases/gradle-plugin.html#revisions">version of
     * the plugin</a> you're using. To specify a different version of the build tools for the plugin
     * to use, specify the version as follows:
     *
     * <pre>
     * // Specifying this property is optional.
     * buildToolsVersion "26.0.0"
     * </pre>
     *
     * <p>For a list of build tools releases, read <a
     * href="https://developer.android.com/studio/releases/build-tools.html#notes">the release
     * notes</a>.
     *
     * <p>Note that the value assigned to this property is parsed and stored in a normalized form,
     * so reading it back may give a slightly different result.
     */
    String getBuildToolsVersion();

    /**
     * Specifies the API level to compile your project against. The Android plugin requires you to
     * configure this property.
     *
     * <p>This means your code can use only the Android APIs included in that API level and lower.
     * You can configure the compile sdk version by adding the following to the <code>android</code>
     * block: <code>compileSdkVersion 26</code>.
     *
     * <p>You should generally <a
     * href="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels">use
     * the most up-to-date API level</a> available. If you are planning to also support older API
     * levels, it's good practice to <a
     * href="https://developer.android.com/studio/write/lint.html">use the Lint tool</a> to check if
     * you are using APIs that are not available in earlier API levels.
     *
     * <p>The value you assign to this property is parsed and stored in a normalized form, so
     * reading it back may return a slightly different value.
     */
    String getCompileSdkVersion();

    /**
     * This property is for internal use only.
     *
     * <p>To specify the version of the <a
     * href="https://developer.android.com/studio/releases/build-tools.html">SDK Build Tools</a>
     * that the Android plugin should use, use <a
     * href="com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:buildToolsVersion">buildToolsVersion</a>
     * instead.
     */
    @Internal
    Revision getBuildToolsRevision();

    /**
     * Specifies the version of the module to publish externally. This property is generally useful
     * only to library modules that you intend to publish to a remote repository, such as Maven.
     *
     * <p>If you don't configure this property, the Android plugin publishes the release version of
     * the module by default. If the module configures <a
     * href="https://developer.android.com/studio/build/build-variants.html#product-flavors">product
     * flavors</a>, you need to configure this property with the name of the variant you want the
     * plugin to publish, as shown below:
     *
     * <pre>
     * // Specifies the 'demoDebug' build variant as the default variant
     * // that the plugin should publish to external consumers.
     * defaultPublishConfig 'demoDebug'
     * </pre>
     *
     * <p>If you plan to only consume your library module locally, you do not need to configure this
     * property. Android plugin 3.0.0 and higher use <a
     * href="https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware">variant-aware
     * dependency resolution</a> to automatically match the variant of the producer to that of the
     * consumer. That is, when publishing a module to another local module, the plugin no longer
     * respects this property when determining which version of the module to publish to the
     * consumer.
     */
    String getDefaultPublishConfig();

    /**
     * Specifies variants the Android plugin should include or remove from your Gradle project.
     *
     * <p>By default, the Android plugin creates a build variant for every possible combination of
     * the product flavors and build types that you configure, and adds them to your Gradle project.
     * However, there may be certain build variants that either you do not need or do not make sense
     * in the context of your project. You can remove certain build variant configurations by <a
     * href="https://developer.android.com/studio/build/build-variants.html#filter-variants">creating
     * a variant filter</a> in your module-level <code>build.gradle</code> file.
     *
     * <p>The following example tells the plugin to ignore all variants that combine the "dev"
     * product flavor, which you can configure to <a
     * href="https://developer.android.com/studio/build/optimize-your-build.html#create_dev_variant">optimize
     * build speeds</a> during development, and the "release" build type:
     *
     * <pre>
     * android {
     *     ...
     *     variantFilter { variant -&gt;
     *
     *         def buildTypeName = variant.buildType*.name
     *         def flavorName = variant.flavors*.name
     *
     *         if (flavorName.contains("dev") &amp;&amp; buildTypeName.contains("release")) {
     *             // Tells Gradle to ignore each variant that satisfies the conditions above.
     *             setIgnore(true)
     *         }
     *     }
     * }
     * </pre>
     *
     * <p>During subsequent builds, Gradle ignores any build variants that meet the conditions you
     * specify. If you're using <a href="https://developer.android.com/studio/index.html">Android
     * Studio</a>, those variants no longer appear in the drop down menu when you click <b>Build
     * &gt; Select Build Variant</b> from the menu bar.
     *
     * @see com.android.build.gradle.internal.api.VariantFilter
     */
    Action<VariantFilter> getVariantFilter();

    /**
     * Specifies APK install options for the <a
     * href="https://developer.android.com/studio/command-line/adb.html">Android Debug Bridge
     * (ADB)</a>.
     *
     * @see com.android.build.gradle.internal.dsl.AdbOptions
     */
    AdbOptions getAdbOptions();

    /**
     * Specifies the module's resource prefix to Android Studio for editor features, such as Lint
     * checks. This property is useful only when using Android Studio.
     *
     * <p>Including unique prefixes for module resources helps avoid naming collisions with
     * resources from other modules. For example, when creating a library with String resources, you
     * may want to name each resource with a unique prefix, such as <code>"mylib_"</code> to avoid
     * naming collisions with similar resources that the consumer defines. You can then specify this
     * prefix, as shown below, so that Android Studio expects this prefix when you name module
     * resources:
     *
     * <pre>
     * // This property is useful only when developing your project in Android Studio.
     * resourcePrefix 'mylib_'
     * </pre>
     */
    String getResourcePrefix();

    /**
     * Specifies the names of product flavor dimensions for this project.
     *
     * <p>To configure flavor dimensions, use <a
     * href="com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[])">
     * <code>flavorDimensions</code></a>. To learn more, read <a
     * href="https://developer.android.com/studio/build/build-variants.html#flavor-dimensions">combine
     * multiple product flavors</a>.
     */
    List<String> getFlavorDimensionList();

    /**
     * Specifies whether to build APK splits or multiple APKs from configurations in the {@link
     * com.android.build.gradle.internal.dsl.Splits splits} block.
     *
     * <p>When you set this property to <code>true</code>, the Android plugin generates each object
     * in the {@link com.android.build.gradle.internal.dsl.Splits splits} block as a portion of a
     * whole APK, called an <em>APK split</em>. Compared to building multiple APKs, each APK split
     * includes only the components that each ABI or screen density requires. Generating APK splits
     * is an incubating feature, which requires you to set {@link
     * com.android.build.gradle.internal.dsl.BaseFlavor#minSdkVersion(int)} to <code>21</code> or
     * higher, and is currently supported only when publishing <a
     * href="https://d.android.com/instant-apps">Android Instant Apps</a>.
     *
     * <p>When you do not configure this property or set it to <code>false</code> (default), the
     * Android plugin builds separate APKs for each object you configure in the {@link
     * com.android.build.gradle.internal.dsl.Splits splits} block that you can deploy to a device.
     * To learn more about building different versions of your app that each target a different <a
     * href="https://developer.android.com/ndk/guides/abis.html">Application Binary Interfaces</a>
     * or screen density, read <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html">Build Multiple
     * APKs</a>.
     */
    @Incubating
    boolean getGeneratePureSplits();

    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * <p>You can override any <code>defaultConfig</code> property when <a
     * href="https://developer.android.com/studio/build/build-variants.html#product-flavors">configuring
     * product flavors</a>.
     *
     * @see com.android.build.gradle.internal.dsl.ProductFlavor
     */
    CoreProductFlavor getDefaultConfig();

    /**
     * Specifies options for the Android Asset Packaging Tool (AAPT).
     *
     * @see com.android.build.gradle.internal.dsl.AaptOptions
     */
    AaptOptions getAaptOptions();

    /**
     * Specifies Java compiler options, such as the language level of the Java source code and
     * generated bytecode.
     *
     * @see com.android.build.gradle.internal.CompileOptions
     */
    CompileOptions getCompileOptions();

    /**
     * Specifies options for the DEX tool, such as enabling library pre-dexing.
     *
     * <p>Experimenting with DEX options tailored for your workstation may improve build
     * performance. To learn more, read <a
     * href="https://developer.android.com/studio/build/optimize-your-build.html#dex_options">Optimize
     * your build</a>.
     *
     * @see com.android.build.gradle.internal.dsl.DexOptions
     */
    DexOptions getDexOptions();

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
    JacocoOptions getJacoco();

    /**
     * Specifies options for the lint tool.
     *
     * <p>Android Studio and the Android SDK provide a code scanning tool called lint that can help
     * you to identify and correct problems with the structural quality of your code without having
     * to execute the app or write test cases. Each problem the tool detects is reported with a
     * description message and a severity level, so that you can quickly prioritize the critical
     * improvements that need to be made.
     *
     * <p>This property allows you to configure certain lint options, such as which checks to run or
     * ignore. If you're using Android Studio, you can <a
     * href="https://developer.android.com/studio/write/lint.html#cis">configure similar lint
     * options</a> from the IDE. To learn more about using and running lint, read <a
     * href="https://developer.android.com/studio/write/lint.html">Improve Your Code with Lint</a>.
     *
     * @see com.android.build.gradle.internal.dsl.LintOptions
     */
    LintOptions getLintOptions();

    /**
     * Specifies options for external native build using <a href="https://cmake.org/">CMake</a> or
     * <a href="https://developer.android.com/ndk/guides/ndk-build.html">ndk-build</a>.
     *
     * <p>When using <a href="https://developer.android.com/studio/index.html">Android Studio 2.2 or
     * higher</a> with <a
     * href="https://developer.android.com/studio/releases/gradle-plugin.html">Android plugin 2.2.0
     * or higher</a>, you can compile C and C++ code into a native library that Gradle packages into
     * your APK.
     *
     * <p>To learn more, read <a
     * href="https://developer.android.com/studio/projects/add-native-code.html">Add C and C++ Code
     * to Your Project</a>.
     *
     * @see com.android.build.gradle.internal.dsl.ExternalNativeBuild
     * @since 2.2.0
     */
    CoreExternalNativeBuild getExternalNativeBuild();

    /**
     * Specifies options and rules that determine which files the Android plugin packages into your
     * APK.
     *
     * <p>For example, the following example tells the plugin to avoid packaging files that are
     * intended only for testing:
     *
     * <pre>
     * packagingOptions {
     *     // Tells the plugin to not include any files in the 'testing-data/' directory,
     *     // which is specified as an absolute path from the root of the APK archive.
     *     // The exclude property includes certain defaults paths to help you avoid common
     *     // duplicate file errors when building projects with multiple dependencies.
     *     exclude "/testing-data/**"
     * }
     * </pre>
     *
     * <p>To learn more about how to specify rules for packaging, merging, and excluding files, see
     * {@link PackagingOptions}
     *
     * @see com.android.build.gradle.internal.dsl.PackagingOptions
     */
    PackagingOptions getPackagingOptions();

    /**
     * Specifies configurations for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html">building multiple
     * APKs</a> or APK splits.
     *
     * <p>To generate APK splits, you need to also set <a
     * href="com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:generatePureSplits">
     * <code>generatePureSplits</code></a> to <code>true</code>. However, generating APK splits is
     * an incubating feature, which requires you to set {@link
     * com.android.build.gradle.internal.dsl.BaseFlavor#minSdkVersion(int)} to <code>21</code> or
     * higher, and is currently supported only when publishing <a
     * href="https://d.android.com/instant-apps">Android Instant Apps</a>.
     *
     * @see com.android.build.gradle.internal.dsl.Splits
     */
    Splits getSplits();

    /**
     * Specifies options for how the Android plugin should run local and instrumented tests.
     *
     * <p>To learn more, read <a
     * href="https://developer.android.com/studio/test/index.html#test_options">Configure Gradle
     * test options</a>.
     *
     * @see com.android.build.gradle.internal.dsl.TestOptions
     */
    TestOptions getTestOptions();

    /** List of device providers */
    @NonNull
    List<DeviceProvider> getDeviceProviders();

    /** List of remote CI servers. */
    @NonNull
    List<TestServer> getTestServers();

    @NonNull
    List<Transform> getTransforms();
    @NonNull
    List<List<Object>> getTransformsDependencies();

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     * <p>Product flavors represent different versions of your project that you expect to co-exist
     * on a single device, the Google Play store, or repository. For example, you can configure
     * 'demo' and 'full' product flavors for your app, and each of those flavors can specify
     * different features, device requirements, resources, and application ID's--while sharing
     * common source code and resources. So, product flavors allow you to output different versions
     * of your project by simply changing only the components and settings that are different
     * between them.
     *
     * <p>Configuring product flavors is similar to <a
     * href="https://developer.android.com/studio/build/build-variants.html#build-types">configuring
     * build types</a>: add them to the <code>productFlavors</code> block of your module's <code>
     * build.gradle</code> file and configure the settings you want. Product flavors support the
     * same properties as the {@link com.android.build.gradle.internal.dsl.DefaultConfig}
     * block--this is because <code>defaultConfig</code> defines a {@link
     * com.android.build.gradle.internal.dsl.ProductFlavor} object that the plugin uses as the base
     * configuration for all other flavors. Each flavor you configure can then override any of the
     * default values in <code>defaultConfig</code>, such as the <a
     * href="https://d.android.com/studio/build/application-id.html"><code>applicationId</code></a>.
     *
     * <p>When using Android plugin 3.0.0 and higher, <em>each flavor must belong to a <a
     * href="com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[])">
     * <code>flavorDimensions</code></a> value</em>. By default, when you specify only one
     * dimension, all flavors you configure belong to that dimension. If you specify more than one
     * flavor dimension, you need to manually assign each flavor to a dimension. To learn more, read
     * <a
     * href="https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware">
     * Use Flavor Dimensions for variant-aware dependency management</a>.
     *
     * <p>When you configure product flavors, the Android plugin automatically combines them with
     * your {@link com.android.build.gradle.internal.dsl.BuildType} configurations to <a
     * href="https://developer.android.com/studio/build/build-variants.html">create build
     * variants</a>. If the plugin creates certain build variants that you don't want, you can <a
     * href="https://developer.android.com/studio/build/build-variants.html#filter-variants">filter
     * variants</a>.
     *
     * @see com.android.build.gradle.internal.dsl.ProductFlavor
     */
    Collection<? extends CoreProductFlavor> getProductFlavors();

    /**
     * Encapsulates all build type configurations for this project.
     *
     * <p>Unlike using {@link com.android.build.gradle.internal.dsl.ProductFlavor} to create
     * different versions of your project that you expect to co-exist on a single device, build
     * types determine how Gradle builds and packages each version of your project. Developers
     * typically use them to configure projects for various stages of a development lifecycle. For
     * example, when creating a new project from Android Studio, the Android plugin configures a
     * 'debug' and 'release' build type for you. By default, the 'debug' build type enables
     * debugging options and signs your APK with a generic debug keystore. Conversely, The 'release'
     * build type strips out debug symbols and requires you to <a
     * href="https://developer.android.com/studio/publish/app-signing.html#sign-apk">create a
     * release key and keystore</a> for your app. You can then combine build types with product
     * flavors to <a href="https://developer.android.com/studio/build/build-variants.html">create
     * build variants</a>.
     *
     * @see com.android.build.gradle.internal.dsl.BuildType
     */
    Collection<? extends CoreBuildType> getBuildTypes();

    /**
     * Encapsulates signing configurations that you can apply to {@link
     * com.android.build.gradle.internal.dsl.BuildType} and {@link
     * com.android.build.gradle.internal.dsl.ProductFlavor} configurations.
     *
     * <p>Android requires that all APKs be digitally signed with a certificate before they can be
     * installed onto a device. When deploying a debug version of your project from Android Studio,
     * the Android plugin automatically signs your APK with a generic debug certificate. However, to
     * build an APK for release, you must <a
     * href="https://developer.android.com/studio/publish/app-signing.html">sign the APK</a> with a
     * release key and keystore. You can do this by either <a
     * href="https://developer.android.com/studio/publish/app-signing.html#sign-apk">using the
     * Android Studio UI</a> or manually <a
     * href="https://developer.android.com/studio/publish/app-signing.html#gradle-sign">configuring
     * your <code>build.gradle</code> file</a>.
     *
     * @see com.android.build.gradle.internal.dsl.SigningConfig
     */
    Collection<? extends SigningConfig> getSigningConfigs();

    /**
     * Encapsulates source set configurations for all variants.
     *
     * <p>The Android plugin looks for your project's source code and resources in groups of
     * directories called <i><a
     * href="https://developer.android.com/studio/build/index.html#sourcesets">source sets</a></i>.
     * Each source set also determines the scope of build outputs that should consume its code and
     * resources. For example, when creating a new project from Android Studio, the IDE creates
     * directories for a <code>main/</code> source set that contains the code and resources you want
     * to share between all your build variants.
     *
     * <p>You can then define basic functionality in the <code>main/</code> source set, but use
     * product flavor source sets to change only the branding of your app between different clients,
     * or include special permissions and logging functionality to only "debug" versions of your
     * app.
     *
     * <p>The Android plugin expects you to organize files for source set directories a certain way,
     * similar to the <code>main/</code> source set. For example, Gradle expects Java class files
     * that are specific to your "debug" build type to be located in the <code>src/debug/java/
     * </code> directory.
     *
     * <p>Gradle provides a useful task to shows you how to organize your files for each build
     * type-, product flavor-, and build variant-specific source set. you can run this task from the
     * command line as follows:
     *
     * <pre>./gradlew sourceSets</pre>
     *
     * <p>The following sample output describes where Gradle expects to find certain files for the
     * "debug" build type:
     *
     * <pre>
     * ------------------------------------------------------------
     * Project :app
     * ------------------------------------------------------------
     *
     * ...
     *
     * debug
     * ----
     * Compile configuration: compile
     * build.gradle name: android.sourceSets.debug
     * Java sources: [app/src/debug/java]
     * Manifest file: app/src/debug/AndroidManifest.xml
     * Android resources: [app/src/debug/res]
     * Assets: [app/src/debug/assets]
     * AIDL sources: [app/src/debug/aidl]
     * RenderScript sources: [app/src/debug/rs]
     * JNI sources: [app/src/debug/jni]
     * JNI libraries: [app/src/debug/jniLibs]
     * Java-style resources: [app/src/debug/resources]
     * </pre>
     *
     * <p>If you have sources that are not organized into the default source set directories that
     * Gradle expects, as described in the sample output above, you can use the <code>sourceSet
     * </code> block to change where Gradle looks to gather files for each component of a given
     * source set. You don't need to relocate the files; you only need to provide Gradle with the
     * path(s), relative to the module-level <code>build.gradle</code> file, where Gradle should
     * expect to find files for each source set component.
     *
     * <p><b>Note:</b> You should specify only static paths whenever possible. Specifying dynamic
     * paths reduces build speed and consistency.
     *
     * <p>The following code sample maps sources from the <code>app/other/</code> directory to
     * certain components of the <code>main</code> source set and changes the root directory of the
     * <code>androidTest</code> source set:
     *
     * <pre>
     * android {
     *   ...
     *   sourceSets {
     *     // Encapsulates configurations for the main source set.
     *     main {
     *         // Changes the directory for Java sources. The default directory is
     *         // 'src/main/java'.
     *         java.srcDirs = ['other/java']
     *
     *         // If you list multiple directories, Gradle uses all of them to collect
     *         // sources. Because Gradle gives these directories equal priority, if
     *         // you define the same resource in more than one directory, you get an
     *         // error when merging resources. The default directory is 'src/main/res'.
     *         res.srcDirs = ['other/res1', 'other/res2']
     *
     *         // Note: You should avoid specifying a directory which is a parent to one
     *         // or more other directories you specify. For example, avoid the following:
     *         // res.srcDirs = ['other/res1', 'other/res1/layouts', 'other/res1/strings']
     *         // You should specify either only the root 'other/res1' directory, or only the
     *         // nested 'other/res1/layouts' and 'other/res1/strings' directories.
     *
     *         // For each source set, you can specify only one Android manifest.
     *         // By default, Android Studio creates a manifest for your main source
     *         // set in the src/main/ directory.
     *         manifest.srcFile 'other/AndroidManifest.xml'
     *         ...
     *     }
     *
     *     // Create additional blocks to configure other source sets.
     *     androidTest {
     *         // If all the files for a source set are located under a single root
     *         // directory, you can specify that directory using the setRoot property.
     *         // When gathering sources for the source set, Gradle looks only in locations
     *         // relative to the root directory you specify. For example, after applying the
     *         // configuration below for the androidTest source set, Gradle looks for Java
     *         // sources only in the src/tests/java/ directory.
     *         setRoot 'src/tests'
     *         ...
     *     }
     *   }
     * }
     * </pre>
     *
     * @see com.android.build.gradle.internal.dsl.AndroidSourceSetFactory
     */
    NamedDomainObjectContainer<AndroidSourceSet> getSourceSets();

    /** build outputs for all variants */
    Collection<BaseVariantOutput> getBuildOutputs();

    /** Whether to package build config class file. */
    Boolean getPackageBuildConfig();

    /** Aidl files to package in the aar. */
    Collection<String> getAidlPackageWhiteList();

    Collection<LibraryRequest> getLibraryRequests();

    /**
     * Specifies options for the <a
     * href="https://developer.android.com/topic/libraries/data-binding/index.html">Data Binding
     * Library</a>.
     *
     * <p>Data binding helps you write declarative layouts and minimize the glue code necessary to
     * bind your application logic and layouts.
     */
    DataBindingOptions getDataBinding();

    /** Whether the feature module is the base feature. */
    Boolean getBaseFeature();

    /**
     * Name of the build type that will be used when running Android (on-device) tests.
     *
     * <p>Defaults to "debug".
     *
     * <p>FIXME this should not be here, but it has to be because of gradle-core not knowing
     * anything besides this interface. This will be fixed with the new gradle-api based extension
     * interfaces.
     */
    @Nullable
    String getTestBuildType();

    /**
     * Name of the NDK version that will be used when building native code.
     *
     * <p>The value null means that no particular NDK version is requested. In this case, the latest
     * available NDK will be used.
     */
    @Nullable
    String getNdkVersion();

    /** Returns the list of files that form bootClasspath used for compilation. */
    List<File> getBootClasspath();
}
