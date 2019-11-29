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

import com.android.build.api.dsl.extension.ApkProperties
import com.android.build.api.dsl.extension.BuildProperties
import com.android.build.api.dsl.extension.EmbeddedTestProperties
import com.android.build.api.dsl.extension.OnDeviceTestProperties
import com.android.build.api.dsl.extension.TestExtension
import com.android.build.api.dsl.extension.VariantAwareProperties
import com.android.build.api.dsl.extension.VariantOrExtensionProperties
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import javax.inject.Inject

open class TestExtensionImpl @Inject constructor(
            private val buildProperties: BuildPropertiesImpl,
            override val variantExtensionProperties: VariantOrExtensionPropertiesImpl,
            private val variantAwareProperties: VariantAwarePropertiesImpl,
            private val apkPropertiesImpl: ApkPropertiesImpl,
            private val embeddedTestProperties: EmbeddedTestPropertiesImpl,
            private val onDeviceTestProperties: OnDeviceTestPropertiesImpl,
            dslScope: DslScope)
        : SealableObject(dslScope),
        TestExtension,
        BaseExtension2,
        BuildProperties by buildProperties,
        VariantOrExtensionProperties by variantExtensionProperties,
        VariantAwareProperties by variantAwareProperties,
        ApkProperties by apkPropertiesImpl,
        EmbeddedTestProperties by embeddedTestProperties,
        OnDeviceTestProperties by onDeviceTestProperties {

    override fun seal() {
        super.seal()

        buildProperties.seal()
        variantExtensionProperties.seal()
        variantAwareProperties.seal()
        apkPropertiesImpl.seal()
        embeddedTestProperties.seal()
        onDeviceTestProperties.seal()
    }

    override var targetProjectPath: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    @Suppress("OverridingDeprecatedMember")
    override var targetVariant: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }
}