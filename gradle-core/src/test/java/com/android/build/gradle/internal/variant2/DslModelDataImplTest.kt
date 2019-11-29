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

import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.model.BaseFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.DefaultConfigImpl
import com.android.build.gradle.internal.api.dsl.model.FallbackStrategyImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.fixtures.FakeConfigurationContainer
import com.android.build.gradle.internal.fixtures.FakeContainerFactory
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeFilesProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.FakeVariantFactory2
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.google.common.truth.Truth
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.junit.Test

class DslModelDataImplTest {
    private val issueReporter = FakeEvalIssueReporter()
    private val deprecationReporter = FakeDeprecationReporter()
    private val configurationContainer = FakeConfigurationContainer()
    private val dslScope = DslScopeImpl(issueReporter, deprecationReporter, FakeObjectFactory())


    @Test
    fun `source sets for default config`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.BASE_APK))

        val defaultConfigData = modelData.defaultConfigData
        Truth.assertThat(defaultConfigData).isNotNull()

        var sourceSet = defaultConfigData.getSourceSet(VariantTypeImpl.BASE_APK)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(BuilderConstants.MAIN)

        sourceSet = defaultConfigData.getSourceSet(VariantTypeImpl.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("androidTest")

        sourceSet = defaultConfigData.getSourceSet(VariantTypeImpl.UNIT_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("test")

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `configurations for default config`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.BASE_APK))

        val defaultConfigData = modelData.defaultConfigData
        Truth.assertThat(defaultConfigData).isNotNull()

        val compile = getNonNullConfig("compile")
        val api = getNonNullConfig("api")
        Truth.assertThat(api.extendsFrom).containsExactly(compile)
        val impl = getNonNullConfig("implementation")
        Truth.assertThat(impl.extendsFrom).containsExactly(api)

        Truth.assertThat(getNonNullConfig("compileOnly")).isNotNull()

        val apkOnly = getNonNullConfig("apk")
        val runtimeOnly = getNonNullConfig("runtimeOnly")
        Truth.assertThat(runtimeOnly.extendsFrom).containsExactly(apkOnly)

        // test test configuration
        val testCompile = getNonNullConfig("testCompile")
        val testApi = getNonNullConfig("testApi")
        Truth.assertThat(testApi.extendsFrom).containsExactly(testCompile)
        val testImpl = getNonNullConfig("testImplementation")
        Truth.assertThat(testImpl.extendsFrom).containsExactly(testApi, impl)
        val testApkOnly = getNonNullConfig("testApk")
        val testRuntimeOnly = getNonNullConfig("testRuntimeOnly")
        Truth.assertThat(testRuntimeOnly.extendsFrom).containsExactly(runtimeOnly, testApkOnly)

        // test androidTest configuration
        val androidTestCompile = getNonNullConfig("androidTestCompile")
        val androidTestApi = getNonNullConfig("androidTestApi")
        Truth.assertThat(androidTestApi.extendsFrom).containsExactly(androidTestCompile)
        val androidTestImpl = getNonNullConfig("androidTestImplementation")
        Truth.assertThat(androidTestImpl.extendsFrom).containsExactly(androidTestApi, impl)
        val androidTestApkOnly = getNonNullConfig("androidTestApk")
        val androidTestRuntimeOnly = getNonNullConfig("androidTestRuntimeOnly")
        Truth.assertThat(androidTestRuntimeOnly.extendsFrom).containsExactly(runtimeOnly, androidTestApkOnly)

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `configurations for default config with library`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.LIBRARY))

        val defaultConfigData = modelData.defaultConfigData
        Truth.assertThat(defaultConfigData).isNotNull()

        // test main configurations
        val publishOnly = getNonNullConfig("publish")
        val runtimeOnly = getNonNullConfig("runtimeOnly")
        Truth.assertThat(runtimeOnly.extendsFrom).containsExactly(publishOnly)

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `source sets for default config when no tests`() {
        val modelData = createModelData(
                createFactories(
                        BaseExtension2::class.java,
                        VariantTypeImpl.BASE_APK,
                        hasAndroidTests = false,
                        hasUnitTests = false))

        val defaultConfigData = modelData.defaultConfigData
        Truth.assertThat(defaultConfigData).isNotNull()

        var sourceSet = defaultConfigData.getSourceSet(VariantTypeImpl.BASE_APK)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(BuilderConstants.MAIN)

        sourceSet = defaultConfigData.getSourceSet(VariantTypeImpl.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNull()

        sourceSet = defaultConfigData.getSourceSet(VariantTypeImpl.UNIT_TEST)
        Truth.assertThat(sourceSet).isNull()

        // tests to make sure we don't have test source sets or configs.
        checkNullConfiguration("testCompile")
        checkNullConfiguration("androidTestCompile")

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `source sets for added flavors`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.BASE_APK))

        val flavorName = "free"
        modelData.productFlavors.create(flavorName)

        modelData.afterEvaluateCompute()

        val data = modelData.flavorData[flavorName]
        Truth.assertThat(data).isNotNull()

        var sourceSet = data?.getSourceSet(VariantTypeImpl.BASE_APK)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(flavorName)

        sourceSet = data?.getSourceSet(VariantTypeImpl.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("androidTestFree")

        sourceSet = data?.getSourceSet(VariantTypeImpl.UNIT_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("testFree")

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `configurations for added flavors`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.BASE_APK))

        val flavorName = "free"
        modelData.productFlavors.create(flavorName)

        modelData.afterEvaluateCompute()

        val compile = getNonNullConfig("freeCompile")
        val api = getNonNullConfig("freeApi")
        Truth.assertThat(api.extendsFrom).containsExactly(compile)
        val impl = getNonNullConfig("freeImplementation")
        Truth.assertThat(impl.extendsFrom).containsExactly(api)

        Truth.assertThat(getNonNullConfig("freeCompileOnly")).isNotNull()

        val apkOnly = getNonNullConfig("freeApk")
        val runtimeOnly = getNonNullConfig("freeRuntimeOnly")
        Truth.assertThat(runtimeOnly.extendsFrom).containsExactly(apkOnly)

        // test test configuration
        val testCompile = getNonNullConfig("testFreeCompile")
        val testApi = getNonNullConfig("testFreeApi")
        Truth.assertThat(testApi.extendsFrom).containsExactly(testCompile)
        val testImpl = getNonNullConfig("testFreeImplementation")
        Truth.assertThat(testImpl.extendsFrom).containsExactly(testApi, impl)
        val testApkOnly = getNonNullConfig("testFreeApk")
        val testRuntimeOnly = getNonNullConfig("testFreeRuntimeOnly")
        Truth.assertThat(testRuntimeOnly.extendsFrom).containsExactly(runtimeOnly, testApkOnly)

        // test androidTest configuration
        val androidTestCompile = getNonNullConfig("androidTestFreeCompile")
        val androidTestApi = getNonNullConfig("androidTestFreeApi")
        Truth.assertThat(androidTestApi.extendsFrom).containsExactly(androidTestCompile)
        val androidTestImpl = getNonNullConfig("androidTestFreeImplementation")
        Truth.assertThat(androidTestImpl.extendsFrom).containsExactly(androidTestApi, impl)
        val androidTestApkOnly = getNonNullConfig("androidTestFreeApk")
        val androidTestRuntimeOnly = getNonNullConfig("androidTestFreeRuntimeOnly")
        Truth.assertThat(androidTestRuntimeOnly.extendsFrom).containsExactly(runtimeOnly, androidTestApkOnly)

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `source sets for added build type`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.BASE_APK))

        val flavorName = "staging"
        modelData.buildTypes.create(flavorName)

        modelData.afterEvaluateCompute()

        val data = modelData.buildTypeData[flavorName]
        Truth.assertThat(data).isNotNull()

        var sourceSet = data?.getSourceSet(VariantTypeImpl.BASE_APK)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(flavorName)

        sourceSet = data?.getSourceSet(VariantTypeImpl.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("androidTestStaging")

        sourceSet = data?.getSourceSet(VariantTypeImpl.UNIT_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("testStaging")

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `configurations for added build type`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.BASE_APK))

        val flavorName = "staging"
        modelData.buildTypes.create(flavorName)

        modelData.afterEvaluateCompute()

        val compile = getNonNullConfig("stagingCompile")
        val api = getNonNullConfig("stagingApi")
        Truth.assertThat(api.extendsFrom).containsExactly(compile)
        val impl = getNonNullConfig("stagingImplementation")
        Truth.assertThat(impl.extendsFrom).containsExactly(api)

        Truth.assertThat(getNonNullConfig("stagingCompileOnly")).isNotNull()

        val apkOnly = getNonNullConfig("stagingApk")
        val runtimeOnly = getNonNullConfig("stagingRuntimeOnly")
        Truth.assertThat(runtimeOnly.extendsFrom).containsExactly(apkOnly)

        // test test configuration
        val testCompile = getNonNullConfig("testStagingCompile")
        val testApi = getNonNullConfig("testStagingApi")
        Truth.assertThat(testApi.extendsFrom).containsExactly(testCompile)
        val testImpl = getNonNullConfig("testStagingImplementation")
        Truth.assertThat(testImpl.extendsFrom).containsExactly(testApi, impl)
        val testApkOnly = getNonNullConfig("testStagingApk")
        val testRuntimeOnly = getNonNullConfig("testStagingRuntimeOnly")
        Truth.assertThat(testRuntimeOnly.extendsFrom).containsExactly(runtimeOnly, testApkOnly)

        // test androidTest configuration
        val androidTestCompile = getNonNullConfig("androidTestStagingCompile")
        val androidTestApi = getNonNullConfig("androidTestStagingApi")
        Truth.assertThat(androidTestApi.extendsFrom).containsExactly(androidTestCompile)
        val androidTestImpl = getNonNullConfig("androidTestStagingImplementation")
        Truth.assertThat(androidTestImpl.extendsFrom).containsExactly(androidTestApi, impl)
        val androidTestApkOnly = getNonNullConfig("androidTestStagingApk")
        val androidTestRuntimeOnly = getNonNullConfig("androidTestStagingRuntimeOnly")
        Truth.assertThat(androidTestRuntimeOnly.extendsFrom).containsExactly(runtimeOnly, androidTestApkOnly)

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `name collisions between flavor, build type and default values`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantTypeImpl.BASE_APK))

        // first validate collision against forbidden names
        val flavors = modelData.productFlavors
        validateCollision(
                flavors, "main","ProductFlavor names cannot be 'main'")
        validateCollision(
                flavors, "lint","ProductFlavor names cannot be 'lint'")
        validateCollision(
                flavors,
                VariantType.ANDROID_TEST_PREFIX,
                "ProductFlavor names cannot start with 'androidTest'")
        validateCollision(
                flavors,
                VariantType.ANDROID_TEST_PREFIX + "Foo",
                "ProductFlavor names cannot start with 'androidTest'")
        validateCollision(
                flavors,
                VariantType.UNIT_TEST_PREFIX,
                "ProductFlavor names cannot start with 'test'")
        validateCollision(
                flavors,
                VariantType.UNIT_TEST_PREFIX + "Foo",
                "ProductFlavor names cannot start with 'test'")

        // same with build types
        val buildTypes = modelData.buildTypes
        validateCollision(
                buildTypes, "main","BuildType names cannot be 'main'")
        validateCollision(
                buildTypes, "lint","BuildType names cannot be 'lint'")
        validateCollision(
                buildTypes,
                VariantType.ANDROID_TEST_PREFIX,
                "BuildType names cannot start with 'androidTest'")
        validateCollision(
                buildTypes,
                VariantType.ANDROID_TEST_PREFIX + "Foo",
                "BuildType names cannot start with 'androidTest'")
        validateCollision(
                buildTypes,
                VariantType.UNIT_TEST_PREFIX,
                "BuildType names cannot start with 'test'")
        validateCollision(
                buildTypes,
                VariantType.UNIT_TEST_PREFIX + "Foo",
                "BuildType names cannot start with 'test'")

        // create a flavor and then a build type of the same name
        flavors.create("foo")
        validateCollision(
                buildTypes,
                "foo",
                "BuildType names cannot collide with ProductFlavor names: foo")

        // and the other way around
        buildTypes.create("bar")
        validateCollision(
                flavors,
                "bar",
                "ProductFlavor names cannot collide with BuildType names: bar")
    }


    // ----

    private fun getNonNullConfig(name: String): Configuration {
        val config = configurationContainer.findByName(name)
        Truth.assertThat(config).named(name).isNotNull()
        return config!!
    }

    private fun checkNullConfiguration(name: String) {
        val config = configurationContainer.findByName(name)
        Truth.assertThat(config).named(name).isNull()
    }

    private fun <T> validateCollision(
            container: NamedDomainObjectContainer<T>,
            itemName: String,
            expectedMessage: String) {
        container.create(itemName)
        Truth.assertThat(issueReporter.messages).containsExactly(expectedMessage)
        issueReporter.messages.clear()
    }

    private fun <E: BaseExtension2> createModelData(
            factories: List<VariantFactory2<E>>): DslModelDataImpl<E> {

        val baseFlavor = BaseFlavorImpl(dslScope)
        val defaultConfig = DefaultConfigImpl(
                VariantPropertiesImpl(dslScope),
                BuildTypeOrProductFlavorImpl(dslScope) { baseFlavor.postProcessing },
                ProductFlavorOrVariantImpl(dslScope),
                baseFlavor,
                dslScope)

        return DslModelDataImpl(
                defaultConfig,
                factories,
                configurationContainer,
                FakeFilesProvider(),
                FakeContainerFactory(),
                dslScope,
                FakeLogger())
    }

    private fun <T: BaseExtension2> createFactories(
            itemClass: Class<T>,
            mainVariantType: VariantType,
            hasAndroidTests: Boolean = true,
            hasUnitTests: Boolean = true
    ): List<VariantFactory2<T>> {
        val list = mutableListOf<VariantFactory2<T>>()

        val testList = mutableListOf<VariantType>()
        if (hasAndroidTests) {
            testList.add(VariantTypeImpl.ANDROID_TEST)
        }
        if (hasUnitTests) {
            testList.add(VariantTypeImpl.UNIT_TEST)
        }

        list.add(FakeVariantFactory2<BaseExtension2>(mainVariantType, testList, null))

        if (hasAndroidTests) {
            list.add(
                    FakeVariantFactory2<BaseExtension2>(
                            VariantTypeImpl.ANDROID_TEST, listOf(), mainVariantType))
        }

        if (hasUnitTests) {
            list.add(
                    FakeVariantFactory2<BaseExtension2>(
                            VariantTypeImpl.UNIT_TEST, listOf(), mainVariantType))
        }

        return list
    }
}

