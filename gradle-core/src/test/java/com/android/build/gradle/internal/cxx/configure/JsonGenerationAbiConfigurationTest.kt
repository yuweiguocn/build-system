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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import com.google.common.truth.Truth.assertThat

import org.junit.Test
import java.io.File

class JsonGenerationAbiConfigurationTest {

    @Test
    fun baseCmake() {

        val config = createJsonGenerationAbiConfiguration(
            Abi.X86,
            "debug",
            File("./.externalNativeBuild"),
            File("./obj-base-folder"),
            NativeBuildSystem.CMAKE,
            13  )
        assertThat(config.externalNativeBuildFolder)
            .isEqualTo(File("./.externalNativeBuild/cmake/debug/x86"))
        assertThat(config.jsonFile)
            .isEqualTo(File(
                "./.externalNativeBuild/cmake/debug/x86/android_gradle_build.json"))

        assertThat(config.gradleBuildOutputFolder)
            .isEqualTo(File("./.externalNativeBuild/cxx/debug/x86"))
        assertThat(config.buildCommandFile)
            .isEqualTo(File(
                "./.externalNativeBuild/cmake/debug/x86/cmake_build_command.txt"))
        assertThat(config.buildOutputFile)
            .isEqualTo(File(
                "./.externalNativeBuild/cmake/debug/x86/cmake_build_output.txt"))
        assertThat(config.cmake?.buildGenerationStateFile)
            .isEqualTo(File(
                "./.externalNativeBuild/cxx/debug/x86/build_generation_state.json"))
        assertThat(config.cmake?.cmakeListsWrapperFile)
            .isEqualTo(File("./.externalNativeBuild/cxx/debug/x86/CMakeLists.txt"))
        assertThat(config.cmake?.toolchainWrapperFile)
            .isEqualTo(File(
                "./.externalNativeBuild/cxx/debug/x86/android_gradle_build.toolchain.cmake"))
        assertThat(config.cmake?.cacheKeyFile)
            .isEqualTo(File("./.externalNativeBuild/cxx/debug/x86/compiler_cache_key.json"))
    }
}