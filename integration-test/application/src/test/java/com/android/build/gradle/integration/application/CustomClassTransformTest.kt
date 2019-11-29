/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.application.testData.TestDependency
import com.android.build.gradle.integration.application.testData.TestTransform
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.TestInputsGenerator
import com.google.common.base.Charsets
import com.google.common.io.ByteStreams
import org.junit.Rule
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CustomClassTransformTest {

    @Rule
    @JvmField
    val project =
        GradleTestProject.builder().fromTestApp(
            MinimalSubProject.app("com.example.test")).create()

    @Test
    fun testCustomClassTransform() {

        // Create a fake dependency jar file
        val dependencyJarFile = project.testDir.resolve("fake_dependency.jar")
        TestInputsGenerator.pathWithClasses(
            dependencyJarFile.toPath(), listOf(TestDependency::class.java))

        // Create a custom class transform jar file
        val name = TestTransform::class.java.name
        val entry = name.replace('.', '/') + ".class"
        val resource = "/$entry"
        val url = TestTransform::class.java.getResource(resource)
        val jarFile = File(project.testDir, "transform.jar")
        ZipOutputStream(FileOutputStream(jarFile)).use { zip ->
            var e = ZipEntry(entry)
            zip.putNextEntry(e)
            url.openStream().use {
                ByteStreams.copy(it, zip)
            }
            zip.closeEntry()

            e = ZipEntry("META-INF/services/java.util.function.BiConsumer")
            zip.putNextEntry(e)
            zip.write(name.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            e = ZipEntry("dependencies/fake_dependency.jar")
            zip.putNextEntry(e)
            BufferedInputStream(FileInputStream(dependencyJarFile)).use {
                ByteStreams.copy(it, zip)
            }
            zip.closeEntry()
        }

        // run with ENABLE_DEXING_ARTIFACT_TRANSFORM = true to ensure it gets disabled by
        // android.advanced.profiling.transforms=<path to jar file>
        project.executor()
            .with(StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS, jarFile.absolutePath)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, true)
            .run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
            .containsClass(
                "Lcom/android/build/gradle/integration/application/testData/TestDependency;")
    }
}
