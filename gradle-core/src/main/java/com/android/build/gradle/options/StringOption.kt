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

import com.android.build.gradle.options.Option.Status.EXPERIMENTAL
import com.android.build.gradle.options.Option.Status.STABLE
import com.android.builder.model.AndroidProject

enum class StringOption(
    override val propertyName: String,
    override val status: Option.Status = EXPERIMENTAL
) : Option<String> {
    BUILD_CACHE_DIR("android.buildCacheDir", status = STABLE),

    IDE_BUILD_TARGET_DENSITY(AndroidProject.PROPERTY_BUILD_DENSITY, status = STABLE),
    IDE_BUILD_TARGET_ABI(AndroidProject.PROPERTY_BUILD_ABI, status = STABLE),

    IDE_RESTRICT_VARIANT_PROJECT(AndroidProject.PROPERTY_RESTRICT_VARIANT_PROJECT, status = STABLE),
    IDE_RESTRICT_VARIANT_NAME(AndroidProject.PROPERTY_RESTRICT_VARIANT_NAME, status = STABLE),

    // Signing options
    IDE_SIGNING_STORE_TYPE(AndroidProject.PROPERTY_SIGNING_STORE_TYPE, status = STABLE),
    IDE_SIGNING_STORE_FILE(AndroidProject.PROPERTY_SIGNING_STORE_FILE, status = STABLE),
    IDE_SIGNING_STORE_PASSWORD(AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD, status = STABLE),
    IDE_SIGNING_KEY_ALIAS(AndroidProject.PROPERTY_SIGNING_KEY_ALIAS, status = STABLE),
    IDE_SIGNING_KEY_PASSWORD(AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD, status = STABLE),

    // device config for ApkSelect
    IDE_APK_SELECT_CONFIG(AndroidProject.PROPERTY_APK_SELECT_CONFIG, status = STABLE),

    // location where to write the APK/BUNDLE
    IDE_APK_LOCATION(AndroidProject.PROPERTY_APK_LOCATION, status = STABLE),

    // Instant run
    IDE_OPTIONAL_COMPILATION_STEPS(AndroidProject.PROPERTY_OPTIONAL_COMPILATION_STEPS, status = STABLE),
    IDE_COLD_SWAP_MODE(AndroidProject.PROPERTY_SIGNING_COLDSWAP_MODE, status = STABLE),
    IDE_VERSION_NAME_OVERRIDE(AndroidProject.PROPERTY_VERSION_NAME, status = STABLE),

    IDE_TARGET_DEVICE_CODENAME(AndroidProject.PROPERTY_BUILD_API_CODENAME, status = STABLE),

    // Profiler plugin
    IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS("android.advanced.profiling.transforms", status = STABLE),

    // Testing
    DEVICE_POOL_SERIAL("com.android.test.devicepool.serial"),
    PROFILE_OUTPUT_DIR("android.advanced.profileOutputDir"),

    BUILD_ARTIFACT_REPORT_FILE("android.buildartifact.reportfile"),

    AAPT2_FROM_MAVEN_OVERRIDE("android.aapt2FromMavenOverride"),

    SUPPRESS_UNSUPPORTED_OPTION_WARNINGS("android.suppressUnsupportedOptionWarnings"),

    // The exact version of Android Studio used, e.g. 2.4.0.6
    IDE_ANDROID_STUDIO_VERSION(AndroidProject.PROPERTY_STUDIO_VERSION, status = STABLE),

    // Jetifier: List of regular expressions for libraries that should not be jetified
    JETIFIER_BLACKLIST("android.jetifier.blacklist"),

    ;

    override fun parse(value: Any): String {
        if (value is CharSequence || value is Number) {
            return value.toString()
        }
        throw IllegalArgumentException(
            "Cannot parse project property "
                    + this.propertyName
                    + "='"
                    + value
                    + "' of type '"
                    + value.javaClass
                    + "' as string."
        )
    }
}
