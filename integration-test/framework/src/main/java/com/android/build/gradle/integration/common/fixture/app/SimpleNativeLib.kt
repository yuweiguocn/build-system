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

package com.android.build.gradle.integration.common.fixture.app

import com.android.build.gradle.integration.common.fixture.GradleTestProject

/** A simple android library with only native code. */
class SimpleNativeLib : AbstractAndroidTestModule() {
    private val buildGradle =
            TestSourceFile(
                    "build.gradle",
                    """
apply plugin: 'com.android.library'
android {
    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
    externalNativeBuild {
        cmake {
            path "CMakeLists.txt"
        }
    }
}""")

    private val source =
            TestSourceFile(
                    "src/main/jni",
                    "foo.c",
"""
void foo() {
}
""")

    private val cmakeLists =
            TestSourceFile(
                    ".",
                    "CMakeLists.txt",
                    """
cmake_minimum_required(VERSION 3.4.1)

# Compile all source files under this tree into a single shared library
file(GLOB_RECURSE SRC src/*.c src/*.cpp src/*.cc src/*.cxx src/*.c++ src/*.C)
message(\"${"$"}{SRC}\")
set(CMAKE_VERBOSE_MAKEFILE ON)
add_library(foo SHARED ${"$"}{SRC})
""")

    private val androidManifestXml =
            TestSourceFile(
                    "src/main",
                    "AndroidManifest.xml",
                    """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.mylibrary" />
""")

    init {
        addFile(buildGradle)
        addFile(source)
        addFile(cmakeLists)
        addFile(androidManifestXml)
    }
}