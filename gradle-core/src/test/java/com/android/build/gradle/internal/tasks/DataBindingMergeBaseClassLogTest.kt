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

import android.databinding.tool.DataBindingBuilder.BINDING_CLASS_LIST_SUFFIX
import android.databinding.tool.DataBindingBuilder.INCREMENTAL_BINDING_CLASSES_LIST_DIR
import android.databinding.tool.DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeIncrementalTaskInputs
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeBaseClassLogDelegate
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockitoAnnotations
import java.util.concurrent.ForkJoinPool

private fun getFileInDataBindingFolder(directory: String, inputDir: File, filename: String): File {
    val dataBindingFolder = File(inputDir, directory)
    return File(dataBindingFolder, filename)
}

class DataBindingMergeBaseClassLogTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var expectedOutFolder: File
    private val executor = ExecutorServiceAdapter(ForkJoinPool.commonPool())
    private lateinit var outputDir: Provider<Directory>

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        expectedOutFolder = temporaryFolder.newFolder()
        outputDir = FakeGradleProvider(FakeGradleDirectory(expectedOutFolder))
    }

    @Test
    fun nonIncrementalFromFolder() {
        val classList1 = "class_list_1$BINDING_CLASS_LIST_SUFFIX"
        val classList2 = "class_list_2$BINDING_CLASS_LIST_SUFFIX"

        val folder = createFolder(
            "blah",
            ImmutableList.of(classList1, classList2))

        val delegate = DataBindingMergeBaseClassLogDelegate(FakeFileCollection(), FakeFileCollection(folder), outputDir)
        delegate.doFullRun(executor)

        assertThat(collectOutputs()).containsExactly(classList1, classList2)
    }

    @Test
    fun incrementalFolderWithChanges() {
        val classList1 = "class_list_1$BINDING_CLASS_LIST_SUFFIX"
        val classList2 = "class_list_2$BINDING_CLASS_LIST_SUFFIX"
        val classList3 = "class_list_3$BINDING_CLASS_LIST_SUFFIX"
        val classList4 = "class_list_4$BINDING_CLASS_LIST_SUFFIX"
        val classList5 = "class_list_5$BINDING_CLASS_LIST_SUFFIX"

        val classListInputDir = createFolder("blah", ImmutableList.of(classList1));

        val delegate = DataBindingMergeBaseClassLogDelegate(FakeFileCollection(), FakeFileCollection(classListInputDir), outputDir)
        delegate.doFullRun(executor)

        // now run another invocation w/ a new file in that folder
        val unwanted =
                createInFolder(INCREMENTAL_BIN_AAR_DIR, classListInputDir, "unnecessary.tada")
        val wanted =
                createInFolder(INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir, classList2)

        delegate.doIncrementalRun(executor, mapOf(unwanted to FileStatus.NEW, wanted to FileStatus.NEW))

        assertThat(collectOutputs()).containsExactly(classList1, classList2)

        // change file contents. literally
        val classList1Updated =
                getFileInDataBindingFolder(
                        INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir, classList1)
        FileUtils.writeStringToFile(classList1Updated, "updated-class-list")

        delegate.doIncrementalRun(executor, mapOf(classList1Updated to FileStatus.CHANGED))

        assertThat(collectOutputs()).containsExactly(classList1, classList2)

        val classListOutFile = findOutputFile(classList1Updated.name)

        assertThat(FileUtils.readFileToString(classListOutFile)).isEqualTo("updated-class-list")

        // now delete one file
        delegate.doIncrementalRun(executor, mapOf(classList1Updated to FileStatus.REMOVED))

        assertThat(collectOutputs()).containsExactly(classList2)

        // introduce another folder
        val classListInputDir2 = createFolder("foobar", ImmutableList.of("d.qq"));
        val classListInput2 =
                createInFolder(
                        INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir2, classList3)

        delegate.doIncrementalRun(executor, mapOf(classListInput2 to FileStatus.NEW))

        assertThat(collectOutputs()).containsExactly(classList2, classList3)

        // more new input in both
        val newInInput3 =
                createInFolder(INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir, classList4)
        val newInInput4 =
                createInFolder(
                        INCREMENTAL_BINDING_CLASSES_LIST_DIR, classListInputDir2, classList5)

        delegate.doIncrementalRun(executor, mapOf(newInInput3 to FileStatus.NEW, newInInput4 to FileStatus.NEW))

        assertThat(collectOutputs()).containsExactly(classList2, classList3, classList4, classList5)
    }

    private fun collectOutputs(): Set<String> {
        return FileUtils.listFiles(expectedOutFolder, TrueFileFilter.TRUE, TrueFileFilter.TRUE)
                .map(File::getName).toSet()
    }

    private fun findOutputFile(name: String): File {
        return FileUtils.listFiles(expectedOutFolder, TrueFileFilter.TRUE, TrueFileFilter.TRUE).first { it.name == name }

    }

    private fun createFolder(fileName: String, files: List<String>):File {
        val folder = temporaryFolder.newFolder("classlist-$fileName")
        files.forEach {
            FileUtils.touch(getFileInDataBindingFolder(INCREMENTAL_BINDING_CLASSES_LIST_DIR, folder, it))
        }
        return folder
    }

    private fun createInFolder(folderName: String, inputDir: File, filename: String) : File {
        val file = getFileInDataBindingFolder(folderName, inputDir, filename)
        FileUtils.touch(file)
        return file
    }
}