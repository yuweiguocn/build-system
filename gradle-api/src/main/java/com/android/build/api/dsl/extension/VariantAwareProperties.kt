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

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.DefaultConfig
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.dsl.variant.Variant
import com.android.build.api.dsl.variant.VariantFilter
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer

/** Partial extension properties for modules that have variants made of [ProductFlavor] and
 * [BuildType]
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface VariantAwareProperties : DefaultConfig {

    /** Build types used by this project.  */
    val buildTypes: NamedDomainObjectContainer<BuildType>

    fun buildTypes(action: Action<NamedDomainObjectContainer<BuildType>>)

    /** List of flavor dimensions.  */
    var flavorDimensions: MutableList<String>

    /** All product flavors used by this project.  */
    val productFlavors: NamedDomainObjectContainer<ProductFlavor>

    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>)

    /** Signing configs used by this project.  */
    val signingConfigs: NamedDomainObjectContainer<SigningConfig>

    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>)

    /**
     * Specifies variants the Android plugin should include or remove from your Gradle project.
     *
     * By default, the Android plugin creates a build variant for every possible combination of
     * the product flavors and build types that you configure, and adds them to your Gradle project.
     * However, there may be certain build variants that either you do not need or do not make sense
     * in the context of your project. You can remove certain build variant configurations by
     * [creating a variant filter](https://d.android.com/studio/build/build-variants.html#filter-variants)
     * in your module-level `build.gradle` file.
     *
     * The following example tells the plugin to ignore all variants that combine the "dev"
     * product flavor, which you can configure to
     * [optimize build speeds](https://d.android.com/studio/build/optimize-your-build.html#create_dev_variant)
     * during development, and the "release" build type:
     *
     * ```
     * android {
     *     ...
     *     variantFilter { variant ->
     *
     *         def buildTypeName = variant.buildType*.name
     *         def flavorName = variant.flavors*.name
     *
     *         if (flavorName.contains("dev") && buildTypeName.contains("release")) {
     *             // Tells Gradle to ignore each variant that satisfies the conditions above.
     *             setIgnore(true)
     *         }
     *     }
     * }
     * ```
     *
     * During subsequent builds, Gradle ignores any build variants that meet the conditions you
     * specify. If you're using [Android Studio](https://d.android.com/studio/index.html), those
     * variants no longer appear in the drop down menu when you click
     * __Build > Select Build Variant__ from the menu bar.
     *
     * @see com.android.build.api.variant.VariantFilter
     */
    var variantFilters: MutableList<Action<VariantFilter>>

    fun variantFilter(action: Action<VariantFilter>)

    /** pre variant callbacks  */
    var preVariantCallbacks: MutableList<Action<Void>>
    /** register a pre variant callbacks  */
    fun preVariantCallback(action: Action<Void>)

    /** Callback object to register callbacks for variants */
    val variants: VariantCallbackHandler<Variant>

    /** post variant callbacks  */
    var postVariants: MutableList<Action<Collection<Variant>>>

    /** register a post-variant callback */
    fun postVariantCallback(action: Action<Collection<Variant>>)

    @Deprecated("Use flavorDimensions")
    /**
     * @suppress
     * @see [flavorDimensions]
     * */
    var flavorDimensionList: MutableList<String>

    /** Default config, shared by all flavors.  */
    @Deprecated("Use properties on extension itself")
    val defaultConfig: DefaultConfig

    @Deprecated("Use properties on extension itself")
    fun defaultConfig(action: Action<DefaultConfig>)

}
