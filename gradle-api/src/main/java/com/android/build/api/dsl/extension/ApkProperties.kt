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

import com.android.build.api.dsl.options.PackagingOptions
import com.android.build.api.dsl.options.Splits
import org.gradle.api.Action
import org.gradle.api.Incubating

/** Partial extension properties for modules that generate APKs
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface ApkProperties {

    /** Packaging options.  */
    val packagingOptions: PackagingOptions

    fun packagingOptions(action: Action<PackagingOptions>)

    /**
     * APK splits options.
     *
     *
     * See [APK Splits](https://developer.android.com/studio/build/configure-apk-splits.html).
     */
    val splits: Splits

    fun splits(action: Action<Splits>)

    /**
     * Specifies whether to build APK splits or multiple APKs from configurations in the
     * [splits][com.android.build.api.dsl.options.Splits] block.
     *
     * Generating APK splits is an incubating feature, which requires you to set
     * [`minSdkVersion`][com.android.build.api.dsl.model.ProductFlavorOrVariant.minSdkVersion] to
     * `21` or higher, and is currently supported only when publishing
     * [Android Instant Apps](https://d.android.com/instant-apps).
     *
     * When you set this property to `true`, the Android plugin generates each object
     * in the `splits` block as a portion of a whole APK, called a _Configuration APK_.
     * Compared to [building multiple APKs](https://d.android.com/studio/build/configure-apk-splits.html),
     * configuration APKs do not represent stand-alone versions of your app. That is, devices need
     * to download both a base APK (that includes your app's device-agnostic code and resources)
     * and additional device-specific configuration APKs from the Google Play Store to run your
     * instant app.
     *
     * When you do not configure this property or set it to `false` (default), the
     * Android plugin builds separate stand-alone APKs for each object you configure in the `splits`
     * block, which you can deploy to a device. To learn more about building device-specific Android
     * Instant App artifacts, read
     * [Set up your build for configuration APKs](https://d.android.com/topic/instant-apps/guides/config-splits.html).
     */
    var generatePureSplits: Boolean
}
