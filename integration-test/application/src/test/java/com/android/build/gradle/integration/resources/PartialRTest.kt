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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import kotlin.test.assertFailsWith

class PartialRTest {

    val app = MinimalSubProject.app("com.example.app")
        .appendToBuild("android.aaptOptions.namespaced = true")
        .withFile(
                "src/main/res/values/strings.xml",
                """<resources>
                       <string name="default_string">My String</string>
                       <string name="public_string">public</string>
                       <string name="private_string">private</string>
                   </resources>"""
        )
        .withFile(
            "src/main/res/values/strings2.xml",
            """<resources>
                       <string name="string2">delete_me</string>
                       <public type="string" name="string2"/>
                   </resources>"""
        )
        .withFile(
                "src/main/res/values/public.xml",
                """<resources>
                       <public type="string" name="public_string"/>
                   </resources>"""
        )
        .withFile(
                "src/main/res/values/symbols.xml",
                """<resources>
                       <java-symbol type="string" name="private_string"/>
                   </resources>"""
        )
        .withFile("src/main/res/raw/raw_resource.txt", "Raw resource content")

    val testApp =
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkBuilds() {
        project.executor().run(":app:assembleDebug")

        val stringsR = FileUtils.join(
                project.getSubproject("app").intermediatesDir,
            "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "out",
                "values_strings.arsc.flat-R.txt"
        )
        val publicR = File(stringsR.parentFile, "values_public.arsc.flat-R.txt")
        val symbolsR = File(stringsR.parentFile, "values_symbols.arsc.flat-R.txt")
        val strings2 = File(stringsR.parentFile, "values_strings2.arsc.flat-R.txt")

        assertThat(stringsR).exists()
        assertThat(publicR).exists()
        assertThat(symbolsR).exists()

        assertThat(stringsR).contains(
                "" +
                        "default int string default_string\n" +
                        "default int string private_string\n" +
                        "default int string public_string\n"
        )
        assertThat(publicR).contains("public int string public_string")
        assertThat(symbolsR).contains("private int string private_string")

        val rJar = FileUtils.join(
                project.getSubproject("app").intermediatesDir,
            "compile_only_namespaced_r_class_jar",
            "debug",
            "createDebugRFiles",
            "R.jar")
        assertThat(rJar).exists()

        URLClassLoader(arrayOf(rJar.toURI().toURL()), null).use { classLoader ->
            val testC = classLoader.loadClass("com.example.app.R\$string")
            checkResource(testC, "public_string")
            checkResource(testC, "private_string")
            checkResource(testC, "default_string")
            checkResource(testC, "string2")
            checkResourceNotPresent(testC, "invalid")
        }

        URLClassLoader(arrayOf(rJar.toURI().toURL()), null).use { classLoader ->
            val testC = classLoader.loadClass("com.example.app.R\$raw")
            checkResource(testC, "raw_resource")
        }

        // Check that deletes are handled properly too.
        val strings2SourceFile =
                FileUtils.join(
                        project.getSubproject(":app").mainSrcDir.parentFile,
                        "res", "values", "strings2.xml")
        // Make sure we've got the right file and then delete it.
        assertThat(strings2SourceFile).exists()
        FileUtils.delete(strings2SourceFile)
        assertThat(strings2SourceFile).doesNotExist()
        // Partial file from previous build.
        assertThat(strings2).exists()

        // Incremental build.
        project.executor().run(":app:assembleDebug")

        // Partial file for the removed file should have been removed too.
        assertThat(strings2).doesNotExist()

        URLClassLoader(arrayOf(rJar.toURI().toURL()), null).use { classLoader ->
            val testC = classLoader.loadClass("com.example.app.R\$string")
            checkResource(testC, "public_string")
            checkResource(testC, "private_string")
            checkResource(testC, "default_string")
            checkResourceNotPresent(testC, "string2")
            checkResourceNotPresent(testC, "invalid")
        }
    }

    private fun checkResource(testC: Class<*>, name: String) {
        val field = testC.getField(name)
        Truth.assertThat(field.getInt(testC)).isEqualTo(0)
    }

    private fun checkResourceNotPresent(testC: Class<*>, name: String) {
        assertFailsWith<NoSuchFieldException> {
            testC.getField(name)
        }
    }
}