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

package com.android.build.api.dsl.extension

import org.gradle.api.Incubating

/** 'android' extension for 'com.android.library' projects.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface LibraryExtension : BuildProperties, VariantOrExtensionProperties, VariantAwareProperties, EmbeddedTestProperties, OnDeviceTestProperties, AndroidExtension {

    /**
     * Specifies the version of the module to publish externally. This property is generally useful
     * only to library modules that you intend to publish to a remote repository, such as Maven.
     *
     * If you plan to only consume your library module locally, you do not need to configure this
     * property. Android plugin 3.0.0 and higher use
     * [variant-aware dependency resolution](https://d.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware)
     * to automatically match the variant of the producer to that of the
     * consumer. That is, when publishing a module to another local module, the plugin no longer
     * uses this property to determine which version of the module to publish to the consumer.
     *
     * For library modules that you intend to publish to a remote repository, such as Maven, the
     * Android plugin publishes the release version of the module by default. You can use this
     * property to specify a different build type that the plugin should publish. If you also
     * configure [product flavors](https://d.android.com/studio/build/build-variants.html#product-flavors),
     * you need to specify the name of the build variant you want the plugin to publish, as shown
     * below:
     *
     * ```
     * // Specifies the 'demoDebug' build variant as the default variant
     * // that the plugin should publish to external consumers.
     * defaultPublishConfig 'demoDebug'
     * ```
     */
    var defaultPublishConfig: String

    /** Aidl files to package in the aar.  */
    var aidlPackageWhiteList: Collection<String>

    // --- DEPRECATED

    @Deprecated("This always return false ")
    var packageBuildConfig: Boolean
}
