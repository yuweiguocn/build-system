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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.desugar.resources.TestClass
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.TestInputsGenerator
import com.google.common.collect.Lists
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

private const val CACHE_DIR = "foo"

class RecalculateStackFramesTaskTest {

    @JvmField
    @Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testNonEmptyFileCache() {
        enableJava8()

        createLibrary()

        addLibraryAsDependency()

        project
            .executor()
            .with(BooleanOption.ENABLE_BUILD_CACHE, true)
            .with(BooleanOption.ENABLE_GRADLE_WORKERS, true)
            .with(BooleanOption.ENABLE_D8_DESUGARING, false)
            .with(BooleanOption.ENABLE_R8_DESUGARING, false)
            .with(BooleanOption.ENABLE_R8, false)
            .with(BooleanOption.ENABLE_DESUGAR, true)
            .with(StringOption.BUILD_CACHE_DIR, CACHE_DIR)
            .run("fixStackFramesDebug")

        assertThat(getCacheDir().listFiles().asList().filter { it.isDirectory }).isNotEmpty()
    }

    private fun enableJava8() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """android.compileOptions.sourceCompatibility 1.8
                |android.compileOptions.targetCompatibility 1.8""".trimMargin()
        )
    }

    private fun createLibrary() {
        val lib = project.testDir.toPath().resolve("libs/my-lib.jar")
        Files.createDirectories(lib.parent)
        TestInputsGenerator.pathWithClasses(
            lib, Lists.newArrayList<Class<*>>(TestClass::class.java))
    }

    private fun addLibraryAsDependency() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """dependencies {
                        |compile fileTree(dir: 'libs', include: ['*.jar'])
                    |}""".trimMargin())
    }

    private fun getCacheDir(): File {
        val cacheFolder = project.file(CACHE_DIR).listFiles().find { it.isDirectory }
        assertThat(cacheFolder).isNotNull()
        return cacheFolder!!
    }
}