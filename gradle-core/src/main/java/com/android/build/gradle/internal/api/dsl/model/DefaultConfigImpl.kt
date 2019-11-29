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

package com.android.build.gradle.internal.api.dsl.model

import com.android.build.api.dsl.model.BaseFlavor
import com.android.build.api.dsl.model.BuildTypeOrProductFlavor
import com.android.build.api.dsl.model.DefaultConfig
import com.android.build.api.dsl.model.FallbackStrategy
import com.android.build.api.dsl.model.ProductFlavorOrVariant
import com.android.build.api.dsl.model.VariantProperties
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject

class DefaultConfigImpl(
            private val variantProperties: VariantPropertiesImpl,
            private val buildTypeOrProductFlavor: BuildTypeOrProductFlavorImpl,
            private val productFlavorOrVariant: ProductFlavorOrVariantImpl,
            private val baseFlavor: BaseFlavorImpl,
            dslScope: DslScope)
        : SealableObject(dslScope),
        DefaultConfig,
        VariantProperties by variantProperties,
        BuildTypeOrProductFlavor by buildTypeOrProductFlavor,
        ProductFlavorOrVariant by productFlavorOrVariant,
        BaseFlavor by baseFlavor {

    override fun seal() {
        super.seal()

        variantProperties.seal()
        buildTypeOrProductFlavor.seal()
        productFlavorOrVariant.seal()
        baseFlavor.seal()
    }
}