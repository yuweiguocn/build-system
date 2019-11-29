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

package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.Context
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.gradle.internal.res.namespaced.JarRequest
import com.android.build.gradle.internal.res.namespaced.JarWorkerRunnable
import com.android.build.gradle.internal.transforms.TransformTestHelper.invocationBuilder
import com.android.build.gradle.internal.transforms.TransformTestHelper.singleJarBuilder
import com.android.builder.packaging.JarMerger.MODULE_PATH
import com.android.testutils.truth.MoreTruth
import com.android.testutils.apk.Zip
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.gradle.api.Action
import org.gradle.workers.WorkerConfiguration
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.jar.JarFile

class MergeClassesTransformTest {

    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var outputJar: File
    private lateinit var transform: MergeClassesTransform
    private lateinit var context: Context

    private val workerExecutor = object : WorkerExecutor {
        override fun submit(
            aClass: Class<out Runnable>,
            action: Action<in WorkerConfiguration>
        ) {
            val workerConfiguration = Mockito.mock(WorkerConfiguration::class.java)
            val captor = ArgumentCaptor.forClass(JarRequest::class.java)
            action.execute(workerConfiguration)
            Mockito.verify<WorkerConfiguration>(workerConfiguration).params(captor.capture())
            val workAction = JarWorkerRunnable(captor.value)
            workAction.run()
        }

        override fun await() {
            // do nothing;
        }
    }

    @Before
    fun setUp() {
        outputJar = tmp.root.toPath().resolve("feature-foo.jar").toFile()
        transform = MergeClassesTransform(outputJar)

        context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.workerExecutor).thenReturn(workerExecutor)
    }

    @After
    fun tearDown() {
        FileUtils.deleteIfExists(outputJar)
    }

    @Test
    fun testBasic() {
        val invocation =
            invocationBuilder()
                .setContext(context)
                .setIncremental(false)
                .addReferenceInput(
                    singleJarBuilder(getInputFile("test-jar1.jar"))
                        .setContentTypes(CLASSES, RESOURCES)
                        .setScopes(PROJECT)
                        .build())
                .addReferenceInput(
                    singleJarBuilder(getInputFile("test-jar2.jar"))
                        .setContentTypes(CLASSES)
                        .setScopes(SUB_PROJECTS)
                        .build())
            .build()

        transform.transform(invocation)

        // outputJar should only contain classes, not java resources.
        Zip(outputJar).use {
            MoreTruth.assertThat(it)
                .contains("com/example/android/multiproject/person/People.class")
            MoreTruth.assertThat(it).contains("com/example/android/multiproject/person/Foo.class")
            MoreTruth.assertThat(it).doesNotContain("file1.txt")
            MoreTruth.assertThat(it).doesNotContain("file2.txt")
        }
    }

    private fun getInputFile(name: String): File {
        val stream = this.javaClass.getResourceAsStream(name)
        val inputPath = Files.createTempFile(null, null)
        val inputFile = inputPath.toFile()
        inputFile.deleteOnExit()
        FileUtils.deleteIfExists(inputFile)

        Files.copy(stream, inputPath)

        return inputFile
    }
}