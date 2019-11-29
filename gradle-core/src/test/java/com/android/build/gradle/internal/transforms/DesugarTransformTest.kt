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

package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.process.JavaProcessExecutor
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

class DesugarTransformTest {
    @JvmField
    @Rule
    var tmp = TemporaryFolder()

    private val processExecutor = JavaProcessExecutor { _, _ -> throw RuntimeException() }
    private var output: Path? = null
    private var outputProvider: TransformOutputProvider? = null

    @Before
    fun setUp() {
        output = tmp.newFolder().toPath()
        outputProvider = TestTransformOutputProvider(output!!)
    }

    @Test
    fun testFullJar() {
        val jar = tmp.newFile("input.jar").toPath()
        val input = TransformTestHelper.singleJarBuilder(jar.toFile()).build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(input)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val cacheMisses = transform.cacheMisses
        assertThat(getInputFiles(cacheMisses)).containsExactly(jar)
    }

    @Test
    fun testFullJarAndDir() {
        val jar = tmp.newFile("input.jar").toPath()
        val dir = tmp.newFolder().toPath()
        val inputJar = TransformTestHelper.singleJarBuilder(jar.toFile()).build()
        val dirInput = TransformTestHelper.directoryBuilder(dir.toFile()).build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputJar)
                        .addInput(dirInput)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val cacheMisses = transform.cacheMisses
        assertThat(getInputFiles(cacheMisses)).containsExactly(jar, dir)
    }

    @Test
    fun testIncremental() {
        val jar = tmp.newFile("input.jar").toPath()
        val jar2 = tmp.newFile("input2.jar").toPath()
        val dir = tmp.newFolder().toPath()
        val inputJar =
                TransformTestHelper.singleJarBuilder(jar.toFile())
                        .setStatus(Status.CHANGED)
                        .build()
        val inputJar2 =
                TransformTestHelper.singleJarBuilder(jar2.toFile())
                        .setStatus(Status.NOTCHANGED)
                        .build()
        val dirInput =
                TransformTestHelper.directoryBuilder(dir.toFile())
                        .putChangedFiles(mapOf())
                        .build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputJar)
                        .addInput(inputJar2)
                        .addInput(dirInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val cacheMisses = transform.cacheMisses
        assertThat(getInputFiles(cacheMisses)).containsExactly(jar)
    }

    @Test
    fun testIncrementalRemoved() {
        val outputJar = output!!.resolve("input.jar.jar")
        Files.createFile(outputJar)
        val outputDir = output!!.resolve("input_dir")
        Files.createFile(outputDir)

        val jar = tmp.root.toPath().resolve("input.jar")
        val dir = tmp.root.toPath().resolve("input_dir")
        val inputJar =
                TransformTestHelper.singleJarBuilder(jar.toFile())
                        .setStatus(Status.REMOVED)
                        .build()
        val dirInput =
                TransformTestHelper.directoryBuilder(dir.toFile())
                        .putChangedFiles(mapOf())
                        .build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputJar)
                        .addInput(dirInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val cacheMisses = transform.cacheMisses
        assertThat(cacheMisses).hasSize(0)
        assertThat(outputJar).doesNotExist()
        assertThat(outputDir).doesNotExist()
    }

    @Test
    fun testIncrementalAddedFiles() {
        val jar = tmp.root.toPath().resolve("input.jar")
        val dir = tmp.newFolder().toPath()
        val inputJar = TransformTestHelper.singleJarBuilder(jar.toFile()).build()
        val dirInput =
                TransformTestHelper.directoryBuilder(dir.toFile())
                        .putChangedFiles(
                                mapOf(File("A") to Status.ADDED,
                                        File("B") to Status.ADDED))
                        .build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(inputJar)
                        .addInput(dirInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val cacheMisses = transform.cacheMisses
        assertThat(getInputFiles(cacheMisses)).containsExactly(dir)
    }

    @Test
    fun testIncrementalChangeFewFilesFromInput() {
        val dir = tmp.newFolder().toPath()
        val changed = mutableMapOf<File, Status>()
        for (i in 0 until DesugarTransform.MIN_INPUT_SIZE_TO_COPY_TO_TMP + 1) {
            // name them in a way that no file name (without ext) starts with another file name
            val classFile = Files.createFile(dir.resolve("${i}_$i.class"))
            if (i % 20 == 0) {
                changed[classFile.toFile()] = Status.ADDED
            }
        }
        val dirInput =
                TransformTestHelper.directoryBuilder(dir.toFile()).putChangedFiles(changed).build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(dirInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val changedFiles = changed.keys.map { it.name }

        val onlyMiss = Iterables.getOnlyElement(transform.cacheMisses)
        // the changed files should be copied to a temporary directory
        assertThat(onlyMiss.inputPath).isNotEqualTo(dir)
        assertThat(
                Files.walk(onlyMiss.inputPath)
                        .filter { Files.isRegularFile(it) }
                        .map { p -> p.fileName.toString() }
                        .toList())
                .containsExactlyElementsIn(changedFiles)
    }

    @Test
    fun testIncrementalChangeTooManyFromInput() {
        val dir = tmp.newFolder().toPath()
        val changed = mutableMapOf<File, Status>()
        for (i in 0 until DesugarTransform.MIN_INPUT_SIZE_TO_COPY_TO_TMP + 1) {
            // name them in a way that no file name (without ext) starts with another file name
            val classFile = Files.createFile(dir.resolve("${i}_$i.class"))
            changed[classFile.toFile()] = Status.ADDED
        }
        val dirInput =
                TransformTestHelper.directoryBuilder(dir.toFile()).putChangedFiles(changed).build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(dirInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val changedFiles = changed.keys.map { it.name }

        val onlyMiss = Iterables.getOnlyElement(transform.cacheMisses)
        assertThat(onlyMiss.inputPath).isEqualTo(dir)
        assertThat(
                Files.walk(onlyMiss.inputPath)
                        .filter { Files.isRegularFile(it) }
                        .map { p -> p.fileName.toString() }
                        .toList())
                .containsExactlyElementsIn(changedFiles)
    }

    @Test
    fun testIncrementalChangeFewFilesSiblingsCopied() {
        val outputDir = output!!.resolve("input_dir")
        Files.createDirectory(outputDir)
        // these should be removed
        Files.createFile(outputDir.resolve("A.class"))
        Files.createFile(outputDir.resolve("AA.class"))
        Files.createFile(outputDir.resolve("AAA.class"))

        val dir = tmp.newFolder("input_dir").toPath()
        for (i in 0 until DesugarTransform.MIN_INPUT_SIZE_TO_COPY_TO_TMP) {
            Files.createFile(dir.resolve("$i.class"))
        }
        val changed = Files.createFile(dir.resolve("A.class"))
        Files.createFile(dir.resolve("AA.class"))
        Files.createFile(dir.resolve("AAA.class"))
        val dirInput =
                TransformTestHelper.directoryBuilder(dir.toFile())
                        .putChangedFiles(mapOf(changed.toFile() to Status.ADDED))
                        .build()

        val invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(dirInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider!!)
                        .build()

        val transform = runTransform(invocation)

        val onlyMiss = Iterables.getOnlyElement(transform.cacheMisses)
        assertThat(onlyMiss.inputPath).isNotEqualTo(dir)
        assertThat(
                Files.walk(onlyMiss.inputPath)
                        .filter { Files.isRegularFile(it) }
                        .map { p -> p.fileName.toString() }
                        .toList())
                .containsExactly("A.class", "AA.class", "AAA.class")

        assertThat(outputDir.resolve("A.class")).doesNotExist()
        assertThat(outputDir.resolve("AA.class")).doesNotExist()
        assertThat(outputDir.resolve("AAA.class")).doesNotExist()
    }

    @Test
    fun testIncrementalAdditionalPathsCopiedToTemp() {
        val dir = tmp.newFolder().toPath()
        for (i in 0 until DesugarTransform.MIN_INPUT_SIZE_TO_COPY_TO_TMP) {
            Files.createFile(dir.resolve("$i.class"))
        }
        val additionalClassFile = Files.createFile(dir.resolve("A.class"))
        val dirInput = TransformTestHelper.directoryBuilder(dir.toFile()).build()

        val invocation = TransformTestHelper.invocationBuilder()
                .addInput(dirInput)
                .setIncremental(true)
                .setTransformOutputProvider(outputProvider!!)
                .build()

        val transform = runTransform(invocation, setOf(additionalClassFile.toFile()))

        val onlyMiss = Iterables.getOnlyElement(transform.cacheMisses)
        assertThat(onlyMiss.inputPath).isNotEqualTo(dir)
        assertThat(
                Files.walk(onlyMiss.inputPath)
                        .filter { Files.isRegularFile(it) }
                        .map { p -> p.fileName.toString() }
                        .toList())
                .containsExactly("A.class")
    }

    private fun runTransform(
            invocation: TransformInvocation,
            additionalPaths: Set<File> = setOf()): DesugarTransform {
        val executor = WaitableExecutor.useDirectExecutor()
        val transform = DesugarTransform(
            FakeFileCollection(),
            null,
            19,
            processExecutor,
            true,
            false,
            tmp.newFolder().toPath(),
            "debug",
            executor,
            true
        )
        transform.processInputs(invocation, additionalPaths)
        executor.waitForTasksWithQuickFail<Any>(true)
        return transform
    }

    private fun getInputFiles(cacheMisses: Set<DesugarTransform.InputEntry>): List<Path> =
            cacheMisses.map { it.inputPath }
}
