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

package com.android.build.api.dsl.model

import com.android.build.api.dsl.options.PostProcessingOptions
import org.gradle.api.Action
import org.gradle.api.Incubating

/** properties common to build types and variants
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface BuildTypeOrVariant {

    /** Whether the build type is configured to generate a debuggable apk.  */
    var debuggable: Boolean

    /**
     * Whether test coverage is enabled for this build type.
     *
     * If enabled this uses Jacoco to capture coverage and creates a report in the build
     * directory.
     *
     *
     * The version of Jacoco can be configured with:
     *
     * <pre>
     * android {
     * jacoco {
     * version = '0.6.2.201302030002'
     * }
     * }
    </pre> *
     */
    var testCoverageEnabled: Boolean

    /**
     * Whether to generate pseudo locale in the APK.
     *
     *
     * If enabled, 2 fake pseudo locales (en-XA and ar-XB) will be added to the APK to help test
     * internationalization support in the app.
     */
    var pseudoLocalesEnabled: Boolean


    /** Whether this build type is configured to generate an APK with debuggable native code.  */
    var jniDebuggable: Boolean

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    var renderscriptDebuggable: Boolean


    /** Optimization level to use by the renderscript compiler.  */
    var renderscriptOptimLevel: Int

    /** Whether zipalign is enabled for this build type.  */
    var zipAlignEnabled: Boolean

    /**
     * Whether a linked Android Wear app should be embedded in variant using this build type.
     *
     *
     * Wear apps can be linked with the following code:
     *
     * <pre>
     * dependencies {
     * freeWearApp project(:wear:free') // applies to variant using the free flavor
     * wearApp project(':wear:base') // applies to all other variants
     * }
    </pre> *
     */
    var embedMicroApp: Boolean

    /**
     * Specifies whether to crunch PNG files.
     *
     * Setting this property to `true` reduces the size of PNG resources that are not already
     * optimally compressed. However, this process increases build times. So, to reduce your APK
     * size and its build time, try
     * [converting your images to WebP](https://developer.android.com/studio/write/convert-webp.html#convert_images_to_webp).
     *
     * __Note:__ PNG crunching is enabled by default in the release build type and disabled by
     * default in the debug build type.
     *
     * @since 3.0.0
     */
    var crunchPngs: Boolean

    /** Configures the post-processing options with the given action.  */
    fun postProcessing(action: Action<PostProcessingOptions>)

    val postProcessing: PostProcessingOptions

    // DEPRECATED

    @Deprecated("Use property debuggable")
    fun isDebuggable(): Boolean

    @Deprecated("Use property testCoverageEnabled")
    fun isTestCoverageEnabled(): Boolean

    @Deprecated("Use property embedMicroApp")
    fun isEmbedMicroApp(): Boolean

    @Deprecated("Use property pseudoLocalesEnabled")
    fun isPseudoLocalesEnabled(): Boolean

    @Deprecated("Use property jniDebuggable")
    fun isJniDebuggable(): Boolean

    @Deprecated("Use property renderscriptDebuggable")
    fun isRenderscriptDebuggable(): Boolean

    @Deprecated("Use property zipAlignEnabled")
    fun isZipAlignEnabled(): Boolean
}
