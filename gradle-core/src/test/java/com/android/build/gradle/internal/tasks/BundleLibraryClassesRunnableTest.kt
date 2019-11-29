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

import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.testutils.truth.MoreTruth.assertThatZip
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BundleLibraryClassesRunnableTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()
    lateinit var workers: WorkerExecutorFacade

    @Before
    fun setUp() {
        workers = ExecutorServiceAdapter(MoreExecutors.newDirectExecutorService())
    }

    @Test
    fun testClassesCopied() {
        val output = tmp.newFolder().resolve("output.jar")
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("A.class").createNewFile()
                dir.resolve("B.class").createNewFile()
                dir.resolve("res.txt").createNewFile()
                dir.resolve("META-INF").also {
                    it.mkdir()
                    it.resolve("a.modules").createNewFile()
                }
                dir.resolve("sub").also {
                    it.mkdir()
                    it.resolve("C.class").createNewFile()
                }
            }
        )
        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(),
                output = output,
                input = input,
                packageBuildConfig = false
            )
        ).run()
        assertThatZip(output).contains("A.class")
        assertThatZip(output).contains("B.class")
        assertThatZip(output).contains("sub/C.class")
        assertThatZip(output).contains("META-INF/a.modules")
        assertThatZip(output).doesNotContain("res.txt")
    }

    @Test
    fun testGeneratedSkipped() {
        val output = tmp.newFolder().resolve("output.jar")
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("A.class").createNewFile()
                dir.resolve("test").mkdirs()
                dir.resolve("test/R.class").createNewFile()
                dir.resolve("test/R\$string.class").createNewFile()
                dir.resolve("test/BuildConfig.class").createNewFile()
                dir.resolve("test/Manifest.class").createNewFile()
                dir.resolve("test/Manifest\$nested.class").createNewFile()
            }
        )
        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "test",
                toIgnore = listOf(),
                output = output,
                input = input,
                packageBuildConfig = false
            )
        ).run()
        assertThatZip(output).contains("A.class")
        assertThatZip(output).doesNotContain("test/R.class")
        assertThatZip(output).doesNotContain("test/R\$string.class")
        assertThatZip(output).doesNotContain("test/BuildConfig.class")
        assertThatZip(output).doesNotContain("test/Manifest.class")
        assertThatZip(output).doesNotContain("test/Manifest\$nested.class")
    }

    @Test
    fun testReadingFromJars() {
        val output = tmp.newFolder().resolve("output.jar")

        val inputJar = tmp.root.resolve("input.jar")
        ZipOutputStream(inputJar.outputStream()).use {
            it.putNextEntry(ZipEntry("A.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/B.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("a.txt"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/a.txt"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("META-INF/a.modules"))
            it.closeEntry()
        }

        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(),
                output = output,
                input = setOf(inputJar),
                packageBuildConfig = false
            )
        ).run()
        assertThatZip(output).contains("A.class")
        assertThatZip(output).contains("sub/B.class")
        assertThatZip(output).contains("META-INF/a.modules")
        assertThatZip(output).doesNotContain("a.txt")
        assertThatZip(output).doesNotContain("sub/a.txt")
    }

    @Test
    fun testIgnoredExplicitly() {
        val output = tmp.newFolder().resolve("output.jar")

        val inputJar = tmp.root.resolve("input.jar")
        ZipOutputStream(inputJar.outputStream()).use {
            it.putNextEntry(ZipEntry("A.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/B.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("a.txt"))
            it.closeEntry()
        }

        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(".*A\\.class$"),
                output = output,
                input = setOf(inputJar),
                packageBuildConfig = false
            )
        ).run()
        assertThatZip(output).doesNotContain("A.class")
        assertThatZip(output).contains("sub/B.class")
    }
}