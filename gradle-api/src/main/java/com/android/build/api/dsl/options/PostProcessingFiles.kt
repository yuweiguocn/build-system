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

package com.android.build.api.dsl.options

import org.gradle.api.Incubating

/** DSL object for configuring postProcessing: removing dead code, obfuscating etc.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface PostProcessingFiles {

    /**
     * Specifies the ProGuard configuration files that the plugin should use.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     * *    `proguard-android.txt`
     * *    `proguard-android-optimize.txt`
     *
     * The following sample uses a ProGuard rules file to enable code shrinking for the release
     * build type:
     *
     * ```
     * android {
     *     buildTypes {
     *         release {
     *             minifyEnabled true
     *             proguardFiles getDefaultProguardFile('proguard-android.txt')
     *         }
     *     }
     * }
     * ```
     *
     * When you build your project, the plugin creates a copy of the settings file in
     * `project/build/intermediates/proguard-files/`. For even more code shrinking, try the
     * `proguard-android-optimize.txt` file located in the same directory. It includes the same
     * default ProGuard rules, but with other optimizations that perform analysis at the bytecode
     * level—inside and across methods—to reduce your APK size further and help it run faster.
     *
     * To add ProGuard rules that are specific to each build variant, add another `proguardFiles`
     * property in the corresponding [productFlavor][com.android.build.api.dsl.model.ProductFlavor]
     * block. For example, the following Gradle file adds `flavor2-rules.pro` to the flavor2 product
     * flavor. Now flavor2 uses both ProGuard rules files because those from the release block are
     * also applied.
     *
     * ```
     * android {
     *     buildTypes {
     *         release {...}
     *     }
     *     productFlavors {
     *         flavor1 {}
     *         flavor2 {
     *             proguardFile 'flavor2-rules.pro'
     *         }
     *     }
     * }
     * ```
     *
     * To learn more about how to remove unused code and resources from your APK, read
     * [Shrink Your Code and Resources](https://developer.android.com/studio/build/shrink-code.html).
     *
     * @return A non-null collection of files.
     * @see testProguardFiles
     */
    var proguardFiles: MutableList<Any>

    /**
     * Specifies proguard rule files that you want to use when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    var testProguardFiles: MutableList<Any>

    /**
     * Specifies proguard rule files to be included in the published AAR.
     *
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    var consumerProguardFiles: MutableList<Any>
}
