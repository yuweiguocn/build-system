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

package com.android.build.gradle.options

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.model.AndroidProject

enum class BooleanOption(
    override val propertyName: String,
    override val defaultValue: Boolean = false,
    override val status: Option.Status = Option.Status.EXPERIMENTAL,
    override val additionalInfo: String = ""
) : Option<Boolean> {

    // ---------------
    // Permanent IDE Flags -- no lifecycle

    IDE_INVOKED_FROM_IDE(AndroidProject.PROPERTY_INVOKED_FROM_IDE, status = Option.Status.STABLE),
    IDE_BUILD_MODEL_ONLY(AndroidProject.PROPERTY_BUILD_MODEL_ONLY, status = Option.Status.STABLE),
    IDE_BUILD_MODEL_ONLY_ADVANCED(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED, status = Option.Status.STABLE),
    IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES(
        AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES, status = Option.Status.STABLE),
    IDE_REFRESH_EXTERNAL_NATIVE_MODEL(AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL, status = Option.Status.STABLE),
    IDE_GENERATE_SOURCES_ONLY(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY, status = Option.Status.STABLE),

    // tell bundletool to only extract instant APKs.
    IDE_EXTRACT_INSTANT(AndroidProject.PROPERTY_EXTRACT_INSTANT_APK, status = Option.Status.STABLE),

    // Flag used to indicate a "deploy as instant" run configuration.
    IDE_DEPLOY_AS_INSTANT_APP(AndroidProject.PROPERTY_DEPLOY_AS_INSTANT_APP, false, status = Option.Status.STABLE),


    // ---------------
    // Permanent Other Flags -- No lifecycle

    // Used by Studio as workaround for b/71054106, b/75955471
    ENABLE_SDK_DOWNLOAD("android.builder.sdkDownload", true, status = Option.Status.STABLE),
    ENABLE_PROFILE_JSON("android.enableProfileJson", false),
    WARN_ABOUT_DEPENDENCY_RESOLUTION_AT_CONFIGURATION("android.dependencyResolutionAtConfigurationTime.warn"),
    DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION("android.dependencyResolutionAtConfigurationTime.disallow"),
    DEBUG_OBSOLETE_API("android.debug.obsoleteApi", false, Option.Status.STABLE),

    // ---------------
    // Lifecycle flags: Experimental stage, not yet enabled by default
    ENABLE_TEST_SHARDING("android.androidTest.shardBetweenDevices"),
    VERSION_CHECK_OVERRIDE_PROPERTY("android.overrideVersionCheck"),
    OVERRIDE_PATH_CHECK_PROPERTY("android.overridePathCheck"),
    ENABLE_GRADLE_WORKERS("android.enableGradleWorkers", false),
    DISABLE_RESOURCE_VALIDATION("android.disableResourceValidation"),
    CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES("android.consumeDependenciesAsSharedLibraries"),
    KEEP_TIMESTAMPS_IN_APK("android.keepTimestampsInApk"),
    ENABLE_NEW_DSL_AND_API("android.enableNewDsl"),
    ENABLE_EXPERIMENTAL_FEATURE_DATABINDING("android.enableExperimentalFeatureDatabinding", false),
    ENABLE_JETIFIER("android.enableJetifier", false, status = Option.Status.STABLE),
    USE_ANDROID_X("android.useAndroidX", false, status = Option.Status.STABLE),
    DISABLE_EARLY_MANIFEST_PARSING("android.disableEarlyManifestParsing", false),
    DEPLOYMENT_USES_DIRECTORY("android.deployment.useOutputDirectory", false),
    DEPLOYMENT_PROVIDES_LIST_OF_CHANGES("android.deployment.provideListOfChanges", false),
    ENABLE_RESOURCE_NAMESPACING_DEFAULT("android.enableResourceNamespacingDefault", false),
    NAMESPACED_R_CLASS("android.namespacedRClass", false),
    ENABLE_SEPARATE_ANNOTATION_PROCESSING("android.enableSeparateAnnotationProcessing", false),
    FULL_R8("android.enableR8.fullMode", false),
    CONDITIONAL_KEEP_RULES("android.useConditionalKeepRules", false),
    ENFORCE_UNIQUE_PACKAGE_NAMES("android.uniquePackageNames", false, status = Option.Status.STABLE),

    // ---------------
    // Lifecycle flags: Stable stage, Enabled by default, can be disabled

    ENABLE_BUILD_CACHE("android.enableBuildCache", true),
    ENABLE_INTERMEDIATE_ARTIFACTS_CACHE("android.enableIntermediateArtifactsCache", true),
    ENABLE_EXTRACT_ANNOTATIONS("android.enableExtractAnnotations", true),
    ENABLE_AAPT2_WORKER_ACTIONS("android.enableAapt2WorkerActions", true),
    ENABLE_D8_DESUGARING("android.enableD8.desugaring", true),
    ENABLE_R8("android.enableR8", true, status = Option.Status.STABLE),
    ENABLE_R8_LIBRARIES("android.enableR8.libraries", true, status = Option.Status.STABLE),
    /** Set to true by default, but has effect only if R8 is enabled. */
    ENABLE_R8_DESUGARING("android.enableR8.desugaring", true),
    // Marked as stable to avoid reporting deprecation twice.
    CONVERT_NON_NAMESPACED_DEPENDENCIES("android.convertNonNamespacedDependencies", true),
    /** Set to true to build native .so libraries only for the device it will be run on. */
    BUILD_ONLY_TARGET_ABI("android.buildOnlyTargetAbi", true),
    ENABLE_DATA_BINDING_V2("android.databinding.enableV2", true),
    ENABLE_SEPARATE_APK_RESOURCES("android.enableSeparateApkRes", true),
    ENABLE_SEPARATE_R_CLASS_COMPILATION(AndroidProject.PROPERTY_SEPARATE_R_CLASS_COMPILATION, true),
    ENABLE_PARALLEL_NATIVE_JSON_GEN("android.enableParallelJsonGen", true),
    ENABLE_SIDE_BY_SIDE_CMAKE("android.enableSideBySideCmake", true),
    ENABLE_NATIVE_COMPILER_SETTINGS_CACHE("android.enableNativeCompilerSettingsCache", false),
    ENABLE_PROGUARD_RULES_EXTRACTION("android.proguard.enableRulesExtraction", true),
    ENABLE_UNCOMPRESSED_NATIVE_LIBS_IN_BUNDLE("android.bundle.enableUncompressedNativeLibs", true),
    USE_DEPENDENCY_CONSTRAINTS("android.dependency.useConstraints", true),
    ENABLE_DEXING_ARTIFACT_TRANSFORM("android.enableDexingArtifactTransform", true, status=Option.Status.STABLE),
    ENABLE_UNIT_TEST_BINARY_RESOURCES("android.enableUnitTestBinaryResources", true, Option.Status.STABLE),
    ENABLE_DUPLICATE_CLASSES_CHECK("android.enableDuplicateClassesCheck", true),

    // ---------------
    // Lifecycle flags: Deprecated stage, feature is stable and we want to get rid of the ability to revert to older code path
    ENABLE_DESUGAR(
        "android.enableDesugar", true, DeprecationReporter.DeprecationTarget.DESUGAR_TOOL),
    ENABLE_D8("android.enableD8", true, DeprecationReporter.DeprecationTarget.LEGACY_DEXER),
    INJECT_SDK_MAVEN_REPOS(
        "android.injectSdkMavenRepos",
        false,
        Option.Status.Deprecated(DeprecationReporter.DeprecationTarget.SDK_MAVEN_REPOS)),

    ;
    constructor(
        propertyName: String,
        defaultValue: Boolean,
        deprecationTarget: DeprecationReporter.DeprecationTarget
    ) :
            this(propertyName, defaultValue, Option.Status.Deprecated(deprecationTarget))

    override fun parse(value: Any): Boolean {
        return parseBoolean(propertyName, value)
    }
}
