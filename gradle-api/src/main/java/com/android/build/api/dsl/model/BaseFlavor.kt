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

import com.android.build.api.dsl.options.PostProcessingFilesOptions
import org.gradle.api.Action
import org.gradle.api.Incubating

/** Base DSL object used to configure product flavors.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface BaseFlavor {

    /**
     * Returns whether to enable unbundling mode for embedded wear app.
     *
     *
     * If true, this enables the app to transition from an embedded wear app to one distributed
     * by the play store directly.
     *
     * @return a boolean or null if the value is not set in this flavor
     */
    /**
     * Sets whether to enable unbundling mode for embedded wear app.
     *
     *
     * If true, this enables the app to transition from an embedded wear app to one distributed
     * by the play store directly.
     */
    var wearAppUnbundled: Boolean?

    /**
     * @suppress
     * @see [`missingDimensionStrategy`][com.android.build.api.dsl.model.BaseFlavor.missingDimensionStrategy]
     *
     * @param dimension The dimension for which you are providing a new matching strategy.
     * @param requestedValue The flavor that the dimension should request when resolving
     *                       dependencies.
     *
     * @since 3.0.0
     */
    fun missingDimensionStrategy(dimension: String, requestedValue: String)

    /**
     * @suppress
     * @see [`missingDimensionStrategy`][com.android.build.api.dsl.model.BaseFlavor.missingDimensionStrategy]
     *
     * @param dimension The dimension for which you are providing a new matching strategy.
     * @param requestedValues The flavor(s) that the dimension should request when resolving
     *                       dependencies, in order of descending priority.
     *
     * @since 3.0.0
     */
    fun missingDimensionStrategy(dimension: String, vararg requestedValues: String)

    /**
     * Specifies a sorted list of flavors that the plugin should try to use from a given dimension
     * in a dependency.
     *
     * Android plugin 3.0.0 and higher uses
     * [variant-aware dependency management](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware)
     * to match each variant of your module with the same one from its dependencies. For example,
     * consider if both your app and its dependencies include a
     * "tier" [flavor dimension](https://developer.android.com/studio/build/build-variants.html#flavor-dimensions),
     * with flavors "free" and "paid". When you build a "freeDebug" version of your app, the plugin
     * tries to match it with "freeDebug" versions of the local library modules the app depends on.
     *
     * However, there may be situations in which __a library dependency includes a flavor
     * dimension that your app does not__. For example, consider if a library dependency includes
     * flavors for a "minApi" dimension, but your app includes flavors for only the "tier"
     * dimension. So, when you want to build the "freeDebug" version of your app, the plugin doesn't
     * know whether to use the "minApi23Debug" or "minApi18Debug" version of the dependency, and
     * you'll see an error message similar to the following:
     *
     * ```no-pretty-print
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     *     project :app
     * ```
     *
     * In this type of situation, use `missingDimensionStrategy` in the
     * [`defaultConfig`][com.android.build.api.dsl.model.DefaultConfig] block to specify the default
     * flavor the plugin should select from each missing dimension, as shown in the sample below.
     * You can also override your selection in the
     * [`productFlavors`][com.android.build.api.dsl.model.ProductFlavor] block, so that each flavor
     * can specify a different matching strategy for a missing dimension.
     *
     * __Tip:__ you can also use this property if you simply want to change the matching strategy
     * for a dimension that exists in both the app and its dependencies.
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     defaultConfig{
     *     // Specifies a sorted list of flavors that the plugin should try to use from
     *     // a given dimension. The following tells the plugin that, when encountering
     *     // a dependency that includes a "minApi" dimension, it should select the
     *     // "minApi18" flavor. You can include additional flavor names to provide a
     *     // sorted list of fallbacks for the dimension.
     *     missingDimensionStrategy 'minApi', 'minApi18', 'minApi23'
     *     // You should specify a missingDimensionStrategy property for each
     *     // dimension that exists in a local dependency but not in your app.
     *     missingDimensionStrategy 'abi', 'x86', 'arm64'
     *     }
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         free {
     *             dimension 'tier'
     *             // You can override the default selection at the product flavor
     *             // level by configuring another missingDimensionStrategy property
     *             // for the "minApi" dimension.
     *             missingDimensionStrategy 'minApi', 'minApi23', 'minApi18'
     *         }
     *         paid {}
     *     }
     * }
     * ```
     *
     * @param dimension The dimension for which you are providing a new matching strategy.
     * @param requestedValues The flavor(s) that the dimension should request when resolving
     *                       dependencies, in order of descending priority.
     *
     * @see [`matchingFallbacks`][com.android.build.api.dsl.model.FallbackStrategy.matchingFallbacks]
     * @since 3.0.0
     */
    fun missingDimensionStrategy(dimension: String, requestedValues: List<String>)

    /** Configures the post-processing options with the given action.  */
    fun postProcessing(action: Action<PostProcessingFilesOptions>)

    /** Returns the post-processing option  */
    val postProcessing: PostProcessingFilesOptions

    // --- DEPRECATED

    /**
     * @see .getVectorDrawables
     */
    @Deprecated("Use {@link VectorDrawablesOptions#getGeneratedDensities()}\n      ")
    val generatedDensities: Set<String>?

    /**
     * @see .getVectorDrawables
     * @see .vectorDrawables
     */
    @Deprecated("Use {@link VectorDrawablesOptions#setGeneratedDensities(Iterable)}\n      ")
    fun setGeneratedDensities(densities: Iterable<String>?)

    @Deprecated("Use {@link #setWearAppUnbundled(Boolean)} ")
    fun wearAppUnbundled(wearAppUnbundled: Boolean?)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#getApplicationId()}\n      ")
    var testApplicationId: String?

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#instrumentationRunnerArgument(String, String)}\n      ")
    fun testInstrumentationRunnerArgument(key: String, value: String)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#addInstrumentationRunnerArguments(Map)}\n      ")
    fun testInstrumentationRunnerArguments(args: Map<String, String>)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setInstrumentationRunner(String)}\n      ")
    var testInstrumentationRunner: String?

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setInstrumentationRunnerArguments(Map)}\n      ")
    var testInstrumentationRunnerArguments: Map<String, String>

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setHandleProfiling(boolean)}\n      ")
    fun setTestHandleProfiling(handleProfiling: Boolean)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#getHandleProfiling()}\n      ")
    val testHandleProfiling: Boolean?

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setFunctionalTest(boolean)}\n      ")
    fun setTestFunctionalTest(functionalTest: Boolean)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#getFunctionalTest()}\n      ")
    val testFunctionalTest: Boolean?
}
