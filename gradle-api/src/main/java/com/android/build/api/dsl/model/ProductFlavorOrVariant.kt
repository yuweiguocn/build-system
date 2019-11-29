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

import com.android.build.api.dsl.ApiVersion
import com.android.build.api.dsl.options.InstrumentationOptions
import com.android.build.api.dsl.options.VectorDrawablesOptions
import org.gradle.api.Action
import org.gradle.api.Incubating

/** properties common to product flavors and variants.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface ProductFlavorOrVariant {

    /**
     * The application ID.
     *
     * See [Set the Application ID](https://developer.android.com/studio/build/application-id.html)
     */
    var applicationId: String?

    /**
     * Version code.
     *
     * See [Versioning Your Application](http://developer.android.com/tools/publishing/versioning.html)
     */
    var versionCode: Int?

    /**
     * Version name.
     *
     * See [Versioning Your Application](http://developer.android.com/tools/publishing/versioning.html)
     */
    var versionName: String?

    /**
     * Returns the minSdkVersion. This is only the value set on this product flavor.
     *
     * @return the minSdkVersion, or null if not specified
     */
    var minSdkVersion: ApiVersion?

    /**
     * Sets minimum SDK version using an integer.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    fun setMinSdkVersion(minSdkVersion: Int)

    /**
     * Sets minimum SDK version using an integer.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    fun minSdkVersion(minSdkVersion: Int)

    /**
     * Sets minimum SDK version with a string
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    fun setMinSdkVersion(minSdkVersion: String)

    /**
     * Sets minimum SDK version.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    fun minSdkVersion(minSdkVersion: String)

    /**
     * Returns the targetSdkVersion. This is only the value set on this product flavor.
     *
     * @return the targetSdkVersion, or null if not specified
     */
    var targetSdkVersion: ApiVersion?

    fun setTargetSdkVersion(targetSdkVersion: Int)

    /**
     * Sets the target SDK version using an integer
     *
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    fun targetSdkVersion(targetSdkVersion: Int)

    fun setTargetSdkVersion(targetSdkVersion: String)

    /**
     * Sets the target SDK version to the given value.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    fun targetSdkVersion(targetSdkVersion: String)

    /**
     * Returns the maxSdkVersion. This is only the value set on this produce flavor.
     *
     * @return the maxSdkVersion, or null if not specified
     */
    var maxSdkVersion: Int?

    /**
     * Adds several resource configuration filters.
     *
     *
     * If a qualifier value is passed, then all other resources using a qualifier of the same
     * type but of different value will be ignored from the final packaging of the APK.
     *
     *
     * For instance, specifying 'hdpi', will ignore all resources using mdpi, xhdpi, etc...
     *
     *
     * To package only the localization languages your app includes as string resources, specify
     * 'auto'. For example, if your app includes string resources for 'values-en' and 'values-fr',
     * and its dependencies provide 'values-en' and 'values-ja', Gradle packages only the
     * 'values-en' and 'values-fr' resources from the app and its dependencies. Gradle does not
     * package 'values-ja' resources in the final APK.
     */
    var resConfigs: MutableList<String>

    /**
     * Returns the renderscript target api. This is only the value set on this product flavor. TODO:
     * make final renderscript target api available through the model
     *
     * @return the renderscript target api, or null if not specified
     */
    /** Sets the renderscript target API to the given value.  */
    var renderscriptTargetApi: Int?

    /**
     * Returns whether the renderscript code should be compiled in support mode to make it
     * compatible with older versions of Android.
     *
     * @return true if support mode is enabled, false if not, and null if not specified.
     */
    /**
     * Sets whether the renderscript code should be compiled in support mode to make it compatible
     * with older versions of Android.
     */
    var renderscriptSupportModeEnabled: Boolean?

    /**
     * Returns whether the renderscript BLAS support lib should be used to make it compatible with
     * older versions of Android.
     *
     * @return true if BLAS support lib is enabled, false if not, and null if not specified.
     */
    /**
     * Sets whether RenderScript BLAS support lib should be used to make it compatible with older
     * versions of Android.
     */
    var renderscriptSupportModeBlasEnabled: Boolean?

    /**
     * Returns whether the renderscript code should be compiled to generate C/C++ bindings.
     *
     * @return true for C/C++ generation, false for Java, null if not specified.
     */
    /** Sets whether the renderscript code should be compiled to generate C/C++ bindings.  */
    var renderscriptNdkModeEnabled: Boolean?

    /** Options to configure the build-time support for `vector` drawables.  */
    val vectorDrawables: VectorDrawablesOptions

    fun vectorDrawables(action: Action<VectorDrawablesOptions>)

    val instrumentationOptions: InstrumentationOptions

    fun instrumentationOptions(action: Action<InstrumentationOptions>)
}
