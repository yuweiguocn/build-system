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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.extension.VariantAwareProperties
import com.android.build.api.dsl.extension.VariantCallbackHandler
import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.DefaultConfig
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.dsl.variant.Variant
import com.android.build.api.dsl.variant.VariantFilter
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.variant2.DslModelData
import com.android.build.gradle.internal.variant2.VariantCallbackHolder
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

class VariantAwarePropertiesImpl(
            private val dslModelData: DslModelData,
            variantCallbackHolder: VariantCallbackHolder,
            dslScope: DslScope)
        : SealableObject(dslScope),
        VariantAwareProperties,
        DefaultConfig by dslModelData.defaultConfig {

    override val productFlavors
        get() = dslModelData.productFlavors
    override val buildTypes
        get() = dslModelData.buildTypes
    override val signingConfigs
        get() = dslModelData.signingConfigs

    private val _flavorDimensions: SealableList<String> = SealableList.new(dslScope)
    private val _variantFilters: SealableList<Action<VariantFilter>> = SealableList.new(dslScope)
    private val _preVariants: SealableList<Action<Void>> = SealableList.new(dslScope)
    private val _postVariants: SealableList<Action<Collection<Variant>>> = SealableList.new(dslScope)

    override val variants: VariantCallbackHandler<Variant> =
            variantCallbackHolder.createVariantCallbackHandler()

    override var variantFilters:MutableList<Action<VariantFilter>>
        get() = _variantFilters
        set(value) {
            _variantFilters.reset(value)
        }

    override var flavorDimensions: MutableList<String>
        get() = _flavorDimensions
        set(value) {
            _flavorDimensions.reset(value)
        }

    override fun buildTypes(action: Action<NamedDomainObjectContainer<BuildType>>) {
        action.execute(buildTypes)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>) {
        action.execute(productFlavors)
    }

    override fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>) {
        action.execute(signingConfigs)
    }

    override var preVariantCallbacks: MutableList<Action<Void>>
        get() = _preVariants
        set(value) {
            _preVariants.reset(value)
        }

    override fun preVariantCallback(action: Action<Void>) {
        if (checkSeal()) {
            _preVariants.add(action)
        }
    }

    override fun variantFilter(action: Action<VariantFilter>) {
        if (checkSeal()) {
            _variantFilters.add(action)
        }
    }

    override var postVariants: MutableList<Action<Collection<Variant>>>
        get() = _postVariants
        set(value) {
            _postVariants.reset(value)
        }

    override fun postVariantCallback(action: Action<Collection<Variant>>) {
        if (checkSeal()) {
            _postVariants.add(action)
        }
    }

    override fun seal() {
        super.seal()
        _flavorDimensions.seal()
        _preVariants.seal()
        _variantFilters.seal()
        _postVariants.seal()
    }

    // DEPRECATED

    @Suppress("OverridingDeprecatedMember")
    override var flavorDimensionList: MutableList<String>
        get() {
            dslScope.deprecationReporter.reportDeprecatedUsage(
                    "android.flavorDimensions",
                    "android.flavorDimensionList",
                    DeprecationReporter.DeprecationTarget.OLD_DSL)
            return flavorDimensions
        }
        set(value) {
            dslScope.deprecationReporter.reportDeprecatedUsage(
                    "android.flavorDimensions",
                    "android.flavorDimensionList",
                    DeprecationReporter.DeprecationTarget.OLD_DSL)
            flavorDimensions = value
        }

    @Suppress("OverridingDeprecatedMember")
    override val defaultConfig: DefaultConfig
        get() {
            dslScope.deprecationReporter.reportDeprecatedUsage(
                    "android",
                    "android.defaultConfig",
                    DeprecationReporter.DeprecationTarget.OLD_DSL)
            return this
        }

    @Suppress("OverridingDeprecatedMember")
    override fun defaultConfig(action: Action<DefaultConfig>) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "android",
                "android.defaultConfig",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        action.execute(this)
    }
}