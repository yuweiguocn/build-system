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

package com.android.build.gradle.tasks

import com.android.build.gradle.external.cmake.server.Target
import com.android.build.gradle.internal.cxx.json.StringTable
import com.google.common.collect.Maps
import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import org.junit.Test
import java.io.File
import java.io.StringReader

class CmakeServerExternalNativeJsonGeneratorTranslationTest {

    // Reference b/112611156
    @Test
    fun testWindowsPathConcatenation() {
        val compilationDatabase = """
            [{"directory": "C:/Users/jomof/AndroidStudioProjects/MyApplication10/app/.externalNativeBuild/cmake/debug/x86",
              "command": "C:\\Users\\jomof\\AppData\\Local\\Android\\Sdk\\ndk-bundle\\toolchains\\llvm\\prebuilt\\windows-x86_64\\bin\\clang++.exe --target=i686-none-linux-android16 --gcc-toolchain=C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/toolchains/x86-4.9/prebuilt/windows-x86_64 --sysroot=C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sysroot  -Dnative_lib_EXPORTS -isystem C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include -isystem C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/android/support/include -isystem C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include  -isystem C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sysroot/usr/include/i686-linux-android -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -Wa,--noexecstack -Wformat -Werror=format-security -std=c++11  -O0 -fno-limit-debug-info  -fPIC   -o CMakeFiles\\native-lib.dir\\src\\main\\cpp\\native-lib.cpp.o -c C:\\Users\\jomof\\AndroidStudioProjects\\MyApplication10\\app\\src\\main\\cpp\\native-lib.cpp",
              "file": "C:/Users/jomof/AndroidStudioProjects/MyApplication10/app/src/main/cpp/native-lib.cpp"}]"""
        val target = """{
             "artifacts":[
                "C:/Users/jomof/AndroidStudioProjects/MyApplication10/app/build/intermediates/cmake/debug/obj/x86/libnative-lib.so"
             ],
             "buildDirectory":"C:/Users/jomof/AndroidStudioProjects/MyApplication10/app/.externalNativeBuild/cmake/debug/x86",
             "fileGroups":[
                {
                   "compileFlags":"-isystem C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sysroot/usr/include/i686-linux-android -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -Wa,--noexecstack -Wformat -Werror=format-security -std=c++11  -O0 -fno-limit-debug-info  -fPIC  ",
                   "defines":[
                      "native_lib_EXPORTS"
                   ],
                   "includePath":[
                      {
                         "isSystem":true,
                         "path":"C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include"
                      },
                      {
                         "isSystem":true,
                         "path":"C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/android/support/include"
                      },
                      {
                         "isSystem":true,
                         "path":"C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include"
                      }
                   ],
                   "isGenerated":false,
                   "language":"CXX",
                   "sources":[
                      "src/main/cpp/native-lib.cpp"
                   ]
                }
             ],
             "fullName":"libnative-lib.so",
             "linkFlags":"-Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libatomic.a -nostdlib++ --sysroot C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/platforms/android-16/arch-x86 -Wl,--build-id -Wl,--warn-shared-textrel -Wl,--fatal-warnings -LC:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/libs/x86 -Wl,--no-undefined -Wl,-z,noexecstack -Qunused-arguments -Wl,-z,relro -Wl,-z,now",
             "linkLibraries":"-llog -latomic -lm \"C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/libs/x86/libc++_static.a\" \"C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/libs/x86/libc++abi.a\" \"C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/libs/x86/libandroid_support.a\"",
             "linkerLanguage":"CXX",
             "name":"native-lib",
             "sourceDirectory":"C:/Users/jomof/AndroidStudioProjects/MyApplication10/app",
             "sysroot":"C:/Users/jomof/AppData/Local/Android/Sdk/ndk-bundle/sysroot",
             "type":"SHARED_LIBRARY"
            }
            """
        val table = Maps.newHashMap<Int, String>()
        val library = CmakeServerExternalNativeJsonGenerator.getNativeLibraryValue(
            File("cmake.exe"),
            File("./output-folder/x86"),
            true,
            JsonReader(StringReader(compilationDatabase)),
            "x86",
            File("C:/Users/jomof/AndroidStudioProjects/MyApplication10/app/.externalNativeBuild/cmake/debug/x86"),
            getTestTarget(target),
            StringTable(table)
        )
        val flagsOrdinal = library.files!!.first().flagsOrdinal!!
        assertThat(table[flagsOrdinal]).contains("--target")
    }

    private fun getTestTarget(targetStr: String): Target {
        val gson = GsonBuilder().create()
        return gson.fromJson(targetStr, Target::class.java)
    }
}
