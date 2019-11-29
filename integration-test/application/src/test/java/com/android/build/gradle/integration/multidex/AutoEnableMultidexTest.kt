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

package com.android.build.gradle.integration.multidex

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.google.common.base.Throwables
import com.google.common.collect.Sets
import org.junit.Rule
import org.junit.Test

/** Check multidex is automatically enabled for min sdk > 21.  */
class AutoEnableMultidexTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(MinimalSubProject.app("com.example.helloworld").apply {
            appendToBuild(
                """
                    android {
                        flavorDimensions "generation"
                        productFlavors {
                            legacy { minSdkVersion 19 }
                            current { minSdkVersion 21 }
                        }
                    }
                    """.trimIndent()
            )
            val dexMethodLimit = 1L.shl(16)
            val methods = (0..(dexMethodLimit / 3 + 1))
                .joinToString("\n    ") { "public void m$it() {}" }
            for (i in 0..2) {
                addFile(
                    TestSourceFile(
                        "src/main/java/com/example/helloworld", "A$i.java",
                        """
                            package com.example.helloworld;
                            public class A$i {
                                $methods
                            }
                        """.trimIndent()
                    )
                )
            }
        })
        .create()

    @Test
    fun testAutoEnableMultidex() {
        val result = project.executor().expectFailure().run("assembleLegacyDebug")
        assertThat(Throwables.getStackTraceAsString(result.exception!!))
            .contains("https://developer.android.com/tools/building/multidex.html")

        project.executor().run("assembleCurrentDebug")
        val debug = project.getApk(GradleTestProject.ApkType.DEBUG, "current")
        assertThat(debug.allDexes).hasSize(2)
        val classes = debug.allDexes[0].classes.keys
        val classes2 = debug.allDexes[1].classes.keys
        assertThat(classes2).named("No duplicate class definitions").containsNoneIn(classes)
        assertThat(Sets.union(classes, classes2))
            .containsExactly(
                "Lcom/example/helloworld/A0;",
                "Lcom/example/helloworld/A1;",
                "Lcom/example/helloworld/A2;",
                "Lcom/example/helloworld/BuildConfig;",
                "Lcom/example/helloworld/R;"
            )
    }
}
