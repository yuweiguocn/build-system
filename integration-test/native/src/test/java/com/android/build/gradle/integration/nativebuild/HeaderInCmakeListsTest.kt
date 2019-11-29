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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.NativeAndroidProject
import org.junit.Rule
import org.junit.Test
import com.android.build.gradle.integration.common.truth.NativeAndroidProjectSubject.assertThat
import org.junit.Before
import java.io.File
import com.google.common.truth.Truth.assertThat

/**
 * This test is related to b/112611156
 *
 * The project set up is equivalent to File/Create New Project except that there is a new file
 * called extra-header.hpp and this file is referenced by CMakeLists.txt.
 *
 * Even though this is valid CMake syntax we shouldn't send the header-file to Android Studio
 * because it won't have flags and Android Studio will reject it. The header file is recorded
 * in android_gradle_build.json but is not in the model sent to Android Studio.
 */
class HeaderInCmakeListsTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(
            HelloWorldJniApp.builder()
                .withNativeDir("cpp")
                .useCppSource(true)
                .build()
        )
        .setCmakeVersion("3.10.4819442")
        .setWithCmakeDirInLocalProp(true)
        .create()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """apply plugin: 'com.android.application'
                        android.compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                        android.externalNativeBuild.cmake.path "src/main/cpp/CMakeLists.txt"
                """

        )

        val cmakeLists = File(project.buildFile.parent, "src/main/cpp/CMakeLists.txt")
        TestFileUtils.appendToFile(
            cmakeLists,
            """
cmake_minimum_required(VERSION 3.4.1)
add_library(native-lib SHARED hello-jni.cpp extra-header.hpp)
find_library(log-lib log)
target_link_libraries(native-lib ${'$'}{log-lib})
                """
        )

        val extraHeader = File(project.buildFile.parent, "src/main/cpp/extra-header.hpp")
        TestFileUtils.appendToFile(
            extraHeader,
            """
// Extra header file that is referenced in CMakeLists.txt
                """
        )
    }

    @Test
    fun testThatHeaderFileIsExcluded() {
        val nativeProject = project.model().fetch(NativeAndroidProject::class.java)
        assertThat(nativeProject).hasArtifactGroupsNamed("debug", "release")
        assertThat(nativeProject.artifacts.first()!!.sourceFiles.size).isEqualTo(1)
    }
}