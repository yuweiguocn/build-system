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

package com.android.build.gradle.internal.cxx.json;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;

public class AndroidBuildGradleJsonStreamingParserTest {

    @Test
    public void testStringTable() throws IOException {
        String text =
                "{\n"
                        + "  \"stringTable\": {\"0\" : \"My String\"},\n"
                        + "  \"buildFiles\": [\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk\",\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk\",\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/cdep.yml\"\n"
                        + "  ],\n"
                        + "  \"cleanCommands\": [\n"
                        + "    \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/ndk-build NDK_PROJECT_PATH=null APP_BUILD_SCRIPT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk NDK_APPLICATION_MK=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk APP_ABI=armeabi-v7a NDK_ALL_ABIS=armeabi-v7a NDK_DEBUG=1 APP_PLATFORM=android-12 NDK_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj NDK_LIBS_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/lib NDK_MODULE_PATH+=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/cdep/modules/ndk-build clean\"\n"
                        + "  ],\n"
                        + "  \"libraries\": {\n"
                        + "    \"example-debug-armeabi-v7a\": {\n"
                        + "      \"buildCommand\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/ndk-build NDK_PROJECT_PATH=null APP_BUILD_SCRIPT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk NDK_APPLICATION_MK=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk APP_ABI=armeabi-v7a NDK_ALL_ABIS=armeabi-v7a NDK_DEBUG=1 APP_PLATFORM=android-12 NDK_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj NDK_LIBS_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/lib NDK_MODULE_PATH+=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/cdep/modules/ndk-build /usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/libexample.so\",\n"
                        + "      \"toolchain\": \"toolchain-armeabi-v7a\",\n"
                        + "      \"groupName\": \"debug\",\n"
                        + "      \"abi\": \"armeabi-v7a\",\n"
                        + "      \"artifactName\": \"example\",\n"
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/../../../../../other_sources/file1.cpp\",\n"
                        + "          \"flags\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-fpic\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                        + "        },\n"
                        + "        {\n"
                        + "          \"src\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/example.cpp\",\n"
                        + "          \"flags\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-fpic\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"output\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/libexample.so\"\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"toolchains\": {\n"
                        + "    \"toolchain-armeabi-v7a\": {\n"
                        + "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"cFileExtensions\": [],\n"
                        + "  \"cppFileExtensions\": [\n"
                        + "    \"cpp\"\n"
                        + "  ]\n"
                        + "}";

        StringBuilder sb = new StringBuilder();
        AndroidBuildGradleJsonStreamingParser parser =
                new AndroidBuildGradleJsonStreamingParser(
                        new JsonReader(new StringReader(text)),
                        new AndroidBuildGradleJsonStreamingVisitor() {
                            @Override
                            protected void visitStringTableEntry(int index, @NonNull String value) {
                                sb.append(String.format("%s=%s", index, value));
                                assertThat(index).isEqualTo(0);
                                assertThat(value).isEqualTo("My String");
                            }
                        });
        parser.parse();
        assertThat(sb.toString()).isEqualTo("0=My String");
    }

    @Test
    public void testAnExample() {
        checkTextParsesWithStreamer(
                "{\n"
                        + "  \"buildFiles\": [\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk\",\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk\",\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/cdep.yml\"\n"
                        + "  ],\n"
                        + "  \"cleanCommands\": [\n"
                        + "    \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/ndk-build NDK_PROJECT_PATH=null APP_BUILD_SCRIPT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk NDK_APPLICATION_MK=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk APP_ABI=armeabi-v7a NDK_ALL_ABIS=armeabi-v7a NDK_DEBUG=1 APP_PLATFORM=android-12 NDK_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj NDK_LIBS_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/lib NDK_MODULE_PATH+=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/cdep/modules/ndk-build clean\"\n"
                        + "  ],\n"
                        + "  \"libraries\": {\n"
                        + "    \"example-debug-armeabi-v7a\": {\n"
                        + "      \"buildCommand\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/ndk-build NDK_PROJECT_PATH=null APP_BUILD_SCRIPT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk NDK_APPLICATION_MK=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk APP_ABI=armeabi-v7a NDK_ALL_ABIS=armeabi-v7a NDK_DEBUG=1 APP_PLATFORM=android-12 NDK_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj NDK_LIBS_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/lib NDK_MODULE_PATH+=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/cdep/modules/ndk-build /usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/libexample.so\",\n"
                        + "      \"toolchain\": \"toolchain-armeabi-v7a\",\n"
                        + "      \"groupName\": \"debug\",\n"
                        + "      \"abi\": \"armeabi-v7a\",\n"
                        + "      \"artifactName\": \"example\",\n"
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/../../../../../other_sources/file1.cpp\",\n"
                        + "          \"flags\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-fpic\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                        + "        },\n"
                        + "        {\n"
                        + "          \"src\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/example.cpp\",\n"
                        + "          \"flags\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-fpic\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"output\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/libexample.so\"\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"toolchains\": {\n"
                        + "    \"toolchain-armeabi-v7a\": {\n"
                        + "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"cFileExtensions\": [],\n"
                        + "  \"cppFileExtensions\": [\n"
                        + "    \"cpp\"\n"
                        + "  ]\n"
                        + "}");
    }

    @Test
    public void testUnknownTopLevelNames() {
        checkTextParsesWithStreamer(
                "{\n"
                        + "  \"buildFiles1\": [\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk\",\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk\",\n"
                        + "    \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/cdep.yml\"\n"
                        + "  ],\n"
                        + "  \"cleanCommands1\": [\n"
                        + "    \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/ndk-build NDK_PROJECT_PATH=null APP_BUILD_SCRIPT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk NDK_APPLICATION_MK=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk APP_ABI=armeabi-v7a NDK_ALL_ABIS=armeabi-v7a NDK_DEBUG=1 APP_PLATFORM=android-12 NDK_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj NDK_LIBS_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/lib NDK_MODULE_PATH+=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/cdep/modules/ndk-build clean\"\n"
                        + "  ],\n"
                        + "  \"toolchains1\": {\n"
                        + "    \"toolchain-armeabi-v7a\": {\n"
                        + "      \"cppCompilerExecutable1\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"cFileExtensions1\": [],\n"
                        + "  \"cppFileExtensions1\": [\n"
                        + "    \"cpp\"\n"
                        + "  ]\n"
                        + "}");
    }

    @Test
    public void testUnknownLibraryFields() {
        checkTextParsesWithStreamer(
                "{\n"
                        + "  \"libraries\": {\n"
                        + "    \"example-debug-armeabi-v7a\": {\n"
                        + "      \"buildCommand1\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/ndk-build NDK_PROJECT_PATH=null APP_BUILD_SCRIPT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Android.mk NDK_APPLICATION_MK=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/Application.mk APP_ABI=armeabi-v7a NDK_ALL_ABIS=armeabi-v7a NDK_DEBUG=1 APP_PLATFORM=android-12 NDK_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj NDK_LIBS_OUT=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/lib NDK_MODULE_PATH+=/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/cdep/modules/ndk-build /usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/libexample.so\",\n"
                        + "      \"toolchain1\": \"toolchain-armeabi-v7a\",\n"
                        + "      \"groupName1\": \"debug\",\n"
                        + "      \"ab1i\": \"armeabi-v7a\",\n"
                        + "      \"artifactName1\": \"example\",\n"
                        + "      \"files1\": [\n"
                        + "        {\n"
                        + "          \"src\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/../../../../../other_sources/file1.cpp\",\n"
                        + "          \"flags\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-fpic\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                        + "        },\n"
                        + "        {\n"
                        + "          \"src\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/example.cpp\",\n"
                        + "          \"flags\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-fpic\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
    }

    @Test
    public void testUnknownFileField() {
        checkTextParsesWithStreamer(
                "{\n"
                        + "  \"libraries\": {\n"
                        + "    \"example-debug-armeabi-v7a\": {\n"
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src1\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/example.cpp\",\n"
                        + "          \"flags1\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-fpic\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
    }

    @Test
    public void testUnknownToolchainCategory() {
        checkTextParsesWithStreamer(
                "{\n"
                        + "  \"toolchains\": {\n"
                        + "    \"toolchain-armeabi-v7a\": {\n"
                        + "      \"cppCompilerExecutable1\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
    }

    @Test
    public void testUnknownNull() {
        checkTextParsesWithStreamer("{\n" + "  \"buildFiles\": [\n" + "    null\n" + "  ]\n" + "}");
    }

    @Test
    public void testUnknownNumber() {
        checkTextParsesWithStreamer("{\n" + "  \"buildFiles\": [\n" + "    0\n" + "  ]\n" + "}");
    }

    @Test
    public void testUnknownBoolean() {
        checkTextParsesWithStreamer("{\n" + "  \"buildFiles\": [\n" + "    true\n" + "  ]\n" + "}");
    }

    private void checkTextParsesWithStreamer(String text) {
        try (AndroidBuildGradleJsonStreamingParser parser =
                new AndroidBuildGradleJsonStreamingParser(
                        new JsonReader(new StringReader(text)),
                        new AndroidBuildGradleJsonStreamingVisitor() {}) {}) {
            parser.parse();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
