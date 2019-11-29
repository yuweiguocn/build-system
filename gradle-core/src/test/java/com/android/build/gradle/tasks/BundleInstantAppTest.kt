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

import com.android.SdkConstants.EXT_ZIP
import com.android.build.FilterData
import com.android.build.VariantOutput
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.ModuleMetadata
import com.android.tools.build.apkzlib.zip.ZFile
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.stream.Collectors

class BundleInstantAppTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var bundleDirectory: File

    @Mock
    internal lateinit var globalScope: GlobalScope
    @Mock
    internal lateinit var variantScope: VariantScope
    @Mock
    internal lateinit var variantConfiguration: GradleVariantConfiguration

    private fun createFile(name: String, parent: File): File {
        return File(parent.path + File.separator + name).apply {
            createNewFile()
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        bundleDirectory = temporaryFolder.newFolder()

        val apkDirectory1 = temporaryFolder.newFolder("apkDirectory1")
        val apkDirectory2 = temporaryFolder.newFolder("apkDirectory2")

        val apkInfo =
            ApkData.of(
                VariantOutput.OutputType.MAIN,
                ImmutableList.of<FilterData>(),
                12345
            )

        BuildElements(
            ImmutableList.of(
                BuildOutput(
                    InternalArtifactType.APK,
                    apkInfo,
                    createFile("1.apk", apkDirectory1)
                ),
                BuildOutput(
                    InternalArtifactType.APK,
                    apkInfo,
                    createFile("2.apk", apkDirectory1)
                )
            )
        ).save(apkDirectory1)

        BuildElements(
            ImmutableList.of(
                BuildOutput(
                    InternalArtifactType.APK,
                    apkInfo,
                    createFile("3.apk", apkDirectory2)
                )
            )
        ).save(apkDirectory2)

        val applicationFile = temporaryFolder.newFile(ModuleMetadata.PERSISTED_FILE_NAME)

        ModuleMetadata("213", "1", "1", true).save(applicationFile)

        `when`<String>(globalScope.projectBaseName).thenReturn("Bundle")

        `when`<String>(variantConfiguration.baseName).thenReturn("Test")

        `when`<String>(variantScope.fullVariantName).thenReturn("test")
        `when`<GlobalScope>(variantScope.globalScope).thenReturn(globalScope)
        `when`<GradleVariantConfiguration>(variantScope.variantConfiguration).thenReturn(
            variantConfiguration
        )
        `when`<FileCollection>(
            variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.MODULE,
                AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION
            )
        ).thenReturn(FakeFileCollection(ImmutableList.of(applicationFile)))

        `when`<FileCollection>(
            variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.MODULE,
                AndroidArtifacts.ArtifactType.APK
            )
        ).thenReturn(FakeFileCollection(ImmutableList.of(apkDirectory1, apkDirectory2)))
    }

    @Test
    fun bundlingApkFiles() {
        val creationAction = BundleInstantApp.CreationAction(variantScope, bundleDirectory)

        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()

        val task = project.tasks.create("bundleInstantApp", BundleInstantApp::class.java)

        creationAction.configure(task)

        task.taskAction()

        val output = bundleDirectory.listFiles()

        assertThat(output.size).isEqualTo(2)

        var zippedFile = output[0]
        var jsonFile = output[1]

        if (zippedFile.extension != EXT_ZIP) {
            zippedFile = jsonFile.also { jsonFile = zippedFile }
        }

        assertThat(zippedFile.name).isEqualTo("Bundle-Test.zip")
        assertThat(jsonFile.name).isEqualTo("instant-app.json")

        val filesNames = ImmutableSet.of("1.apk", "2.apk", "3.apk")

        assertThat(
            ZFile.openReadOnly(zippedFile).entries().stream().map { it.centralDirectoryHeader.name }.collect(
                Collectors.toSet()
            ) as Set<*>
        ).containsExactlyElementsIn(filesNames)
    }
}