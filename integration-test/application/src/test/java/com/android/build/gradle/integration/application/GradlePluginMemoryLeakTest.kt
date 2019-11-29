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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

/**
 * Integration stress test to verify that the plugin does not leak memory when the build script classpath
 * changes.
 */
class GradlePluginMemoryLeakTest {

    private val app = HelloWorldApp.forPlugin("com.android.application").apply {
        replaceFile(
            getFile("build.gradle").appendContent(
                """
                android {
                    buildTypes {
                        release {
                            minifyEnabled = true
                            shrinkResources = true
                        }
                    }
                }""".trimIndent()
            )
        )


        addFile(TestSourceFile("buildSrc/build.gradle", "plugins { id 'java' }"))
        addFile(
            TestSourceFile(
                BUILDSRC_JAVA_SOURCE_FILE, """
                package com.example;

                public class MyClass {
                    $EDIT_MARKER
                }
                """.trimIndent()
            )
        )
    }
    @get:Rule
    val project =
        GradleTestProject
            .builder()
            .fromTestApp(app)
            .withMetaspace(METASPACE)
            .create()

    @Test
    fun changeBuildSrcTest() {
        for (i in 1..RUNS) {
            System.out.println("---- RUN $i ----")
            project.executor()
                .with(BooleanOption.ENABLE_R8, false)
                .run("assembleRelease")
            TestFileUtils.searchAndReplace(
                project.file(BUILDSRC_JAVA_SOURCE_FILE),
                EDIT_MARKER,
                """public void newMethod$i() { }
                    $EDIT_MARKER"""
            )
        }
    }

}

/**
 * [METASPACE] is chosen so that the build fails after about 7 runs without the memory leak fix.
 * This gives some headroom in an attempt to avoid the test being flaky.
 */
private const val METASPACE = "256M"

/**
 * Given the [METASPACE] choice, run 8 builds to catch a leak even if the metaspace use goes down.
 */
private const val RUNS = 14

private const val BUILDSRC_JAVA_SOURCE_FILE = "buildSrc/src/main/java/com/example/MyClass.java"
private const val EDIT_MARKER = "// Edit here"