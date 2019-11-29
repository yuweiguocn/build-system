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

package com.android.build.gradle.integration.gradleapi

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(FilterableParameterized::class)
class ObsoleteApiTest(private val provider: TestProjectProvider) {

    companion object {
        @JvmStatic @Parameterized.Parameters()
        fun setUps() = listOf(
            TestProjectProvider("Kotlin") {
                KotlinHelloWorldApp.forPlugin("com.android.application")
            }, TestProjectProvider("Java") {
                HelloWorldApp.forPlugin("com.android.application")
            }
        )
    }

    @JvmField @Rule
    var project : GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(provider.provider.invoke())
            .create()

    @Before
    fun setup() {
        if (provider.name == "Java") {
            project.buildFile.appendText(
                "android.applicationVariants.all { variant ->\n" +
                        "  println variant.getJavaCompile().getName()\n" +
                        "}\n"
            )
        }
    }

    @Test
    fun `test via model`() {
        val model = project
            .model()
            .with(BooleanOption.DEBUG_OBSOLETE_API, true)
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()
            .onlyModel

        when(provider.name) {
            "Kotlin" -> {
                Truth.assertThat(model.syncIssues).hasSize(0)
            }
            "Java" -> {
                Truth.assertThat(model.syncIssues).hasSize(1)
                val warningMsg = model.syncIssues.first().message
                Truth.assertThat(warningMsg).isEqualTo(
                    "API 'variant.getJavaCompile()' is obsolete and has been replaced with 'variant.getJavaCompileProvider()'.\n" +
                        "It will be removed at the end of 2019.\n" +
                        "For more information, see https://d.android.com/r/tools/task-configuration-avoidance.\n" +
                        "REASON: Called from: ${project.testDir}${File.separatorChar}build.gradle:23\n" +
                        "WARNING: Debugging obsolete API calls can take time during configuration. It's recommended to not keep it on at all times.")
            }
            else -> throw RuntimeException("Unsupported type")
        }
    }

    @Test
    fun `Test from command line`() {
        val result = project.executor().with(BooleanOption.DEBUG_OBSOLETE_API, true).run("help")

        when(provider.name) {
            "Kotlin" -> {
                Truth.assertThat(result.stdout).doesNotContain("API 'variant.getJavaCompile()' is obsolete")
            }
            "Java" -> {
                Truth.assertThat(result.stdout).contains(
                    "API 'variant.getJavaCompile()' is obsolete and has been replaced with 'variant.getJavaCompileProvider()'.\n" +
                            "It will be removed at the end of 2019.\n" +
                            "For more information, see https://d.android.com/r/tools/task-configuration-avoidance.\n" +
                            "REASON: Called from: ${project.testDir}${File.separatorChar}build.gradle:23\n" +
                            "WARNING: Debugging obsolete API calls can take time during configuration. It's recommended to not keep it on at all times.")
            }
            else -> throw RuntimeException("Unsupported type")
        }
    }

}

class TestProjectProvider(
    val name: String,
    val provider: () -> TestProject
) {
    override fun toString(): String {
        return name
    }
}