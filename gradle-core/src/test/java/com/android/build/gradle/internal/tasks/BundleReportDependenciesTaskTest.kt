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
import com.android.build.api.artifact.BuildableArtifact
import org.gradle.api.file.FileCollection
import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library
import com.android.tools.build.libraries.metadata.LibraryDependencies
import com.android.tools.build.libraries.metadata.MavenLibrary
import com.android.tools.build.libraries.metadata.ModuleDependencies
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import com.google.common.collect.ImmutableSet

class BundleReportDependenciesTaskTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    internal lateinit var project: Project
    lateinit var task: BundleReportDependenciesTask
    lateinit var dependenciesFile : File
    lateinit var baseDepsFile : File
    lateinit var feature1File : File
    lateinit var feature2File : File
    lateinit var featureDepsFiles : Set<File>
    lateinit var baseDepsFiles : Set<File>

    @Mock private lateinit var baseDeps: BuildableArtifact
    @Mock private lateinit var featureDeps: FileCollection

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        dependenciesFile = temporaryFolder.newFile()
        baseDepsFile = temporaryFolder.newFile()
        feature1File = temporaryFolder.newFile()
        feature2File = temporaryFolder.newFile()
        featureDepsFiles = ImmutableSet.of(feature1File, feature2File)
        baseDepsFiles = ImmutableSet.of(baseDepsFile)

        Mockito.`when`(baseDeps.files).thenReturn(baseDepsFiles)
        Mockito.`when`(baseDeps.iterator()).thenReturn(baseDepsFiles.iterator())
        Mockito.`when`(featureDeps.files).thenReturn(featureDepsFiles)

        val testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        task = project.tasks.create("test", BundleReportDependenciesTask::class.java)
        task.dependenciesList = dependenciesFile
    }

    @Test
    fun combineDepsTest() {
        val lib1 = Library.newBuilder().setMavenLibrary(
            MavenLibrary.newBuilder()
                .setGroupId("foo")
                .setArtifactId("baz")
                .setVersion("1.2")
                .build())
            .build()
        val lib2 = Library.newBuilder().setMavenLibrary(
            MavenLibrary.newBuilder()
                .setGroupId("bar")
                .setArtifactId("beef")
                .setVersion("1.1")
                .build())
            .build()
        val lib3 = Library.newBuilder().setMavenLibrary(
            MavenLibrary.newBuilder()
                .setGroupId("dead")
                .setArtifactId("beef")
                .setVersion("1.1")
                .build())
            .build()

        val baseAppDeps = AppDependencies.newBuilder()
            .addLibrary(lib1)
            .addLibrary(lib2)
            .addModuleDependencies(
                ModuleDependencies.newBuilder()
                    .setModuleName("base")
                    .addDependencyIndex(0)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(0)
                    .addLibraryDepIndex(1)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(1)
                    .build())
            .build()
        val featureDep1 = AppDependencies.newBuilder()
            .addLibrary(lib2)
            .addModuleDependencies(
                ModuleDependencies.newBuilder()
                    .setModuleName("feature1")
                    .addDependencyIndex(0)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(0)
                    .build())
            .build()
        val featureDep2 = AppDependencies.newBuilder()
            .addLibrary(lib3)
            .addLibrary(lib2)
            .addModuleDependencies(
                ModuleDependencies.newBuilder()
                    .setModuleName("feature2")
                    .addDependencyIndex(0)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(0)
                    .addLibraryDepIndex(1)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(1)
                    .build())
            .build()
        baseAppDeps.writeDelimitedTo(FileOutputStream(baseDepsFile))
        featureDep1.writeDelimitedTo(FileOutputStream(feature1File))
        featureDep2.writeDelimitedTo(FileOutputStream(feature2File))
        val expected = AppDependencies.newBuilder()
            .addLibrary(lib1)
            .addLibrary(lib2)
            .addLibrary(lib3)
            .addModuleDependencies(
                ModuleDependencies.newBuilder()
                    .setModuleName("base")
                    .addDependencyIndex(0)
                    .build())
            .addModuleDependencies(
                ModuleDependencies.newBuilder()
                    .setModuleName("feature1")
                    .addDependencyIndex(1)
                    .build())
            .addModuleDependencies(
                ModuleDependencies.newBuilder()
                    .setModuleName("feature2")
                    .addDependencyIndex(2)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(0)
                    .addLibraryDepIndex(1)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(1)
                    .build())
            .addLibraryDependencies(
                LibraryDependencies.newBuilder()
                    .setLibraryIndex(2)
                    .addLibraryDepIndex(1)
                    .build())
            .build()

        task.baseDeps = baseDeps
        task.featureDeps = featureDeps

        task.writeFile()
        val allDeps = AppDependencies.parseDelimitedFrom(FileInputStream(task.dependenciesList))
        assertThat(allDeps).isEqualTo(expected)
    }
}