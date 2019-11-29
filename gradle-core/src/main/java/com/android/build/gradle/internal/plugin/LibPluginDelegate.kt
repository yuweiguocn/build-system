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

package com.android.build.gradle.internal.plugin

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.extensions.BuildPropertiesImpl
import com.android.build.gradle.internal.api.dsl.extensions.EmbeddedTestPropertiesImpl
import com.android.build.gradle.internal.api.dsl.extensions.LibraryExtensionImpl
import com.android.build.gradle.internal.api.dsl.extensions.OnDeviceTestPropertiesImpl
import com.android.build.gradle.internal.api.dsl.extensions.VariantAwarePropertiesImpl
import com.android.build.gradle.internal.api.dsl.extensions.VariantOrExtensionPropertiesImpl
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.variant2.LibAndroidTestVariantFactory
import com.android.build.gradle.internal.variant2.LibUnitTestVariantFactory
import com.android.build.gradle.internal.variant2.LibraryVariantFactory
import com.android.builder.core.BuilderConstants
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.plugins.ExtensionContainer

class LibPluginDelegate : TypedPluginDelegate<LibraryExtensionImpl> {

    override fun getVariantFactories() = listOf(
            LibraryVariantFactory(),
            LibAndroidTestVariantFactory(),
            LibUnitTestVariantFactory())

    override fun createNewExtension(
            extensionContainer: ExtensionContainer,
            buildProperties: BuildPropertiesImpl,
            variantExtensionProperties: VariantOrExtensionPropertiesImpl,
            variantAwareProperties: VariantAwarePropertiesImpl,
            dslScope: DslScope): LibraryExtensionImpl {
        return extensionContainer
                .create(
                        "android",
                        LibraryExtensionImpl::class.java,
                        buildProperties,
                        variantExtensionProperties,
                        variantAwareProperties,
                        EmbeddedTestPropertiesImpl(dslScope),
                        OnDeviceTestPropertiesImpl(dslScope),
                        dslScope)
    }

    override fun createDefaults(extension: LibraryExtensionImpl) {
        val signingConfig = extension.signingConfigs.create(BuilderConstants.DEBUG)
        val debug = extension.buildTypes.create(BuilderConstants.DEBUG)
        debug.signingConfig = signingConfig
        debug.debuggable = true
        debug.jniDebuggable = true
        debug.renderscriptDebuggable = true
        debug.embedMicroApp = false
        debug.crunchPngs = false

        extension.buildTypes.create(BuilderConstants.RELEASE)
    }
}