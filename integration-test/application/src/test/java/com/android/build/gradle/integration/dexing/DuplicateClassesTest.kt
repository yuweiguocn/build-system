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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class DuplicateClassesTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    private val lineSeparator: String = System.lineSeparator()

    @Test
    fun testFailsWhenDuplicateClassesAndErrorMessageContainsKeyWords() {
        val jar1 = project.testDir.toPath().resolve("libs/jar1.jar")
        Files.createDirectories(jar1.parent)
        TestInputsGenerator.jarWithEmptyClasses(jar1, listOf("test/A"))

        val jar2 = project.testDir.toPath().resolve("libs/jar2.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar2, listOf("test/A"))

        TestFileUtils.appendToFile(
            project.buildFile,
            """dependencies {
                        |compile files('libs/jar1.jar', 'libs/jar2.jar')
                    |}""".trimMargin())

        val result = project.executor().expectFailure().run("checkDebugDuplicateClasses")

        assertThat(result.failureMessage).contains(
            "Duplicate class test.A found in modules jar1.jar (jar1.jar) and jar2.jar (jar2.jar)$lineSeparator${lineSeparator}Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>.")
    }
}