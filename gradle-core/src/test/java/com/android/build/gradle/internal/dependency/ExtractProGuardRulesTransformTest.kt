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

package com.android.build.gradle.internal.dependency

import com.android.testutils.truth.FileSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractProGuardRulesTransformTest {

    private val slash = File.separator

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    private lateinit var extractTransform: ExtractProGuardRulesTransform

    @Before
    fun setUp() {
        extractTransform = ExtractProGuardRulesTransform()
        extractTransform.outputDirectory = tmp.newFolder()
    }

    @Test
    fun testNoRules_UnrelatedFile() {
        val jarFile = createZip("bar.txt" to "hello")

        extractTransform.transform(jarFile)

        assertThat(producedFileNames).isEmpty()
    }

    @Test
    fun testNoRules_FolderExists() {
        val jarFile = createZip("META-INF/proguard" to null)

        extractTransform.transform(jarFile)

        assertThat(producedFileNames).isEmpty()
    }

    @Test
    fun testSingleRuleFile() {
        val jarFile = createZip("META-INF/proguard/foo.txt" to "bar")

        extractTransform.transform(jarFile)

        assertThat(producedFileNames).containsExactly("META-INF${slash}proguard${slash}foo.txt")
        FileSubject.assertThat(producedFile("META-INF${slash}proguard${slash}foo.txt")).hasContents("bar")
    }

    @Test
    fun testSingleRuleFile_startingWithSlash() {
        val jarFile = createZip("/META-INF/proguard/foo.txt" to "bar")

        extractTransform.transform(jarFile)

        assertThat(producedFileNames).containsExactly("META-INF${slash}proguard${slash}foo.txt")
        FileSubject.assertThat(producedFile("META-INF${slash}proguard${slash}foo.txt")).hasContents("bar")
    }

    @Test
    fun testMultipleRuleFiles() {
        val jarFile = createZip(
            "META-INF/proguard/bar.txt" to "hello",
            "META-INF/proguard/foo.pro" to "goodbye")

        extractTransform.transform(jarFile)

        assertThat(producedFileNames).containsExactly("META-INF${slash}proguard${slash}foo.pro", "META-INF${slash}proguard${slash}bar.txt")
        FileSubject.assertThat(producedFile("META-INF${slash}proguard${slash}foo.pro")).hasContents("goodbye")
        FileSubject.assertThat(producedFile("META-INF${slash}proguard${slash}bar.txt")).hasContents("hello")
    }

    private fun producedFile(relativePath: String) = extractTransform.outputDirectory
        .walk()
        .single { it.relativeTo(extractTransform.outputDirectory).path == relativePath }

    private val producedFileNames get() = extractTransform.outputDirectory
        .walk()
        .filter { !it.isDirectory }
        .map { it.relativeTo(extractTransform.outputDirectory).path }
        .toList()

    private fun createZip(vararg entries: Pair<String, String?>): File {
        val zipFile = tmp.newFile()
        ZipOutputStream(FileOutputStream(zipFile)).use {
            for (entry in entries) {
                it.putNextEntry(ZipEntry(entry.first))
                if (entry.second != null) {
                    it.write(entry.second!!.toByteArray())
                }
                it.closeEntry()
            }
        }
        return zipFile
    }
}