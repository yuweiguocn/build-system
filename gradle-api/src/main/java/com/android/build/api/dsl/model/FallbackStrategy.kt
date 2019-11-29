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

import org.gradle.api.Incubating

/**
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface FallbackStrategy {

    /**
     * Specifies a sorted list of values that the plugin should try to use when a direct
     * variant match with a local module dependency is not possible.
     *
     * Android plugin 3.0.0 and higher uses
     * [variant-aware dependency management](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware)
     * to match each variant of your module with the same one from its dependencies. For example,
     * when you build a "freeDebug" version of your app, the plugin tries to match it with
     * "freeDebug" versions of the local library modules the app depends on.
     *
     * However, there may be situations in which a direct match is not possible, such as the
     * following:
     *
     * *    __Your app includes build types that a dependency does not:__ For example, consider if
     *      your app includes a "stage" build type, but a dependency includes only a "debug" and
     *      "release" build type. Note that there is no issue when a library dependency includes a
     *      build type that your app does not. That's because the plugin simply never requests that
     *      build type from the dependency.
     *
     * *    For a given [`flavor dimension`][com.android.build.api.dsl.model.ProductFlavor.dimension]
     *      that exists in both the app and its library dependencies, __your app includes flavors
     *      that a dependency does not:__ For example, consider if both your app and its library
     *      dependencies include a "tier" flavor dimension. However, the "tier" dimension in the
     *      app includes "free" and "paid" flavors, but one of its dependencies includes only "demo"
     *      and "paid" flavors for the same dimension. Note that, for a given flavor dimension that
     *      exists in both the app and its library dependencies, there is no issue when a library
     *      includes a product flavor that your app does not. That's because the plugin simply never
     *      requests that flavor from the dependency.
     *
     * *    __A library dependency includes a flavor dimension that your app does not__: See
     *      [missingDimensionStrategy][com.android.build.api.dsl.model.BaseFlavor.missingDimensionStrategy].
     *
     * When the plugin tries to build the "stage" or "free" versions of your app, it won't know
     * which version of the dependency to use, and you'll see an error message similar to the
     * following:
     *
     * ```
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     *     project :app
     * ```
     *
     * To resolve the issues described above, use `matchingFallbacks` to specify
     * alternative matches for the app's "stage" build type and "free" product flavor,
     * as shown below:
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     ...
     *     buildTypes {
     *         release {
     *             // Because the dependency already includes a "release" build type,
     *             // you don't need to provide a list of fallbacks here.
     *          }
     *          stage {
     *              // Specifies a sorted list of fallback build types that the
     *              // plugin should try to use when a dependency does not include a
     *              // "stage" build type. You may specify as many fallbacks as you
     *              // like, and the plugin selects the first build type that's
     *              // available in the dependency.
     *              matchingFallbacks = ['debug', 'qa', 'release']
     *          }
     *      }
     *
     *      flavorDimensions 'tier'
     *      productFlavors {
     *          paid {
     *              dimension 'tier'
     *              // Because the dependency already includes a "paid" flavor in its
     *              // "tier" dimension, you don't need to provide a list of fallbacks
     *              // for the "paid" flavor.
     *          }
     *          free {
     *              dimension 'tier'
     *              // Specifies a sorted list of fallback flavors that the plugin
     *              // should try to use when a dependency's matching dimension does
     *              // not include a "free" flavor. You may specify as many
     *              // fallbacks as you like, and the plugin selects the first flavor
     *              // that's available in the dependency's "tier" dimension.
     *              matchingFallbacks = ['demo', 'trial']
     *          }
     *      }
     * }
     * ```
     *
     * @see [`missingDimensionStrategy`][com.android.build.api.dsl.model.BaseFlavor.missingDimensionStrategy]
     * @return Fallback values the plugin should use for variant-aware dependency resolution.
     * @since 3.0.0
     */
    var matchingFallbacks: MutableList<String>

    @Deprecated("Use matchingFallbacks property")
    fun setMatchingFallbacks(vararg fallbacks: String)

    @Deprecated("Use matchingFallbacks property")
    fun setMatchingFallbacks(fallback: String)
}
