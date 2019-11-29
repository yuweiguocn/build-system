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

package com.android.build.gradle.internal.tasks.featuresplit

import com.android.sdklib.AndroidVersion
import com.android.testutils.truth.FileSubject
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException
import java.util.Arrays

/** Tests for the [FeatureSetMetadataWriterTask] class  */
@RunWith(Parameterized::class)
class FeatureSetMetadataWriterTaskTest(val minSdkVersion: Int) {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    lateinit var project: Project
    lateinit var task: FeatureSetMetadataWriterTask

    @Mock lateinit var fileCollection: FileCollection
    @Mock lateinit var fileTree: FileTree
    val files = mutableSetOf<File>()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "minSdkVersion={0}")
        fun getParameters(): Collection<Array<Any>> {
            return Arrays.asList(
                arrayOf<Any>(AndroidVersion.VersionCodes.LOLLIPOP),
                arrayOf<Any>(AndroidVersion.VersionCodes.O)
            )
        }
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {

        MockitoAnnotations.initMocks(this)
        val testDir = temporaryFolder.newFolder()

        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", FeatureSetMetadataWriterTask::class.java)
        task.outputFile = File(temporaryFolder.newFolder(), FeatureSetMetadata.OUTPUT_FILE_NAME)
        task.inputFiles = fileCollection
        task.minSdkVersion = minSdkVersion

        `when`(fileCollection.asFileTree).thenReturn(fileTree)
        `when`(fileTree.files).thenReturn(files)
    }

    @Test
    @Throws(IOException::class)
    fun testTask() {
        val inputDirs = ImmutableSet.builder<File>()
        for (i in 0..4) {
            inputDirs.add(generateInputDir("id_$i", "foo.bar.baz$i"))
        }
        files.addAll(inputDirs.build())

        task.fullTaskAction()
        FileSubject.assertThat(task.outputFile).isFile()

        val loaded = FeatureSetMetadata.load(task.outputFile)
        for (i in 0..4) {
            assertThat(loaded.getResOffsetFor("id_$i")).isEqualTo(
                if (minSdkVersion < AndroidVersion.VersionCodes.O)
                        FeatureSetMetadata.BASE_ID - i - 1 else
                        FeatureSetMetadata.BASE_ID + i + 1)
        }
    }

    @Throws(IOException::class)
    private fun generateInputDir(id: String, appId: String): File {
        val inputDir = temporaryFolder.newFolder()
        val featureSplitDeclaration = FeatureSplitDeclaration(id, appId)
        featureSplitDeclaration.save(inputDir)
        return FeatureSplitDeclaration.getOutputFile(inputDir)
    }
}
