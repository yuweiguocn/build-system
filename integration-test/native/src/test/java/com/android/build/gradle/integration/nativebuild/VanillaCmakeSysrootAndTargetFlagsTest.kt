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
import com.android.build.gradle.integration.common.truth.NativeAndroidProjectSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.NativeAndroidProject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * This test ensures that CMake server codepath returns sufficient information for Android Studio
 * to accept source files as targeting Android.
 */
class VanillaCmakeSysrootAndTargetFlagsTest {

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
add_library(native-lib SHARED hello-jni.cpp)
find_library(log-lib log)
target_link_libraries(native-lib ${'$'}{log-lib})
                """
        )
    }

    @Test
    fun testThatFlagsLooksLikeAndroidProject() {
        val nativeProject = project.model().fetch(NativeAndroidProject::class.java)
        NativeAndroidProjectSubject.assertThat(nativeProject)
            .hasArtifactGroupsNamed("debug", "release")
        nativeProject.settings.onEach { settings ->
            assert(settings != null)
            val hasTarget = settings.compilerFlags.any { flag -> flag.startsWith("--target=") }
            val hasSysroot = settings.compilerFlags.any { flag -> flag.startsWith("--sysroot=") }
            // --sysroot can be removed from this check once Android Studio only requires --target
            assertThat(hasTarget).named("--target in flags: " +
                    "${settings.compilerFlags}").isTrue()
            assertThat(hasSysroot).named("--sysroot in flags: " +
                    "${settings.compilerFlags}").isTrue()
        }
        Truth.assertThat(nativeProject.artifacts.first()!!.sourceFiles.size).isEqualTo(1)
    }
}
