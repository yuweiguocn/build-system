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

package com.android.build.gradle.tasks

import com.android.build.VariantOutput
import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.CoreBuildType
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.ide.FilterDataImpl
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.variant.FeatureVariantData
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.VariantTypeImpl
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.function.Supplier

/**
 * Tests for the [GenerateSplitAbiRes] class
 */
class GenerateSplitAbiResTest {

    @get:Rule val temporaryFolder = TemporaryFolder()
    @Mock private lateinit var mockedGlobalScope: GlobalScope
    @Mock private lateinit var mockedVariantScope: VariantScope
    @Mock private lateinit var mockedArtifacts: BuildArtifactsHolder
    @Mock private lateinit var mockedOutputScope: OutputScope
    @Mock private lateinit var mockedAndroidBuilder: AndroidBuilder
    @Mock private lateinit var mockedVariantConfiguration: GradleVariantConfiguration
    @Mock private lateinit var mockedAndroidConfig: AndroidConfig
    @Mock private lateinit var mockedSplits: Splits
    @Mock private lateinit var mockedBuildType: CoreBuildType
    @Mock private lateinit var mockedVariantData: FeatureVariantData
    @Mock private lateinit var mockedAaptOptions: AaptOptions
    @Mock private lateinit var mockedOutputFactory: OutputFactory
    @Mock private lateinit var provider: FeatureSetMetadata.SupplierProvider
    @Mock private lateinit var projectOptionsMock: ProjectOptions

    private val apkData = OutputFactory.ConfigurationSplitApkData(
            "x86",
            "app",
            "app",
            "dirName",
            "app.apk",
            ImmutableList.of(FilterDataImpl(VariantOutput.FilterType.ABI, "x86")))
    private lateinit var project: Project

    @Before
    fun setUp() {

        MockitoAnnotations.initMocks(this)
        val testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        with(mockedGlobalScope) {
            `when`(androidBuilder).thenReturn(mockedAndroidBuilder)
            `when`(extension).thenReturn(mockedAndroidConfig)
//            `when`(projectBaseName).thenReturn("featureA")
            `when`(project).thenReturn(this@GenerateSplitAbiResTest.project)
            `when`(projectOptions).thenReturn(projectOptionsMock)
        }

        with(mockedVariantScope) {
            `when`(globalScope).thenReturn(mockedGlobalScope)
            `when`(variantData).thenReturn(mockedVariantData)
            `when`(variantConfiguration).thenReturn(mockedVariantConfiguration)
            `when`(outputScope).thenReturn(mockedOutputScope)
            `when`(taskContainer).thenReturn(MutableTaskContainer())
            `when`(fullVariantName).thenReturn("theVariantName")
        }

        mockedVariantScope.taskContainer.preBuildTask = project.tasks.register("preBuildTask")

        with(mockedAndroidConfig) {
            `when`(aaptOptions).thenReturn(mockedAaptOptions)
            `when`(splits).thenReturn(mockedSplits)
        }

        with(mockedVariantConfiguration) {
            `when`(buildType).thenReturn(mockedBuildType)
            `when`(applicationId).thenReturn("com.example.app")
        }

        with(mockedVariantData) {
            `when`(outputFactory).thenReturn(mockedOutputFactory)
        }

        `when`(mockedSplits.abiFilters).thenReturn(ImmutableSet.of("arm", "x86"))
        `when`(mockedBuildType.isDebuggable).thenReturn(true)

        `when`(provider.getFeatureNameSupplierForTask(
                ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(Supplier { "featureA" } )
    }

    @Test
    fun testBaseFeatureConfiguration() {

        with(initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantTypeImpl.BASE_FEATURE)
            `when`(mockedVariantScope.type).thenReturn(VariantTypeImpl.BASE_FEATURE)
        }) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isNull()
        }
    }

    @Test
    fun testNonBaseFeatureConfiguration() {

        with (initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantTypeImpl.FEATURE)
            `when`(mockedVariantScope.type).thenReturn(VariantTypeImpl.FEATURE)
        }) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isEqualTo("featureA")
        }
    }

    @Test
    fun testNonFeatureConfiguration() {

        with(initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantTypeImpl.LIBRARY)
            `when`(mockedVariantScope.type).thenReturn(VariantTypeImpl.LIBRARY)
        }) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isNull()
        }
    }

    @Test
    fun testCommonConfiguration() {
        with(initTask()) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isNull()
            assertThat(outputBaseName).isEqualTo("base")
            assertThat(isDebuggable).isTrue()
        }
    }

    @Ignore
    @Test
    fun testCommonManifestValues() {
        val generatedSplitManifest = initTask().generateSplitManifest("x86", apkData)
        assertThat(generatedSplitManifest).exists()
        val content = Files.asCharSource(generatedSplitManifest, Charsets.UTF_8).read()
        assertThat(content).contains("android:versionCode=1")
        assertThat(content).contains("android:versionName=versionName")
        assertThat(content).contains("package=com.example.app")
        assertThat(content).contains("targetABI=x86")
        assertThat(content).contains("split=\"featureA.comnfig.x86\"")
    }

    @Test
    fun testNonFeatureExecution() {

        val generatedSplitManifest = initTask().generateSplitManifest("x86", apkData)
        assertThat(generatedSplitManifest).exists()
        assertThat(generatedSplitManifest).doesNotContain("configForSplit")
    }

    @Test
    fun testBaseFeatureExecution() {

        val generatedSplitManifest = initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantTypeImpl.BASE_FEATURE)
            `when`(mockedVariantScope.type).thenReturn(VariantTypeImpl.BASE_FEATURE)
        }.generateSplitManifest("x86", apkData)

        assertThat(generatedSplitManifest).exists()
        assertThat(generatedSplitManifest).doesNotContain("configForSplit")
    }

    @Test
    fun testNonBaseFeatureExecution() {

        val generatedSplitManifest = initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantTypeImpl.FEATURE)
            `when`(mockedVariantScope.type).thenReturn(VariantTypeImpl.FEATURE)
        }.generateSplitManifest("x86", apkData)

        assertThat(generatedSplitManifest).exists()
        assertThat(generatedSplitManifest).contains("configForSplit=\"featureA\"")
    }

    private fun initTask(initializationLambda : (GenerateSplitAbiRes.CreationAction) -> Unit = {}) : GenerateSplitAbiRes {
        val configAction = GenerateSplitAbiRes.CreationAction(
            mockedVariantScope,
            provider
        )

        initCommonFields()
        initializationLambda(configAction)

        val task = project!!.tasks.create("test", GenerateSplitAbiRes::class.java)

        `when`(mockedArtifacts.appendArtifact(
            InternalArtifactType.ABI_PROCESSED_SPLIT_RES, task.name, "out"))
            .thenReturn(temporaryFolder.newFolder())

        configAction.preConfigure(task.name)
        configAction.configure(task)

        return task
    }

    private fun initCommonFields() {
        `when`(mockedVariantScope.type).thenReturn(VariantTypeImpl.LIBRARY)
        `when`(mockedVariantScope.artifacts).thenReturn(mockedArtifacts)

        with(mockedVariantConfiguration) {
            `when`(type).thenReturn(VariantTypeImpl.LIBRARY)
            `when`(fullName).thenReturn("debug")
            `when`(versionCode).thenReturn(1)
            `when`<String>(versionName).thenReturn("versionName")
            `when`(baseName).thenReturn("base")
        }
    }
}
