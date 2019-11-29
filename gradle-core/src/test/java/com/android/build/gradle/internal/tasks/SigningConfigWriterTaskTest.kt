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

import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder

import com.android.build.gradle.internal.dsl.SigningConfig
import java.io.File
import java.io.IOException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.gradle.api.provider.Provider
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.FileReader

/** Tests for the [SigningConfigWriterTask]  */
class SigningConfigWriterTaskTest {
    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    internal lateinit var project: Project
    internal lateinit var task: SigningConfigWriterTask
    @Mock
    lateinit var outputDirectoryProvider : Provider<Directory>
    @Mock lateinit var outputDirectoryMock : Directory
    lateinit var outputDirectory : File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val testDir = temporaryFolder.newFolder()
        outputDirectory = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        Mockito.`when`(outputDirectoryProvider.get()).thenReturn(outputDirectoryMock)
        Mockito.`when`(outputDirectoryMock.asFile).thenReturn(outputDirectory)
        task = project.tasks.create("test", SigningConfigWriterTask::class.java)
        task.outputDirectory = outputDirectoryProvider
    }

    @Test
    @Throws(IOException::class)
    fun testTask() {
        val signingConfig = SigningConfig("signingConfig_name")
        signingConfig.storePassword = "foobar"
        signingConfig.isV1SigningEnabled = true
        task.signingConfig = signingConfig

        task.fullTaskAction()
        val files = outputDirectory.listFiles()
        assertThat(files).hasLength(1)

        val config = SigningConfigMetadata.load(files[0])
        assertThat(config).isEqualTo(task.signingConfig)
    }
}
