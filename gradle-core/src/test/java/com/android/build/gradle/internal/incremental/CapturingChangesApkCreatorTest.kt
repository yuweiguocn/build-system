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

package com.android.build.gradle.internal.incremental

import com.android.testutils.truth.FileSubject
import com.android.tools.build.apkzlib.zfile.ApkCreator
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.FileReader

class CapturingChangesApkCreatorTest {
    @get:Rule
    val sourceFolder = TemporaryFolder()

    @get:Rule
    val destFolder = TemporaryFolder()

    @Mock
    lateinit var creationData : ApkCreatorFactory.CreationData

    @Mock
    lateinit var delegatedApkCreator: ApkCreator

    lateinit var apkCreator: CapturingChangesApkCreator

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(creationData.apkPath).thenReturn(destFolder.root)
        apkCreator = CapturingChangesApkCreator(creationData, delegatedApkCreator)
    }

    @Test
    fun simpleAdd() {
        val listOfEntries =
            listOf("inFile1", "foo/inFile2", "foo/bar/inFile3", "foo/bar/inFile4")

        val entries = listOfEntries.map { it to writeFile(it) }
            .toMap()

        apkCreator.close()

        assertThat(apkCreator.changedItems
            .map(ApkChangeList.ChangedItem::path))
            .containsExactlyElementsIn(listOfEntries)

        apkCreator.changedItems.forEach { changedItem ->
            assertThat(changedItem.lastModified).isEqualTo(
                entries[changedItem.path]?.lastModified())
        }

        Mockito.verify(delegatedApkCreator, times(listOfEntries.size)).writeFile(
            ArgumentMatchers.any(), ArgumentMatchers.any())
        Mockito.verify(delegatedApkCreator).close()

        testChangesList(destFolder.root, listOfEntries, listOf())
    }

    @Test
    fun simpleDelete() {
        val listOfEntries =
            listOf("inFile1", "foo/inFile2")
        listOfEntries.forEach { apkCreator.deleteFile(it) }
        apkCreator.close()

        assertThat(apkCreator.deletedItems.map(ApkChangeList.ChangedItem::path))
            .containsExactlyElementsIn(listOfEntries)
        Mockito.verify(delegatedApkCreator, times(listOfEntries.size)).deleteFile(
            ArgumentMatchers.any())
        Mockito.verify(delegatedApkCreator).close()
    }

    @Test
    fun simpleJarAdd() {
        val listOfEntries = listOf("inFile1", "inFile2", "foo/inFile3", "foo/inFile4")
        val zipFile = sourceFolder.newFile("simple.zip")
        FolderBasedApkCreatorTest.createJarFile(zipFile, listOfEntries)

        apkCreator.writeZip(zipFile, null,null)
        apkCreator.close()

        assertThat(apkCreator.changedItems.map(ApkChangeList.ChangedItem::path))
            .containsExactlyElementsIn(listOfEntries)

        apkCreator.changedItems.forEach { changedItem ->
            assertThat(changedItem.lastModified).isEqualTo(
                zipFile.lastModified())
        }

        Mockito.verify(delegatedApkCreator).writeZip(
            ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        Mockito.verify(delegatedApkCreator).close()

        testChangesList(destFolder.root, listOfEntries, listOf())

    }

    private fun writeFile(name: String): File {
        val file = File(sourceFolder.root, name)
        file.parentFile.mkdirs()
        Truth.assertThat(file.parentFile.exists()).isTrue()
        FileUtils.createFile(file, "Content: $name")
        apkCreator.writeFile(file, name)
        return file
    }

    private fun testChangesList(folder: File, changes: List<String>, deletions: List<String>) {
        val changesFile = File(folder, ApkChangeList.CHANGE_LIST_FN)
        FileSubject.assertThat(changesFile).exists()

        val changeList = ApkChangeList.read(FileReader(changesFile))

        assertThat(changeList.changes.map(ApkChangeList.ChangedItem::path)).containsExactlyElementsIn(changes)
        assertThat(changeList.deletions.map(ApkChangeList.ChangedItem::path)).containsExactlyElementsIn(deletions)
    }
}