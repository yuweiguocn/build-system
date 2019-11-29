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

import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;

public class AndroidBuildGradleJsonsTest {
    @Test
    public void testBasicParseAndApplyStatistics() throws IOException {
        GradleBuildVariant.NativeBuildConfigInfo stats =
                parseAndApplyStatistics(
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

        assertThat(stats.getLibrariesCount()).isEqualTo(1);
        GradleBuildVariant.NativeLibraryInfo library = stats.getLibraries(0);
        assertThat(library.getSourceFileCount()).isEqualTo(2);
        assertThat(library.getHasGlldbFlag()).isFalse();
    }

    @Test
    public void testParseAndApplyStatisticsHasGlldbFlag() throws IOException {
        GradleBuildVariant.NativeBuildConfigInfo stats =
                parseAndApplyStatistics(
                        "{\n"
                                + "  \"libraries\": {\n"
                                + "    \"example-debug-armeabi-v7a\": {\n"
                                + "      \"files\": [\n"
                                + "        {\n"
                                + "          \"src\": \"/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp/../../../../../other_sources/file1.cpp\",\n"
                                + "          \"flags\": \"\\\"-gcc-toolchain\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64\\\" \\\"-glldb\\\" \\\"-ffunction-sections\\\" \\\"-funwind-tables\\\" \\\"-fstack-protector-strong\\\" \\\"-Wno-invalid-command-line-argument\\\" \\\"-Wno-unused-command-line-argument\\\" \\\"-no-canonical-prefixes\\\" \\\"-fno-integrated-as\\\" \\\"-g\\\" \\\"-target\\\" \\\"armv7-none-linux-androideabi12\\\" \\\"-march=armv7-a\\\" \\\"-mfloat-abi=softfp\\\" \\\"-mfpu=vfpv3-d16\\\" \\\"-fno-exceptions\\\" \\\"-fno-rtti\\\" \\\"-mthumb\\\" \\\"-O0\\\" \\\"-UNDEBUG\\\" \\\"-fno-limit-debug-info\\\" \\\"-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/boost/1.0.63-rev24/boost_1_63_0.zip/boost_1_63_0\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/llvm-libc++abi/include\\\" \\\"-I/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/android/support/include\\\" \\\"-I/usr/local/google/home/jomof/projects/ndk-build-meet-cdep/app/src/main/cpp\\\" \\\"-std=c++11\\\" \\\"-DANDROID\\\" \\\"-D__ANDROID_API__=14\\\" \\\"-Wa,--noexecstack\\\" \\\"-Wformat\\\" \\\"-Werror=format-security\\\" \\\"--sysroot\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot\\\" \\\"-isystem\\\" \\\"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/arm-linux-androideabi\\\"\"\n"
                                + "        }\n"
                                + "      ]\n"
                                + "    }\n"
                                + "  }\n"
                                + "}");

        GradleBuildVariant.NativeLibraryInfo library = stats.getLibraries(0);
        assertThat(library.getHasGlldbFlag()).isTrue();
    }

    @Test
    public void testParseAndApplyStatisticsBuildTypeIsDebug() throws IOException {
        GradleBuildVariant.NativeBuildConfigInfo stats =
                parseAndApplyStatistics(
                        "{\n"
                                + "  \"libraries\": {\n"
                                + "    \"example-debug-armeabi-v7a\": {\n"
                                + "      \"buildType\": \"debug\"\n"
                                + "    }\n"
                                + "  }\n"
                                + "}");

        GradleBuildVariant.NativeLibraryInfo library = stats.getLibraries(0);
    }

    private GradleBuildVariant.NativeBuildConfigInfo parseAndApplyStatistics(String text)
            throws IOException {
        GradleBuildVariant.Builder stats = GradleBuildVariant.newBuilder();
        AndroidBuildGradleJsons.parseToMiniConfigAndGatherStatistics(
                new JsonReader(new StringReader(text)), stats);
        return stats.build().getNativeBuildConfig(0);
    }
}
