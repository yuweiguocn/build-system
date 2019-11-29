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
import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito.`when`

import com.android.testutils.TestResources
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit

class VariantAttributesProviderTest {

    companion object {
        private const val PACKAGE_NAME = "com.android.tests.builder.core"
    }

    @Rule
    @JvmField
    var rule = MockitoJUnit.rule()

    @Mock
    private lateinit var manifestSupplier: ManifestAttributeSupplier

    private lateinit var manifestFile: File

    private lateinit var mergedFlavor: DefaultProductFlavor

    private lateinit var buildType: DefaultBuildType

    private var isTestVariant: Boolean = false

    private val provider: VariantAttributesProvider
        get() = VariantAttributesProvider(
                mergedFlavor,
                buildType,
                isTestVariant,
                manifestSupplier,
                manifestFile,
                "full.name")

    @Before
    @Throws(Exception::class)
    fun before() {
        mergedFlavor = DefaultProductFlavor("flavor")
        buildType = DefaultBuildType("debug")
        `when`(manifestSupplier.`package`).thenReturn(PACKAGE_NAME)
        manifestFile = TestResources.getFile("/testData/core/AndroidManifest.xml")
    }

    @Test
    fun getPackage() {
        val packageName = provider.packageName
        assertThat(packageName).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun testPackageOverrideNone() {
        assertThat(provider.idOverride).isNull()
    }

    @Test
    fun testIdOverrideIdFromFlavor() {
        mergedFlavor.applicationId = "foo.bar"
        assertThat(provider.idOverride).isEqualTo("foo.bar")
    }

    @Test
    fun testPackageOverridePackageFromFlavorWithSuffix() {
        mergedFlavor.applicationId = "foo.bar"
        buildType.applicationIdSuffix = ".fortytwo"

        assertThat(provider.idOverride).isEqualTo("foo.bar.fortytwo")
    }

    @Test
    fun testPackageOverridePackageFromFlavorWithSuffix2() {
        mergedFlavor.applicationId = "foo.bar"
        buildType.applicationIdSuffix = "fortytwo"

        val supplier = provider

        assertThat(supplier.idOverride).isEqualTo("foo.bar.fortytwo")
    }

    @Test
    fun testPackageOverridePackageWithSuffixOnly() {
        buildType.applicationIdSuffix = "fortytwo"

        assertThat(provider.idOverride).isEqualTo("com.android.tests.builder.core.fortytwo")
    }

    @Test
    fun testApplicationIdFromPackageName() {
        assertThat(provider.getApplicationId("")).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun testApplicationIdFromOverride() {
        mergedFlavor.applicationId = "foo.bar"
        assertThat(provider.getApplicationId("")).isEqualTo("foo.bar")
    }

    @Test
    fun testApplicationIdWithTestVariant() {
        isTestVariant = true
        mergedFlavor.testApplicationId = "foo.bar.test"

        assertThat(provider.getApplicationId("foo.tested")).isEqualTo("foo.bar.test")
    }

    @Test
    fun testOriginalApplicationIdWithTestVariant() {
        isTestVariant = true
        mergedFlavor.testApplicationId = "foo.bar.test"

        assertThat(provider.getOriginalApplicationId("")).isEqualTo("foo.bar.test")
    }

    @Test
    fun testOriginalApplicationId() {
        assertThat(provider.getOriginalApplicationId("")).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun testGetSplit() {
        `when`(manifestSupplier.split).thenReturn("com.split")
        assertThat(provider.split).isEqualTo("com.split")
    }

    @Test
    fun testVersionNameFromFlavorWithSuffix() {
        mergedFlavor.versionName = "1.0"
        buildType.versionNameSuffix = "-DEBUG"
        assertThat(provider.versionName).isEqualTo("1.0-DEBUG")
    }

    @Test
    fun testVersionNameWithSuffixOnly() {
        buildType.versionNameSuffix = "-DEBUG"

        assertThat(provider.versionName).isEqualTo("-DEBUG")
    }

    @Test
    fun testVersionNameFromManifest() {
        `when`(manifestSupplier.versionName).thenReturn("MANIFEST")
        assertThat(provider.versionName).isEqualTo("MANIFEST")
    }

    @Test
    fun testVersionCodeFromManifest() {
        `when`(manifestSupplier.versionCode).thenReturn(34)
        assertThat(provider.versionCode).isEqualTo(34)
    }

    @Test
    fun testVersionCodeFromFlavor() {
        mergedFlavor.versionCode = 32

        assertThat(provider.versionCode).isEqualTo(32)
    }

    @Test
    fun testInstrumentationRunnerFromManifest() {
        `when`(manifestSupplier.instrumentationRunner).thenReturn("instrumentation-manifest")
        assertThat(provider.instrumentationRunner).isEqualTo("instrumentation-manifest")
    }

    @Test
    fun testInstrumentationRunnerFromFlavor() {
        mergedFlavor.testInstrumentationRunner = "instrumentation-flavor"
        assertThat(provider.instrumentationRunner).isEqualTo("instrumentation-flavor")
    }

    @Test
    fun testFunctionalTestFromManifest() {
        `when`(manifestSupplier.functionalTest).thenReturn(false)

        assertThat(provider.functionalTest).isEqualTo(false)
    }

    @Test
    fun testFunctionalTestFromFlavor() {
        mergedFlavor.testFunctionalTest = true
        assertThat(provider.functionalTest).isEqualTo(true)
    }

    @Test
    fun testHandleProfilingFromManifest() {
        `when`(manifestSupplier.handleProfiling).thenReturn(false)
        assertThat(provider.handleProfiling).isEqualTo(false)
    }

    @Test
    fun testHandleProfilingFromFlavor() {
        mergedFlavor.testHandleProfiling = true

        assertThat(provider.handleProfiling).isEqualTo(true)
    }

    @Test
    fun testTestLabelFromManifest() {
        `when`(manifestSupplier.testLabel).thenReturn("test.label")

        assertThat(provider.testLabel).isEqualTo("test.label")
    }

    @Test
    fun testExtractNativeLibsFromManifest() {
        `when`(manifestSupplier.extractNativeLibs).thenReturn(true)

        assertThat(provider.extractNativeLibs).isEqualTo(true)
    }

    @Test
    fun testTargetPackageFromManifest() {
        `when`(manifestSupplier.targetPackage).thenReturn("target.package")

        assertThat(provider.targetPackage).isEqualTo("target.package")
    }
}
