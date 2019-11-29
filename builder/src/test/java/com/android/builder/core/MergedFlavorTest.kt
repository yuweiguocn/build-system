/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.builder.core

import com.android.builder.errors.EvalIssueReporter
import com.android.builder.internal.ClassFieldImpl
import com.android.testutils.internal.CopyOfTester
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MergedFlavorTest {

    private lateinit var defaultFlavor: DefaultProductFlavor
    private lateinit var defaultFlavor2: DefaultProductFlavor
    private lateinit var custom: DefaultProductFlavor
    private lateinit var custom2: DefaultProductFlavor
    private lateinit var issueReporter: EvalIssueReporter

    @Before
    fun setUp() {
        defaultFlavor = DefaultProductFlavor("default")
        defaultFlavor2 = DefaultProductFlavor("default2")

        custom = DefaultProductFlavor("custom")
        custom.minSdkVersion = DefaultApiVersion(42)
        custom.targetSdkVersion = DefaultApiVersion(43)
        custom.renderscriptTargetApi = 17
        custom.versionCode = 44
        custom.versionName = "42.0"
        custom.applicationId = "com.forty.two"
        custom.testApplicationId = "com.forty.two.test"
        custom.testInstrumentationRunner = "com.forty.two.test.Runner"
        custom.setTestHandleProfiling(true)
        custom.setTestFunctionalTest(true)
        custom.addResourceConfiguration("hdpi")
        custom.addManifestPlaceholders(
                ImmutableMap.of<String, Any>("one", "oneValue", "two", "twoValue"))
        custom.addResValue(ClassFieldImpl("foo", "one", "oneValue"))
        custom.addResValue(ClassFieldImpl("foo", "two", "twoValue"))
        custom.addBuildConfigField(ClassFieldImpl("foo", "one", "oneValue"))
        custom.addBuildConfigField(ClassFieldImpl("foo", "two", "twoValue"))
        custom.versionNameSuffix = "custom"
        custom.applicationIdSuffix = "custom"

        custom2 = DefaultProductFlavor("custom2")
        custom2.addResourceConfigurations("ldpi", "hdpi")
        custom2.addManifestPlaceholders(
                ImmutableMap.of<String, Any>("two", "twoValueBis", "three", "threeValue"))
        custom2.addResValue(ClassFieldImpl("foo", "two", "twoValueBis"))
        custom2.addResValue(ClassFieldImpl("foo", "three", "threeValue"))
        custom2.addBuildConfigField(ClassFieldImpl("foo", "two", "twoValueBis"))
        custom2.addBuildConfigField(ClassFieldImpl("foo", "three", "threeValue"))
        custom2.applicationIdSuffix = "custom2"
        custom2.versionNameSuffix = "custom2"
        custom2.applicationId = "com.custom2.app"

        issueReporter = ThrowingIssueReporter()
    }

    @Test
    fun testClone() {
        val flavor = MergedFlavor.clone(custom, issueReporter)
        assertThat(flavor.toString().substringAfter("{"))
                .isEqualTo(custom.toString().substringAfter("{"))

        CopyOfTester
                .assertAllGettersCalled(
                        DefaultProductFlavor::class.java, custom,
                        { MergedFlavor.clone(it, issueReporter) })
    }

    @Test
    fun testMergeOnDefault() {
        val flavor =
                MergedFlavor.mergeFlavors(defaultFlavor, ImmutableList.of(custom), issueReporter)

        assertThat(flavor.minSdkVersion?.apiLevel).isEqualTo(42)
        assertThat(flavor.targetSdkVersion?.apiLevel).isEqualTo(43)
        assertThat(flavor.renderscriptTargetApi).isEqualTo(17)
        assertThat(flavor.versionCode).isEqualTo(44)
        assertThat(flavor.versionName).isEqualTo("42.0")
        assertThat(flavor.applicationId).isEqualTo("com.forty.two")
        assertThat(flavor.testApplicationId).isEqualTo("com.forty.two.test")
        assertThat(flavor.testInstrumentationRunner).isEqualTo("com.forty.two.test.Runner")
        assertThat(flavor.testHandleProfiling).isTrue()
        assertThat(flavor.testFunctionalTest).isTrue()
    }

    @Test
    fun testMergeOnCustom() {
        val flavor =
                MergedFlavor.mergeFlavors(custom, ImmutableList.of(defaultFlavor), issueReporter)

        assertThat(flavor.minSdkVersion?.apiLevel).isEqualTo(42)
        assertThat(flavor.targetSdkVersion?.apiLevel).isEqualTo(43)
        assertThat(flavor.renderscriptTargetApi).isEqualTo(17)
        assertThat(flavor.versionCode).isEqualTo(44)
        assertThat(flavor.versionName).isEqualTo("42.0")
        assertThat(flavor.applicationId).isEqualTo("com.forty.two")
        assertThat(flavor.testApplicationId).isEqualTo("com.forty.two.test")
        assertThat(flavor.testInstrumentationRunner).isEqualTo("com.forty.two.test.Runner")
        assertThat(flavor.testHandleProfiling).isTrue()
        assertThat(flavor.testFunctionalTest).isTrue()
    }

    @Test
    fun testMergeDefaultOnDefault() {
        val flavor =
                MergedFlavor
                        .mergeFlavors(
                                defaultFlavor2, ImmutableList.of(defaultFlavor), issueReporter)

        assertThat(flavor.minSdkVersion).isNull()
        assertThat(flavor.targetSdkVersion).isNull()
        assertThat(flavor.renderscriptTargetApi).isNull()
        assertThat(flavor.versionCode).isNull()
        assertThat(flavor.versionName).isNull()
        assertThat(flavor.applicationId).isNull()
        assertThat(flavor.testApplicationId).isNull()
        assertThat(flavor.testInstrumentationRunner).isNull()
        assertThat(flavor.testHandleProfiling).isNull()
        assertThat(flavor.testFunctionalTest).isNull()
    }

    @Test
    fun testResourceConfigMerge() {
        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), issueReporter)

        val configs = flavor.resourceConfigurations
        assertThat(configs).containsExactly("hdpi", "ldpi")
    }

    @Test
    fun testManifestPlaceholdersMerge() {
        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), issueReporter)

        val manifestPlaceholders = flavor.manifestPlaceholders
        assertThat(manifestPlaceholders)
                .containsExactly("one", "oneValue", "two", "twoValue", "three", "threeValue")
    }

    @Test
    fun testResValuesMerge() {
        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), issueReporter)

        val resValues = flavor.resValues
        assertThat(resValues).hasSize(3)
        assertThat(resValues["one"]?.value).isEqualTo("oneValue")
        assertThat(resValues["two"]?.value).isEqualTo("twoValue")
        assertThat(resValues["three"]?.value).isEqualTo("threeValue")
    }

    @Test
    fun testBuildConfigFieldMerge() {
        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), issueReporter)

        val buildConfigFields = flavor.buildConfigFields
        assertThat(buildConfigFields).hasSize(3)
        assertThat(buildConfigFields["one"]?.value).isEqualTo("oneValue")
        assertThat(buildConfigFields["two"]?.value).isEqualTo("twoValue")
        assertThat(buildConfigFields["three"]?.value).isEqualTo("threeValue")
    }

    @Test
    fun testMergeMultiple() {
        val custom3 = DefaultProductFlavor("custom3")
        custom3.minSdkVersion = DefaultApiVersion(102)
        custom3.applicationIdSuffix = "custom3"
        custom3.versionNameSuffix = "custom3"

        val flavor =
                MergedFlavor.mergeFlavors(custom, ImmutableList.of(custom3, custom2), issueReporter)

        assertThat(flavor.minSdkVersion).isEqualTo(DefaultApiVersion(102))
        assertThat(flavor.versionNameSuffix).isEqualTo("customcustom3custom2")
        assertThat(flavor.applicationIdSuffix).isEqualTo("custom.custom3.custom2")
    }

    @Test
    fun testSecondDimensionOverwritesDefault() {
        val custom3 = DefaultProductFlavor("custom3")
        custom3.minSdkVersion = DefaultApiVersion(102)

        val flavor =
                MergedFlavor.mergeFlavors(custom, ImmutableList.of(custom3, custom2), issueReporter)
        assertThat(flavor.minSdkVersion).isEqualTo(DefaultApiVersion(102))
        assertThat(flavor.applicationId).isEqualTo("com.custom2.app")
        assertThat(flavor.versionNameSuffix).isEqualTo("customcustom2")
        assertThat(flavor.applicationIdSuffix).isEqualTo("custom.custom2")
    }

    @Test
    fun testSetVersionCodeError() {
        val flavor = MergedFlavor.clone(defaultFlavor, issueReporter)
        try {
            flavor.versionCode = 123
            fail("Setting versionCode should result in RuntimeException from issueReporter")
        } catch (e : RuntimeException) {
            assertThat(e.message).isEqualTo("fake")
        }
    }

    @Test
    fun testSetVersionNameError() {
        val flavor = MergedFlavor.clone(defaultFlavor, issueReporter)
        try {
            flavor.versionName = "foo"
            fail("Setting versionName should result in RuntimeException from issueReporter")
        } catch (e : RuntimeException) {
            assertThat(e.message).isEqualTo("fake")
        }
    }
}
