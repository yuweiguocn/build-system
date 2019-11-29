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

package com.android.build.gradle.internal.cxx.configure

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IdempotentFileWriterTest {

    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    private lateinit var folder: File
    private lateinit var file: File

    @Before
    fun setup() {
        folder = tmpFolder.newFolder("folder")
        file =  File(folder, "my-file")
    }

    @Test
    fun testWritingWorks() {
        val writer = IdempotentFileWriter()
        writer.addFile(file.path, "my-content")
        assertThat(writer.write()).containsExactly(file.path)
        assertThat(file.readText()).isEqualTo("my-content")
    }

    @Test
    fun testWritingSkipsSameContent() {
        val writer = IdempotentFileWriter()
        file.writeText("my-content")
        writer.addFile(file.path, "my-content")
        assertThat(writer.write()).isEmpty()
        assertThat(file.readText()).isEqualTo("my-content")
    }

    @Test
    fun testWritingReplacesOldContent() {
        val writer = IdempotentFileWriter()
        file.writeText("my-content-old")
        writer.addFile(file.path, "my-content")
        assertThat(writer.write()).containsExactly(file.path)
        assertThat(file.readText()).isEqualTo("my-content")
    }

    @Test
    fun testSecondWriteWins() {
        val writer = IdempotentFileWriter()
        writer.addFile(file.path, "my-content-1")
        writer.addFile(file.path, "my-content-2")
        assertThat(writer.write()).containsExactly(file.path)
        assertThat(file.readText()).isEqualTo("my-content-2")
    }
}