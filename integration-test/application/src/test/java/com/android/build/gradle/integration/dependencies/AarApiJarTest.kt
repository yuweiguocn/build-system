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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Sanity tests for consuming an AAR with an API jar.
 */
class AarApiJarTest {

    private val apiJarLib = MinimalSubProject.javaLibrary()
        .withFile(
            "src/main/java/com/example/publishedlib/Example.java",
            """package com.example.publishedlib;
                    public class Example {
                        // This doesn't actually make sense to have this in the API only,
                        // but it means we can be sure we were compiling against the API only jar.
                        public static void apiOnly() {}
                    }"""
        )

    private val publishedLib = MinimalSubProject.lib("com.example.publishedlib")
        .appendToBuild(
            """
                configurations {
                    apiJar
                }

                dependencies {
                    apiJar project(":apiJarLib")
                }

                // TODO: A better APi for this?
                tasks.all { task ->
                    if (task.name == "bundleReleaseAar") {
                        task.from(configurations.apiJar) { copySpec ->
                            copySpec.eachFile { it.path = "api.jar" }
                        }
                    }
                }"""
        )
        .withFile(
            "src/main/java/com/example/publishedlib/Example.java",
            """package com.example.publishedlib;
                    public class Example {
                        public static void runtimeOnly() {}
                    }"""
        )

    private val consumingapp = MinimalSubProject.app("com.example.app")
        .appendToBuild(
            """
                    repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                    dependencies { implementation name: 'publishedLib-release', ext:'aar' }"""
        )
        .withFile(
            "src/main/java/com/example/lib2/Example.java",
            """package com.example.lib2;
                    public class Example {
                        public static void useApiMethod() {
                            com.example.publishedlib.Example.apiOnly();
                        }
                    }
                    """
        )

    val testApp =
        MultiModuleTestProject.builder()
            .subproject(":publishedLib", publishedLib)
            .subproject(":apiJarLib", apiJarLib)
            .subproject(":consumingApp", consumingapp)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkBuilds() {
        project.executor().run(":publishedLib:assembleRelease")
        project.executor().run(":consumingApp:assembleDebug")

        project.getSubproject("consumingApp")
            .getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
                assertThat(apk)
                    .hasClass("Lcom/example/publishedlib/Example;")
                    .that().hasMethod("runtimeOnly")
            }
    }

}
