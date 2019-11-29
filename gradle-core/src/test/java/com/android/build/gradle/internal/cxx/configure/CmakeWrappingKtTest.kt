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

import com.google.common.truth.Truth.assertThat

import org.junit.Test
import java.io.File

class CmakeWrappingKtTest {
    @Test
    fun toolchainWrapperWindows() {
        val result = wrapCmakeToolchain(
            originalToolchainFile = File("""C:\path\to\original-toolchain.cmake"""),
            cacheFile = File("""C:\path\to\cache-file.json"""),
            cacheUseSignalFile = File("""C:\path\to\cache-use-signal-file.json""")
        )

        assertThat(result).contains("Do not modify")
        assertThat(result).doesNotContain(" C:") // Check for unquoted path
        assertThat(result).contains(""""C:/path/to/original-toolchain.cmake"""")
        assertThat(result).contains(""""C:/path/to/cache-use-signal-file.json"""")
        assertThat(result).contains(""""C:/path/to/cache-file.json"""")
    }

    @Test
    fun toolchainWrapperNotWindows() {
        val result = wrapCmakeToolchain(
            originalToolchainFile = File("/path/to/original-toolchain.cmake"),
            cacheFile = File("/path/to/cache-file.json"),
            cacheUseSignalFile = File("/path/to/cache-use-signal-file.json")
        )

        assertThat(result).contains("Do not modify")
        assertThat(result).doesNotContain(" /") // Check for unquoted path
        assertThat(result).contains(""""/path/to/original-toolchain.cmake"""")
        assertThat(result).contains(""""/path/to/cache-use-signal-file.json"""")
        assertThat(result).contains(""""/path/to/cache-file.json"""")
    }

    @Test
    fun cmakeListsWrapperWindows() {
        val result = wrapCmakeLists(
            originalCmakeListsFolder = File("""C:\path\to\cmakelists"""),
            gradleBuildOutputFolder = File("""C:\path\to\gradle-build-output"""),
            buildGenerationStateFile = File("""C:\path\to\build-generation-state.json"""),
            isWindows = true
        )
        assertThat(result).contains("Do not modify")
        assertThat(result).doesNotContain(" C:") // Check for unquoted path
        assertThat(result).contains(""""C:/path/to/cmakelists"""")
        assertThat(result).contains(""""C:/path/to/gradle-build-output"""")
        assertThat(result).contains(""""C:/path/to/build-generation-state.json"""")
        assertThat(result).contains("""\r\n""")
    }

    @Test
    fun substituteCmakePaths() {
        val androidNdk = File("""C:\path\to\ndk""")
        val replaced = substituteCmakePaths(
            "set(CMAKE_ANDROID_NDK C:/path/to/ndk)", androidNdk)
        assertThat(replaced).isEqualTo("set(CMAKE_ANDROID_NDK ${'$'}{ANDROID_NDK})")
    }
}