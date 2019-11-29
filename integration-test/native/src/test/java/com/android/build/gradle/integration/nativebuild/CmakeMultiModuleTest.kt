/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.SimpleNativeLib
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Cmake test with multiple modules.
 */
class CmakeMultiModuleTest {

    @get:Rule
    val project =
            GradleTestProject.builder()
                    .fromTestApp(
                            MultiModuleTestProject(
                                    mapOf(
                                            "app" to HelloWorldJniApp.builder().withCmake().build(),
                                            "lib" to SimpleNativeLib())))
                .setCmakeVersion("3.10.4819442")
                .setWithCmakeDirInLocalProp(true)
                    .create()

    @Before
    fun setUp() {
        project.getSubproject(":app").buildFile.appendText(
"""
apply plugin: 'com.android.application'

android {
    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}

    defaultConfig {
        ndk {
            abiFilters "x86"
        }
    }

    externalNativeBuild {
        cmake {
            path "CMakeLists.txt"
        }
    }
}


dependencies {
    implementation project(":lib")
}

// This make the build task in lib run after the task in app if the task dependency is not set up
// properly.
afterEvaluate {
    tasks.getByPath(":lib:externalNativeBuildDebug")
            .shouldRunAfter(tasks.getByPath(":app:externalNativeBuildDebug"))
}

""")

        // Limit ABI to improve running time.
        project.getSubproject(":lib").buildFile.appendText(
"""
android {
    defaultConfig {
        ndk {
            abiFilters "x86"
        }
    }
}
""")
    }

    @Test
    fun checkTaskExecutionOrder() {
        val result = project.executor().run("clean", ":app:assembleDebug")
        assertThat(result.getTask(":lib:externalNativeBuildDebug")).didWork()
        assertThat(result.getTask(":app:externalNativeBuildDebug"))
                .ranAfter(":lib:externalNativeBuildDebug")
    }
}