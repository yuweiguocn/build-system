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

import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.fixtures.createBuildArtifact
import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexArchiveBuilderConfig
import com.android.builder.dexing.DexMergerTool
import com.android.builder.dexing.DexerTool
import com.android.builder.dexing.DexingType
import com.android.dx.command.dexer.DxContext
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class DexMergingTaskTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testMonoDexSingleClass() {
        val dexFiles = generateArchive("test/A")

        val output = tmp.newFolder()
        DexMergingTaskDelegate(
            DexingType.MONO_DEX,
            NoOpMessageReceiver(),
            DexMergerTool.D8,
            21,
            true,
            0,
            null,
            FakeFileCollection(dexFiles),
            output
        ).run()
        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(listOf("Ltest/A;"))
    }

    @Test
    fun testMonoDexMultipleClasses() {
        val dexFiles = generateArchive("test/A", "test/B", "test/C")

        val output = tmp.newFolder()
        DexMergingTaskDelegate(
            DexingType.MONO_DEX,
            NoOpMessageReceiver(),
            DexMergerTool.D8,
            21,
            true,
            0,
            null,
            FakeFileCollection(dexFiles),
            output
        ).run()
        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(
            listOf(
                "Ltest/A;",
                "Ltest/B;",
                "Ltest/C;"
            )
        )
    }

    @Test
    fun testLegacyMultiDex() {
        val dexFiles = generateArchive("test/A", "test/B", "test/C")

        val mainDexList = tmp.newFile()
        mainDexList.writeText("test/A.class")

        val output = tmp.newFolder()
        DexMergingTaskDelegate(
            DexingType.LEGACY_MULTIDEX,
            NoOpMessageReceiver(),
            DexMergerTool.D8,
            19,
            true,
            0,
            createBuildArtifact(mainDexList),
            FakeFileCollection(dexFiles),
            output
        ).run()
        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(listOf("Ltest/A;"))
        assertThatDex(output.resolve("classes2.dex")).containsExactlyClassesIn(
            listOf(
                "Ltest/B;",
                "Ltest/C;"
            )
        )
    }

    @Test
    fun testNativeMultiDexWithThreshold() {
        val numInputs = 5
        val inputFiles = (0 until numInputs).map {
            tmp.newFile("input_$it")
        }

        val output = tmp.newFolder()
        DexMergingTaskDelegate(
            DexingType.NATIVE_MULTIDEX,
            NoOpMessageReceiver(),
            DexMergerTool.D8,
            21,
            true,
            numInputs + 1,
            null,
            FakeFileCollection(inputFiles),
            output
        ).run()

        (0 until numInputs).forEach {
            PathSubject.assertThat(output.resolve("classes_$it.dex")).exists()
        }
    }

    @Test
    fun testNativeMultiDexWithThresholdToMerge() {
        val numInputs = 5
        val inputFiles = (0 until numInputs).map {
            generateArchive("test/A$it")
        }

        val output = tmp.newFolder()
        DexMergingTaskDelegate(
            DexingType.NATIVE_MULTIDEX,
            NoOpMessageReceiver(),
            DexMergerTool.D8,
            21,
            true,
            numInputs,
            null,
            FakeFileCollection(inputFiles),
            output
        ).run()

        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(
            (0 until numInputs).map { "Ltest/A$it;" })
    }

    @Test
    fun testFileCollectionOrdering() {
        val directoryB = tmp.newFolder("b").let { rootDir ->
            generateArchive(tmp, rootDir.toPath(), listOf("test/B"))
            rootDir.resolve("test2").also {
                it.mkdirs()
                generateArchive(tmp, it.toPath(), listOf("test/B2"))
            }
            rootDir.resolve("test1").also {
                it.mkdirs()
                generateArchive(tmp, it.toPath(), listOf("test/B12", "test/B11"))
            }
            rootDir
        }
        val directoryA = tmp.newFolder("a").let { rootDir ->
            generateArchive(tmp, rootDir.toPath(), listOf("test/A2", "test/A1"))
            rootDir
        }
        val inputFiles = listOf(directoryB, directoryA)

        val output = tmp.newFolder()
        DexMergingTaskDelegate(
            DexingType.NATIVE_MULTIDEX,
            NoOpMessageReceiver(),
            DexMergerTool.D8,
            21,
            true,
            Int.MAX_VALUE,
            null,
            FakeFileCollection(inputFiles),
            output
        ).run()

        // Ordering within file collection should not change, but entries inside directories should
        // be sorted.
        assertThatDex(output.resolve("classes_0.dex")).containsExactlyClassesIn(listOf("Ltest/B;"))
        assertThatDex(output.resolve("classes_1.dex"))
            .containsExactlyClassesIn(listOf("Ltest/B11;"))
        assertThatDex(output.resolve("classes_2.dex"))
            .containsExactlyClassesIn(listOf("Ltest/B12;"))
        assertThatDex(output.resolve("classes_3.dex")).containsExactlyClassesIn(listOf("Ltest/B2;"))
        assertThatDex(output.resolve("classes_4.dex")).containsExactlyClassesIn(listOf("Ltest/A1;"))
        assertThatDex(output.resolve("classes_5.dex")).containsExactlyClassesIn(listOf("Ltest/A2;"))
    }

    private fun generateArchive(vararg classes: String): File {
        val dexArchivePath = tmp.newFolder()
        generateArchive(tmp, dexArchivePath.toPath(), classes.toList())
        return dexArchivePath
    }
}

fun generateArchive(tmp: TemporaryFolder, output: Path, classes: Collection<String>) {
    val classesInput = tmp.newFolder().toPath().resolve("input")
    TestInputsGenerator.dirWithEmptyClasses(classesInput, classes.toList())

    // now convert to dex archive
    val builder = DexArchiveBuilder.createDxDexBuilder(
        DexArchiveBuilderConfig(
            DxContext(System.out, System.err),
            true,
            10,
            0,
            DexerTool.DX,
            10,
            true
        )
    )

    ClassFileInputs.fromPath(classesInput)
        .use { input ->
            builder.convert(
                input.entries { p -> true },
                output,
                false
            )
        }
}