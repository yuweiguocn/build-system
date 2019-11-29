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

package com.android.build.gradle.tasks

import com.android.builder.internal.compiler.AidlProcessor
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.io.File
import java.nio.file.Path
import java.util.stream.Collectors

class AidlCompileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createFile(name: String, parent: File) {
        File(parent.path + File.separator + name).createNewFile()
    }

    @Test
    fun testAidlCompileRunnable() {
        val sourceFolder = temporaryFolder.newFolder()
        createFile("1.aidl", sourceFolder)
        createFile("2.aidl", sourceFolder)
        createFile("3.aidl", sourceFolder)
        createFile("noise.txt", sourceFolder)

        val fileNames = ImmutableSet.of("1.aidl", "2.aidl", "3.aidl")

        val processor = Mockito.mock(AidlProcessor::class.java)
        val startDirCaptor = ArgumentCaptor.forClass(Path::class.java)
        val pathCaptor = ArgumentCaptor.forClass(Path::class.java)

        val task =
            AidlCompile.AidlCompileRunnable(AidlCompile.AidlCompileParams(sourceFolder, processor))
        task.run()

        Mockito.verify(processor, Mockito.times(3))
            .call(startDirCaptor.capture(), pathCaptor.capture())

        startDirCaptor.allValues.forEach {
            Truth.assertThat(it.toString()).isEqualTo(sourceFolder.toString())
        }

        Truth.assertThat(
            pathCaptor.allValues.stream().map { it.fileName.toString() }.collect(
                Collectors.toSet()
            ) as Set<*>
        ).containsExactlyElementsIn(fileNames)
    }
}