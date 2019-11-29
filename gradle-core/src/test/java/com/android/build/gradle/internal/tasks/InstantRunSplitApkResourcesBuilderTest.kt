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
package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.AndroidBuilder
import com.android.builder.internal.aapt.CloseableBlockingResourceLinker
import com.android.build.gradle.internal.scope.ApkData
import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.utils.ILogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the [InstantRunSplitApkResourcesBuilder]
 */
class InstantRunSplitApkResourcesBuilderTest {

    companion object {
        val invokeList: Multimap<Int, Int> = ArrayListMultimap.create()
        val bucketSize = AtomicInteger(0)
        val requestedNumberOfBuckets = AtomicInteger(0)
    }

    @Mock private lateinit var buildToolInfo: BuildToolInfo
    @Mock private lateinit var androidBuilder: AndroidBuilder
    @Mock private lateinit var logger: ILogger
    @Mock private lateinit var revision: Revision
    @Mock private lateinit var target: IAndroidTarget
    @Mock private lateinit var variantScope: VariantScope
    @Mock private lateinit var variantConfiguration: GradleVariantConfiguration
    @Mock private lateinit var globalScope: GlobalScope
    @Mock private lateinit var artifacts: BuildArtifactsHolder
    @Mock private lateinit var instantRunBuildContext: InstantRunBuildContext
    @Mock private lateinit var resources: BuildableArtifact
    @Mock private lateinit var projectOptions: ProjectOptions
    @Mock private lateinit var extension: BaseExtension
    @Mock private lateinit var aaptOptions: AaptOptions
    @Mock private lateinit var outputScope: OutputScope
    @Mock private lateinit var mainApkInfo: ApkData
    @Mock private lateinit var taskContainer: MutableTaskContainer

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        invokeList.clear()
        bucketSize.set(0)
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(androidBuilder.buildToolInfo).thenReturn(buildToolInfo)
        Mockito.`when`(androidBuilder.logger).thenReturn(logger)
        Mockito.`when`(buildToolInfo.revision).thenReturn(revision)
        Mockito.`when`(androidBuilder.target).thenReturn(target)
        Mockito.`when`(target.getPath(IAndroidTarget.ANDROID_JAR)).thenReturn("android.jar")
        Mockito.`when`(variantScope.variantConfiguration).thenReturn(variantConfiguration)
        Mockito.`when`(variantScope.globalScope).thenReturn(globalScope)
        Mockito.`when`(variantScope.artifacts).thenReturn(artifacts)
        Mockito.`when`(variantScope.instantRunBuildContext).thenReturn(instantRunBuildContext)
        Mockito.`when`(artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES))
            .thenReturn(resources)
        Mockito.`when`(globalScope.androidBuilder).thenReturn(androidBuilder)
        Mockito.`when`(globalScope.projectOptions).thenReturn(projectOptions)
        Mockito.`when`(globalScope.extension).thenReturn(extension)
        Mockito.`when`(extension.aaptOptions).thenReturn(aaptOptions)
        Mockito.`when`(variantScope.outputScope).thenReturn(outputScope)
        Mockito.`when`(outputScope.mainSplit).thenReturn(mainApkInfo)
    }

    @Test
    fun defaultBucket() {
        performWith(0)
    }

    @Test
    fun oneBucket() {
        performWith(1)
    }

    @Test
    fun maxBucket() {
        performWith(10)
    }

    private fun performWith(requestedBucketSize: Int) {
        val testDir = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()

        val task =
            project.tasks.create("test", InstantRunSplitApkResourcesBuilderForTest::class.java)

        val configAction = InstantRunSplitApkResourcesBuilder.CreationAction(variantScope)
        Mockito.`when`(variantConfiguration.applicationId).thenReturn(
            "com.example.InstantRunSplitApkResourcesBuilderTest")
        Mockito.`when`(artifacts.appendArtifact(
            InternalArtifactType.INSTANT_RUN_SPLIT_APK_RESOURCES,
            task.name)).thenReturn(temporaryFolder.newFolder())
        Mockito.`when`(resources.get()).thenReturn(project.files())
        Mockito.`when`(globalScope.project).thenReturn(project)

        val preBuildTask = project.tasks.register("preBuild")
        Mockito.`when`(variantScope.fullVariantName).thenReturn("theVariantName")
        Mockito.`when`(variantScope.taskContainer).thenReturn(taskContainer)
        Mockito.`when`(taskContainer.preBuildTask).thenReturn(preBuildTask)

        configAction.preConfigure(task.name)
        configAction.configure(task)

        task.poolSize = 0
        if (requestedBucketSize != 0) {
            requestedNumberOfBuckets.set(requestedBucketSize)
        }
        task.taskAction()

        val values = mutableListOf<Int>()
        invokeList.values().forEach { values.add(it) }

        assertThat(values.size).isEqualTo(10)
        for (i in 0..9) assertThat(values).contains(i)

        assertThat(bucketSize.get()).isEqualTo(task.getNumberOfBuckets())
        // each bucket should not more than invokeList.size() / numberOfBuckets.
        invokeList.keys().forEach { assertThat(invokeList.get(it).size).isAtMost(
            (10 / task.getNumberOfBuckets()) + 1
        ) }
    }

    /**
     * Subclass of the task to avoid performing real aapt2 work.
     * We really just want to make sure that all slices are processed in the right number of
     * buckets.
     */
    open class InstantRunSplitApkResourcesBuilderForTest
        : InstantRunSplitApkResourcesBuilder(Mockito.mock(WorkerExecutor::class.java)) {

        var poolSize: Int = 0

        override fun getNumberOfBuckets(): Int {
            return if (requestedNumberOfBuckets.get() == 0) {
                super.getNumberOfBuckets()
            } else requestedNumberOfBuckets.get()
        }

        override fun getAaptService(): Aapt2ServiceKey= Mockito.mock(Aapt2ServiceKey::class.java)
        override fun getAaptServicePoolsSize(aapt2ServiceKey: Aapt2ServiceKey) = poolSize
        override fun getWorkerItemClass(): Class<out Runnable> =
            GenerateSplitApkResourceForTest::class.java
    }

    /**
     * record which bucket processed which item.
     */
    private class GenerateSplitApkResourceForTest(
        params: InstantRunSplitApkResourcesBuilder.GenerateSplitApkResource.Params)
        : InstantRunSplitApkResourcesBuilder.GenerateSplitApkResource(params) {

        val bucketId = bucketSize.incrementAndGet()

        override fun requestAaptDaemon(aapt2ServiceKey: Aapt2ServiceKey): CloseableBlockingResourceLinker {
            return Mockito.mock(CloseableBlockingResourceLinker::class.java)
        }

        override fun generateSplitApkResource(
            linker: CloseableBlockingResourceLinker,
            sliceNumber: Int
        ) {
            synchronized(invokeList) {
                invokeList.put(bucketId, sliceNumber)
            }
        }
    }
}
