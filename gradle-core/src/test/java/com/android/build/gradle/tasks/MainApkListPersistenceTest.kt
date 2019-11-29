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

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.core.VariantTypeImpl
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

/**
 * Tests for {@see MainApkListPersistence} task.
 */
open class MainApkListPersistenceTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    var manifestFileFolder = TemporaryFolder()

    @Mock private lateinit var variantScope: VariantScope
    @Mock private lateinit var config: GradleVariantConfiguration
    @Mock private lateinit var artifacts: BuildArtifactsHolder

    private lateinit var outputFactory: OutputFactory
    internal lateinit var project: Project
    internal lateinit var task: MainApkListPersistence
    private lateinit var configAction: MainApkListPersistence.CreationAction
    internal lateinit var testDir: File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        Mockito.`when`(variantScope.getTaskName(ArgumentMatchers.any(String::class.java)))
                .thenReturn("taskFoo")
        Mockito.`when`(variantScope.artifacts).thenReturn(artifacts)
        Mockito.`when`(variantScope.taskContainer).thenReturn(MutableTaskContainer())
        Mockito.`when`(variantScope.fullVariantName).thenReturn("theVariantName")
        Mockito.`when`(config.type).thenReturn(VariantTypeImpl.BASE_APK)

        variantScope.taskContainer.preBuildTask = project.tasks.register("preBuildTask")

        task = project.tasks.create("test", MainApkListPersistence::class.java)
        configAction = MainApkListPersistence.CreationAction(variantScope)
        Mockito.`when`(artifacts.appendArtifact(InternalArtifactType.APK_LIST,
            task.name, SdkConstants.FN_APK_LIST)).thenReturn(temporaryFolder.newFolder())
        outputFactory = OutputFactory("foo", config)
    }

    @Test
    fun testFullSplitPersistenceNoDisabledState() {
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI, "x86")))
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI,
                        "armeabi")))
        Mockito.`when`(variantScope.outputScope).thenReturn(outputFactory.output)

        configAction.preConfigure(task.name)
        configAction.configure(task)
        assertThat(task.apkDataListJson).isEqualTo(ExistingBuildElements.persistApkList(outputFactory.output.apkDatas))
        assertThat(task.outputFile.absolutePath).startsWith(temporaryFolder.root.absolutePath)

        task.fullTaskAction()

        // assert persistence.
        val apkList = ExistingBuildElements.loadApkList(task.outputFile)
        assertThat(apkList).hasSize(2)
        assertThat(apkList.asSequence()
                .filter { apkData -> apkData.type == VariantOutput.OutputType.FULL_SPLIT}
                .filter { apkData -> apkData.isEnabled }
                .map { apkData -> apkData.filters.asSequence().map {
                    filterData -> filterData.identifier }.first() }
                .toList())
                .containsExactly("x86", "armeabi")
    }

    @Test
    fun testFullSplitPersistenceSomeDisabledState() {
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI, "x86")))
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI,
                        "armeabi"))).disable()
        Mockito.`when`(variantScope.outputScope).thenReturn(outputFactory.output)

        configAction.preConfigure(task.name)
        configAction.configure(task)
        assertThat(task.apkDataListJson).isEqualTo(ExistingBuildElements.persistApkList(outputFactory.output.apkDatas))
        assertThat(task.outputFile.absolutePath).startsWith(temporaryFolder.root.absolutePath)

        task.fullTaskAction()

        // assert persistence.
        val apkList = ExistingBuildElements.loadApkList(task.outputFile)
        assertThat(apkList).hasSize(1)
        assertThat(apkList.asSequence()
                .filter { apkData -> apkData.type == VariantOutput.OutputType.FULL_SPLIT}
                .filter { apkData -> apkData.isEnabled }
                .map { apkData -> apkData.filters.asSequence().map {
                    filterData -> filterData.identifier }.first() }
                .toList())
                .containsExactly("x86")
    }
}