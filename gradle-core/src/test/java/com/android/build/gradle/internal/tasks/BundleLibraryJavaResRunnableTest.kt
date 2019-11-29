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

class BundleLibraryJavaResRunnableTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()
    lateinit var workers: WorkerExecutorFacade

    @Before
    fun setUp() {
        workers = ExecutorServiceAdapter(MoreExecutors.newDirectExecutorService())
    }

    @Test
    fun testResourcesCopied() {
        val output = tmp.newFolder().resolve("output.jar")
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("a.txt").createNewFile()
                dir.resolve("b.txt").createNewFile()
                dir.resolve("sub").also {
                    it.mkdir()
                    it.resolve("c.txt").createNewFile()
                }
            }
        )
        BundleLibraryJavaResRunnable(BundleLibraryJavaResRunnable.Params(output, input)).run()
        assertThatZip(output).contains("a.txt")
        assertThatZip(output).contains("b.txt")
        assertThatZip(output).contains("sub/c.txt")
    }

    @Test
    fun testClassesIgnored() {
        val output = tmp.newFolder().resolve("output.jar")
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("a.txt").createNewFile()
                dir.resolve("A.class").createNewFile()
            }
        )
        BundleLibraryJavaResRunnable(BundleLibraryJavaResRunnable.Params(output, input)).run()
        assertThatZip(output).contains("a.txt")
        assertThatZip(output).doesNotContain("A.class")
    }

    @Test
    fun testResFromJars() {
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
        }

        BundleLibraryJavaResRunnable(BundleLibraryJavaResRunnable.Params(output, setOf(inputJar))).run()
        assertThatZip(output).contains("a.txt")
        assertThatZip(output).contains("sub/a.txt")
        assertThatZip(output).doesNotContain("A.class")
        assertThatZip(output).doesNotContain("sub/B.class")
    }

    @Test
    fun testResFromDirWithJar() {
        val output = tmp.newFolder().resolve("output.jar")

        val inputDirWithJar = tmp.newFolder().also {
            ZipOutputStream(it.resolve("subJar.jar").outputStream()).use {
                it.putNextEntry(ZipEntry("A.class"))
                it.closeEntry()
            }
        }

        BundleLibraryJavaResRunnable(BundleLibraryJavaResRunnable.Params(output, setOf(inputDirWithJar))).run()
        assertThatZip(output).contains("subJar.jar")
        assertThatZip(output).doesNotContain("A.class")
    }
}