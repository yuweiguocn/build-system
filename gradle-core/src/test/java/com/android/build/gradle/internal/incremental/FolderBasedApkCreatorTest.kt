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

import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.utils.FileUtils
import com.google.common.collect.FluentIterable
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Suppress("MemberVisibilityCanBePrivate")
class FolderBasedApkCreatorTest {

    @get:Rule val sourceFolder = TemporaryFolder()

    @get:Rule val destFolder = TemporaryFolder()
    @Mock
    lateinit var creationData : ApkCreatorFactory.CreationData

    lateinit var apkCreator: FolderBasedApkCreator

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(creationData.apkPath).thenReturn(destFolder.root)
        apkCreator =
                FolderBasedApkCreator(creationData)
    }

    @Test
    fun simpleWriting() {
        val listOfEntries =
            listOf("inFile1", "foo/inFile2", "foo/bar/inFile3", "foo/bar/inFile4")
        listOfEntries.forEach { writeFile(it) }

        checkFolderContent(destFolder.root, listOfEntries)
    }

    @Test
    fun incrementalWriting() {
        writeFile("inFile1")
        writeFile("foo/inFile2")
        var copiedFiles = getFilesInFolder(destFolder.root)
        assertThat(copiedFiles).hasSize(2)

        apkCreator.close()
        writeFile("foo/bar/inFile3")
        writeFile("foo/bar/inFile4")

        copiedFiles = getFilesInFolder(destFolder.root)
        assertThat(copiedFiles).hasSize(4)
    }

    @Test
    fun simpleRemoving() {
        val listOfEntries =
            listOf("inFile1", "foo/inFile2", "foo/bar/inFile3", "foo/bar/inFile4")
        listOfEntries.forEach { writeFile(it) }

        apkCreator.close()
        checkFolderContent(destFolder.root, listOfEntries)

        apkCreator =
                FolderBasedApkCreator(creationData)
        apkCreator.deleteFile("foo/bar/inFile3")
        apkCreator.close()
        checkFolderContent(destFolder.root,
            listOf("inFile1", "foo/inFile2", "foo/bar/inFile4"))
    }

    @Test
    fun addAndRemoving() {
        val listOfEntries =
            listOf("inFile1", "foo/inFile2", "foo/bar/inFile3", "foo/bar/inFile4")
        listOfEntries.forEach { writeFile(it) }

        apkCreator.close()
        checkFolderContent(destFolder.root, listOfEntries)

        apkCreator =
                FolderBasedApkCreator(creationData)
        writeFile("new/file")
        apkCreator.deleteFile("foo/bar/inFile3")
        apkCreator.close()
        checkFolderContent(destFolder.root,
            listOf("inFile1", "new/file", "foo/inFile2", "foo/bar/inFile4"))
    }

    @Test
    fun singleEntryZipFileWriting() {
        val zipFile = sourceFolder.newFile("simple.zip")
        JarOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use {
            addJarEntry(it, "inFile1")
        }

        apkCreator.writeZip(zipFile, null,null)
        apkCreator.close()

        checkFolderContent(destFolder.root, listOf("inFile1"))
    }

    @Test
    fun multipleEntriesZipFileWriting() {
        val listOfEntries = listOf("inFile1", "inFile2", "foo/inFile3", "foo/inFile4")
        val zipFile = sourceFolder.newFile("simple.zip")
        createJarFile(zipFile, listOfEntries)

        apkCreator.writeZip(zipFile, null,null)
        apkCreator.close()

        checkFolderContent(destFolder.root, listOfEntries)
    }

    @Test
    fun multipleEntriesZipFilesWriting() {
        val firstEntries = listOf("inFile1", "inFile2", "foo/inFile3", "foo/inFile4")
        val firstJar = sourceFolder.newFile("first.zip")
        createJarFile(firstJar, firstEntries)

        val secondEntries = listOf("inFile5", "inFile6", "foo/inFile7", "foo/inFile8")
        val secondJar = sourceFolder.newFile("second.zip")
        createJarFile(secondJar, secondEntries)

        apkCreator.writeZip(firstJar, null,null)
        apkCreator.writeZip(secondJar, null,null)
        apkCreator.close()

        checkFolderContent(destFolder.root, firstEntries.plus(secondEntries))
    }

    @Test
    fun mixedFiles() {
        val firstEntries = listOf("inFile1", "inFile2", "foo/inFile3", "foo/inFile4")
        val firstJar = sourceFolder.newFile("first.zip")
        createJarFile(firstJar, firstEntries)
        apkCreator.writeZip(firstJar, null,null)

        val secondEntries = listOf("inFile5", "inFile6", "foo/inFile7", "foo/inFile8")
        secondEntries.forEach { writeFile(it) }

        apkCreator.close()

        checkFolderContent(destFolder.root, firstEntries.plus(secondEntries))
    }

    @Test
    fun deleteFilesFromZip() {
        val zipEntries = listOf("inFile1", "inFile2", "foo/inFile3", "foo/inFile4")
        val firstJar = sourceFolder.newFile("first.zip")
        createJarFile(firstJar, zipEntries)
        apkCreator.writeZip(firstJar, null,null)
        apkCreator.close()
        checkFolderContent(destFolder.root, zipEntries)

        apkCreator =
                FolderBasedApkCreator(creationData)
        apkCreator.deleteFile("foo/inFile3")
        apkCreator.close()

        checkFolderContent(destFolder.root, listOf("inFile1", "inFile2", "foo/inFile4"))
    }

    @Test(expected = AssertionError::class)
    fun useFileInsteadOfFolder() {
        val newFile = sourceFolder.newFile("filename")
        Mockito.`when`(creationData.apkPath).thenReturn(newFile)

        FolderBasedApkCreator(creationData)
    }

    @Test
    fun zipFileWithIgnoredContentTest() {
        val zipEntries = listOf(
            "inFile1.dex",
            "inFile2.dex",
            "foo/inFile3.txt", "foo/inFile4.dex")

        val firstJar = sourceFolder.newFile("first.zip")
        createJarFile(firstJar, zipEntries)
        apkCreator.writeZip(firstJar, null) { t -> t!!.endsWith(".txt")}
        apkCreator.close()

        checkFolderContent(destFolder.root, listOf("inFile1.dex", "inFile2.dex", "foo/inFile4.dex"))

    }

    private fun getFilesInFolder(folder: File) =
        FluentIterable.from(Files.fileTraverser().breadthFirst(folder))
            .filter(File::isFile)
            .filter{it.name != ApkChangeList.CHANGE_LIST_FN}
            .map(File::getAbsolutePath)
            .toList()

    private fun writeFile(name: String): File {
        val file = File(sourceFolder.root, name)
        file.parentFile.mkdirs()
        assertThat(file.parentFile.exists()).isTrue()
        FileUtils.createFile(file, "Content: $name")
        apkCreator.writeFile(file, name)
        return file
    }

    private fun checkFolderContent(folder: File, expectedEntries: List<String>) {
        val filesInFolder = getFilesInFolder(folder)
        assertThat(filesInFolder).containsExactlyElementsIn(
            expectedEntries.map{ "${folder.absolutePath}/$it".replace('/', File.separatorChar) })
    }

    companion object {
        fun createJarFile(zipFile: File, expectedEntries: List<String>) {
            JarOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use {
                expectedEntries.forEach { entryName ->
                    addJarEntry(it, entryName)
                }
            }
        }

        private fun addJarEntry(jar: JarOutputStream, name: String) {
            jar.putNextEntry(JarEntry(name))
            jar.writer(Charsets.UTF_8).append("Content: $name")
            jar.closeEntry()
        }
    }
}
