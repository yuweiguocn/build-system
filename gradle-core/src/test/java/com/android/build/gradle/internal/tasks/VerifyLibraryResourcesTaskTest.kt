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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.tasks.VerifyLibraryResourcesTask
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.resources.QueueableResourceCompiler
import com.android.utils.FileUtils
import com.google.common.io.Files
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.HashMap

/*
 * Unit tests for {@link VerifyLibraryResourcesTask}.
 */
class VerifyLibraryResourcesTaskTest {

    @get: Rule
    var temporaryFolder = TemporaryFolder()

    private class FakeAapt : QueueableResourceCompiler {

        override fun compile(request: CompileResourceRequest): ListenableFuture<File> {
            val outputPath = compileOutputFor(request)
            Files.copy(request.inputFile, outputPath)
            return Futures.immediateFuture(outputPath)
        }

        override fun close() {}

        override fun compileOutputFor(request: CompileResourceRequest): File {
            return File(request.outputDirectory, request.inputFile.name + "-c")
        }
    }

    @Test
    fun directoriesShouldBeIgnored() {
        val inputs = HashMap<File, FileStatus>()
        val mergedDir = File(temporaryFolder.newFolder("merged"), "release")
        FileUtils.mkdirs(mergedDir)

        val file = File(File(mergedDir, "values"), "file.xml")
        FileUtils.createFile(file, "content")
        assertTrue(file.exists())
        inputs.put(file, FileStatus.NEW)

        val directory = File(mergedDir, "layout")
        FileUtils.mkdirs(directory)
        assertTrue(directory.exists())
        inputs.put(directory, FileStatus.NEW)

        val outputDir = temporaryFolder.newFolder("output")
        val aapt = FakeAapt()

        VerifyLibraryResourcesTask.compileResources(inputs, outputDir, aapt, null, null, mergedDir)

        val fileOut = aapt.compileOutputFor(CompileResourceRequest(file, outputDir, "values"))
        assertTrue(fileOut.exists())

        val dirOut = aapt.compileOutputFor(
                CompileResourceRequest(directory, outputDir, mergedDir.name))
        assertFalse(dirOut.exists())
    }

    @Test
    fun manifestShouldNotBeCompiled() {
        val inputs = HashMap<File, FileStatus>()
        val mergedDir = File(temporaryFolder.newFolder("merged"), "release")
        FileUtils.mkdirs(mergedDir)

        val file = File(File(mergedDir, "values"), "file.xml")
        FileUtils.createFile(file, "content")
        assertTrue(file.exists())
        inputs.put(file, FileStatus.NEW)

        val manifest = File(temporaryFolder.newFolder("merged_manifest"), "AndroidManifest.xml")
        FileUtils.createFile(manifest, "manifest content")
        assertTrue(manifest.exists())
        inputs.put(manifest, FileStatus.NEW)

        val outputDir = temporaryFolder.newFolder("output")
        val aapt = FakeAapt()

        VerifyLibraryResourcesTask.compileResources(inputs, outputDir, aapt, null, null, mergedDir)

        val fileOut = aapt.compileOutputFor(CompileResourceRequest(file, outputDir, "values"))
        assertTrue(fileOut.exists())

        // Real AAPT would fail trying to compile the manifest, but the fake one would just copy it
        // so we need to check that it wasn't copied into the output directory.
        val manifestOut = aapt.compileOutputFor(
                CompileResourceRequest(manifest, outputDir, "merged_manifest"))
        assertFalse(manifestOut.exists())
    }
}
