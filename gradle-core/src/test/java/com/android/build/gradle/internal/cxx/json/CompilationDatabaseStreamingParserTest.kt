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

package com.android.build.gradle.internal.cxx.json

import com.google.common.truth.Truth.assertThat
import com.google.gson.stream.JsonReader
import org.junit.Test
import java.io.StringReader

class CompilationDatabaseStreamingParserTest {

    val example = "[\n" +
            "{\n" +
            "  \"directory\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/.externalNativeBuild/cmake/debug/arm64-v8a\",\n" +
            "  \"command\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=aarch64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot  -Dnative_lib_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward  -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/aarch64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security   -O0 -fno-limit-debug-info  -fPIC   -o CMakeFiles/native-lib.dir/src/main/cpp/native-lib.cpp.o -c /usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp\",\n" +
            "  \"file\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp\"\n" +
            "}\n" +
            "]"

    @Test
    fun testExampleParses() {
        CompilationDatabaseStreamingParser(JsonReader(StringReader(example)),
            CompilationDatabaseStreamingVisitor()).parse()
    }

    @Test
    fun testCommand() {
        CompilationDatabaseStreamingParser(JsonReader(StringReader(example)),
            object : CompilationDatabaseStreamingVisitor() {
                override fun visitCommand(command: String) {
                    assertThat(command).contains("clang++")
                }
            }).parse()
    }

    @Test
    fun testDirectory() {
        CompilationDatabaseStreamingParser(JsonReader(StringReader(example)),
            object : CompilationDatabaseStreamingVisitor() {
                override fun visitDirectory(directory: String) {
                    assertThat(directory).contains(".externalNativeBuild")
                }
            }).parse()
    }

    @Test
    fun testFile() {
        CompilationDatabaseStreamingParser(JsonReader(StringReader(example)),
            object : CompilationDatabaseStreamingVisitor() {
                override fun visitFile(file: String) {
                    assertThat(file).contains("native-lib.cpp")
                }
            }).parse()
    }
}