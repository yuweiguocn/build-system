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
import java.io.File
import java.io.StringReader

class CompilationDatabaseIndexingVisitorTest {

    @Test
    fun testExampleParses() {
        val example = "[\n" +
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

        val map = mutableMapOf<Int, String>()
        val strings = StringTable(map)
        val visitor = CompilationDatabaseIndexingVisitor(strings)
        CompilationDatabaseStreamingParser(
            JsonReader(StringReader(example)),
            visitor).parse()

        assertThat(visitor.mappings()["/usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp".replace('/', File.separatorChar)]).isEqualTo(0)
        assertThat(map).containsEntry(0, "--target=aarch64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot -Dnative_lib_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/aarch64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIC")
    }

    // This test verifies that when there is "../" in the file path, it gets canonicalized (b/123123307).
    @Test
    fun testCanonicalPath() {
        val example2 = "[\n" +
                "{\n" +
                "  \"directory\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/.externalNativeBuild/cmake/debug/arm64-v8a\",\n" +
                "  \"command\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=aarch64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot  -Dnative_lib_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward  -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/aarch64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security   -O0 -fno-limit-debug-info  -fPIC   -o CMakeFiles/native-lib.dir/src/main/cpp/native-lib.cpp.o -c /usr/local/google/home/jomof/projects/MyApplication22/app/../shared/native-lib.cpp\",\n" +
                "  \"file\": \"/usr/local/google/home/jomof/projects/MyApplication22/app/../shared/native-lib.cpp\"\n" +
                "}\n" +
                "]"

        val map = mutableMapOf<Int, String>()
        val strings = StringTable(map)
        val visitor = CompilationDatabaseIndexingVisitor(strings)
        CompilationDatabaseStreamingParser(
            JsonReader(StringReader(example2)),
            visitor
        ).parse()

        // Verify that the file path was canonicalized.
        assertThat(
            visitor.mappings()["/usr/local/google/home/jomof/projects/MyApplication22/shared/native-lib.cpp".replace(
                '/',
                File.separatorChar
            )]
        ).isEqualTo(0)
        assertThat(map).containsEntry(
            0,
            "--target=aarch64-none-linux-android --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64 --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot -Dnative_lib_EXPORTS -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a/include -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/aarch64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIC"
        )
    }
}
