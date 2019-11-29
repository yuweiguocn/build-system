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

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.fixtures.FakeConfigurationContainer
import com.android.build.gradle.internal.fixtures.FakeContainerFactory
import com.android.build.gradle.internal.fixtures.FakeExtensionContainer
import com.android.build.gradle.internal.fixtures.FakeFilesProvider
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.buildTypes
import com.android.build.gradle.internal.fixtures.configure
import com.android.build.gradle.internal.fixtures.getAndroidTestVariant
import com.android.build.gradle.internal.fixtures.getAppVariant
import com.android.build.gradle.internal.fixtures.getLibVariant
import com.android.build.gradle.internal.fixtures.getNamed
import com.android.build.gradle.internal.fixtures.getUnitTestVariant
import com.android.build.gradle.options.ProjectOptions
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth
import org.junit.Test

/** Tests for the [PluginDelegate] */
class PluginDelegateTest {

    private var fakeObjectFactory = FakeObjectFactory()

    @Test
    fun `basic variants for library plugin`() {
        val pluginDelegate = createDelegate(LibPluginDelegate())
        val extension = pluginDelegate.prepareForEvaluation()

        // --- Validate build types
        val debugBuildType = getNamed(extension.buildTypes, "debug")
        Truth.assertThat(debugBuildType).isNotNull()
        validateDebugBuildType(debugBuildType!!, extension.signingConfigs.getByName("debug"))

        val releaseBuildType = getNamed(extension.buildTypes, "release")
        Truth.assertThat(releaseBuildType).isNotNull()
        validateReleaseBuildType(releaseBuildType!!)

        // create variants

        val variants = pluginDelegate.afterEvaluate()
        Truth.assertThat(variants).hasSize(6)

        // debug

        val debugVariant = getLibVariant(variants, "debug")
        // FIXME this part of variantbuilder is not yet supported
        //Truth.assertThat(debugVariant.signingConfig).isSameAs(extension.signingConfigs.getByName("debug"))

        val debugAndroidTest = getAndroidTestVariant(variants, "debugAndroidTest")
        val debugUnitTest = getUnitTestVariant(variants, "debugUnitTest")

        Truth.assertThat(debugVariant.androidTestVariant).isSameAs(debugAndroidTest)
        Truth.assertThat(debugAndroidTest.testedVariant).isSameAs(debugVariant)

        Truth.assertThat(debugVariant.unitTestVariant).isSameAs(debugUnitTest)
        Truth.assertThat(debugUnitTest.testedVariant).isSameAs(debugVariant)

        // release

        val releaseVariant = getLibVariant(variants, "release")
        Truth.assertThat(releaseVariant.signingConfig).isNull()

        val releaseAndroidTest = getAndroidTestVariant(variants, "releaseAndroidTest")
        val releaseUnitTest = getUnitTestVariant(variants, "releaseUnitTest")

        Truth.assertThat(releaseVariant.androidTestVariant).isSameAs(releaseAndroidTest)
        Truth.assertThat(releaseAndroidTest.testedVariant).isSameAs(releaseVariant)

        Truth.assertThat(releaseVariant.unitTestVariant).isSameAs(releaseUnitTest)
        Truth.assertThat(releaseUnitTest.testedVariant).isSameAs(releaseVariant)
    }

    @Test
    fun `basic variants for app plugin`() {
        val pluginDelegate = createDelegate(AppPluginDelegate())
        val extension = pluginDelegate.prepareForEvaluation()

        // --- Validate build types
        val debugBuildType = getNamed(extension.buildTypes, "debug")
        Truth.assertThat(debugBuildType).isNotNull()
        validateDebugBuildType(debugBuildType!!, extension.signingConfigs.getByName("debug"))

        val releaseBuildType = getNamed(extension.buildTypes, "release")
        Truth.assertThat(releaseBuildType).isNotNull()
        validateReleaseBuildType(releaseBuildType!!)

        // create variants
        val variants = pluginDelegate.afterEvaluate()
        Truth.assertThat(variants).hasSize(6)

        // debug

        val debugVariant = getAppVariant(variants, "debug")
        // FIXME this part of variantbuilder is not yet supported
        //Truth.assertThat(debugVariant.signingConfig).isSameAs(extension.signingConfigs.getByName("debug"))

        val debugAndroidTest = getAndroidTestVariant(variants, "debugAndroidTest")
        val debugUnitTest = getUnitTestVariant(variants, "debugUnitTest")

        Truth.assertThat(debugVariant.androidTestVariant).isSameAs(debugAndroidTest)
        Truth.assertThat(debugAndroidTest.testedVariant).isSameAs(debugVariant)

        Truth.assertThat(debugVariant.unitTestVariant).isSameAs(debugUnitTest)
        Truth.assertThat(debugUnitTest.testedVariant).isSameAs(debugVariant)

        // release

        val releaseVariant = getAppVariant(variants, "release")
        Truth.assertThat(releaseVariant.signingConfig).isNull()

        val releaseAndroidTest = getAndroidTestVariant(variants, "releaseAndroidTest")
        val releaseUnitTest = getUnitTestVariant(variants, "releaseUnitTest")

        Truth.assertThat(releaseVariant.androidTestVariant).isSameAs(releaseAndroidTest)
        Truth.assertThat(releaseAndroidTest.testedVariant).isSameAs(releaseVariant)

        Truth.assertThat(releaseVariant.unitTestVariant).isSameAs(releaseUnitTest)
        Truth.assertThat(releaseUnitTest.testedVariant).isSameAs(releaseVariant)
    }

    @Test
    fun `new build type in application`() {
        val pluginDelegate = createDelegate(AppPluginDelegate())
        val extension = pluginDelegate.prepareForEvaluation()

        configure(extension) {
            buildTypes {
                create("custom")
            }
        }

        val variants = pluginDelegate.afterEvaluate()

        Truth.assertThat(variants).hasSize(9)

        // custom

        val customVariant = getAppVariant(variants, "custom")
        Truth.assertThat(customVariant.signingConfig).isNull()

        val customAndroidTest = getAndroidTestVariant(variants, "customAndroidTest")
        val customUnitTest = getUnitTestVariant(variants, "customUnitTest")

        Truth.assertThat(customVariant.androidTestVariant).isSameAs(customAndroidTest)
        Truth.assertThat(customAndroidTest.testedVariant).isSameAs(customVariant)

        Truth.assertThat(customVariant.unitTestVariant).isSameAs(customUnitTest)
        Truth.assertThat(customUnitTest.testedVariant).isSameAs(customVariant)
    }

    private fun <T: BaseExtension2> createDelegate(
            typedDelegate: TypedPluginDelegate<T>): PluginDelegate<T> {

        return PluginDelegate<T>(
                ":fake",
                fakeObjectFactory,
                FakeExtensionContainer(fakeObjectFactory),
                FakeConfigurationContainer(),
                FakeContainerFactory(),
                FakeFilesProvider(),
                FakeLogger(),
                ProjectOptions(ImmutableMap.of()),
                typedDelegate)
    }

    private fun validateDebugBuildType(buildType: BuildType, signingConfig: SigningConfig) {
        Truth.assertThat(buildType.debuggable).named("debuggable").isTrue()
        Truth.assertThat(buildType.jniDebuggable).named("jniDebuggable").isTrue()
        Truth.assertThat(buildType.renderscriptDebuggable).named("renderscriptDebuggable").isTrue()
        Truth.assertThat(buildType.zipAlignEnabled).named("zipAlignEnabled").isTrue()

        Truth.assertThat(buildType.signingConfig).named("signingConfig").isSameAs(signingConfig)
    }

    private fun validateReleaseBuildType(buildType: BuildType) {
        Truth.assertThat(buildType.debuggable).named("debuggable").isFalse()
        Truth.assertThat(buildType.jniDebuggable).named("jniDebuggable").isFalse()
        Truth.assertThat(buildType.renderscriptDebuggable).named("renderscriptDebuggable").isFalse()
        Truth.assertThat(buildType.zipAlignEnabled).named("zipAlignEnabled").isTrue()

        Truth.assertThat(buildType.signingConfig).named("signingConfig").isNull()
    }
}
