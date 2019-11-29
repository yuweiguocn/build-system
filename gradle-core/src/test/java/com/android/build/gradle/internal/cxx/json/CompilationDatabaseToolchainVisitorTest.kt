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
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.JsonReader
import com.google.gson.stream.MalformedJsonException
import org.junit.Test
import java.io.File
import java.io.StringReader

class CompilationDatabaseToolchainVisitorTest {

    @Test
    fun testExampleParses() {
        val visitor = CompilationDatabaseToolchainVisitor(setOf("cpp"), setOf("c"))
        CompilationDatabaseStreamingParser(
            JsonReader(StringReader(
                "[\n" +
                        "{\n" +
                        "  \"directory\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/.externalNativeBuild/cmake/debug/arm64-v8a\",\n" +
                        "  \"command\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=aarch64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot  -Dnative_lib_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward  -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/aarch64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security   -O0 -fno-limit-debug-info  -fPIC   -o CMakeFiles/native-lib.dir/src/main/cpp/native-lib.cpp.o -c /usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp\",\n" +
                        "  \"file\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp\"\n" +
                        "},\n" +
                        "{\n" +
                        "  \"directory\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/.externalNativeBuild/cmake/debug/arm64-v8a\",\n" +
                        "  \"command\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=aarch64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot  -Dnative_lib_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward  -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/aarch64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security   -O0 -fno-limit-debug-info  -fPIC   -o CMakeFiles/native-lib.dir/src/main/cpp/native-lib.cpp.o -c /usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp\",\n" +
                        "  \"file\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp\"\n" +
                        "}\n" +
                        "]"
            )),
            visitor).parse()

        assertThat(visitor.result().cCompilerExecutable).isNull()
        assertThat(visitor.result().cppCompilerExecutable).isEqualTo(
            File("/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++")
        )
    }

    @Test
    fun testValidOneCompilationDatabase() {
        val visitor = CompilationDatabaseToolchainVisitor(setOf("cc"), setOf("c"))
        CompilationDatabaseStreamingParser(
            JsonReader(StringReader(
                "[\n" +
                        "    {\n" +
                        "        \"directory\": \"/home/user/test/build\",\n" +
                        "        \"command\": \"/usr/bin/clang++ -Irelative -DSOMEDEF=\\\"With spaces, quotes and \\\\-es.\\\" -c -o file.o file.cc\",\n" +
                        "        \"file\": \"file.cc\"\n" +
                        "    }\n" +
                        "    ]"
            )),
            visitor).parse()

        assertThat(visitor.result().cCompilerExecutable).isNull()
        assertThat(visitor.result().cppCompilerExecutable).isEqualTo(
            File("/usr/bin/clang++")
        )
    }

    @Test
    fun testValidMultiCompilationDatabase() {
        val visitor = CompilationDatabaseToolchainVisitor(setOf("cc"), setOf("c"))
        CompilationDatabaseStreamingParser(
            JsonReader(StringReader(
                "[\n" +
                        "  {\n" +
                        "    \"directory\": \"/home/user/test/build\",\n" +
                        "    \"command\": \"/usr/bin/clang++ -Irelative -DSOMEDEF=\\\"With spaces, quotes and \\\\-es.\\\" -c -o file.o file.cc\",\n" +
                        "    \"file\": \"file.cc\"\n" +
                        "  },\n" +
                        "\n" +
                        "  {\n" +
                        "    \"directory\": \"/home/user/test/build\",\n" +
                        "    \"command\": \"/usr/bin/clang++ -Irelative -DSOMEDEF=\\\"With spaces, quotes and \\\\-es.\\\" -c -o foo.o foo.cc\",\n" +
                        "    \"file\": \"foo.cc\"\n" +
                        "  },\n" +
                        "\n" +
                        "  {\n" +
                        "    \"directory\": \"/home/user/test/build\",\n" +
                        "    \"command\": \"/usr/bin/clang++ -Irelative -DSOMEDEF=\\\"With spaces, quotes and \\\\-es.\\\" -c -o baz.o baz.cc\",\n" +
                        "    \"file\": \"baz.cc\"\n" +
                        "  }\n" +
                        "]"
            )),
            visitor).parse()

        assertThat(visitor.result().cCompilerExecutable).isNull()
        assertThat(visitor.result().cppCompilerExecutable).isEqualTo(
            File("/usr/bin/clang++")
        )
    }

    @Test(expected = MalformedJsonException::class)
    fun testInvalidBadJson() {
        val visitor = CompilationDatabaseToolchainVisitor(setOf("cc"), setOf("c"))
        CompilationDatabaseStreamingParser(
            JsonReader(StringReader(
                "invalid json file"
            )),
            visitor).parse()

        assertThat(visitor.result().cCompilerExecutable).isNull()
        assertThat(visitor.result().cppCompilerExecutable).isEqualTo(
            File("/usr/bin/clang++")
        )
    }

    @Test(expected = MalformedJsonException::class)
    fun testInvalidCompilation() {
        val visitor = CompilationDatabaseToolchainVisitor(setOf("cc"), setOf("c"))
        CompilationDatabaseStreamingParser(
            JsonReader(StringReader(
                "[\n" +
                        "  {\n" +
                        "    \"directory\": \"missing-comma\"\n" +
                        "    \"command\": \"/usr/bin/clang++ -Irelative -DSOMEDEF=\\\"With spaces, quotes and \\\\-es.\\\" -c -o file.o file.cc\",\n" +
                        "    \"file\": \"file.cc\"\n" +
                        "  }\n" +
                        "]"
            )),
            visitor).parse()

        assertThat(visitor.result().cCompilerExecutable).isNull()
        assertThat(visitor.result().cppCompilerExecutable).isEqualTo(
            File("/usr/bin/clang++")
        )
    }
}