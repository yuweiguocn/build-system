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

package com.android.build.gradle.internal.variant2

import com.android.build.api.dsl.model.ProductFlavorOrVariant
import com.android.build.api.dsl.variant.Variant
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.extensions.VariantOrExtensionPropertiesImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.variant.CommonVariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.variant.SealableVariant
import com.android.builder.core.VariantType
import com.android.builder.errors.EvalIssueReporter

interface VariantFactory2<in E: BaseExtension2> {

    val generatedType: VariantType
    val testedBy: List<VariantType>
    val testTarget: VariantType?

    fun createVariant(
            extension: E,
            variantProperties: VariantPropertiesImpl,
            productFlavorOrVariant: ProductFlavorOrVariantImpl,
            buildTypOrVariant: BuildTypeOrVariantImpl,
            variantExtensionProperties: VariantOrExtensionPropertiesImpl,
            commonVariantProperties: CommonVariantPropertiesImpl,
            variantDispatcher: VariantDispatcher,
            dslScope: DslScope
    ): SealableVariant

    fun computeApplicationId(mergedFlavor: ProductFlavorOrVariant, appIdSuffixFromFlavors: String?): String?
}
