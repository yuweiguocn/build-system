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

package com.android.build.gradle.internal.ide

import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStreamingParser
import com.google.common.truth.Truth.assertThat
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Test
import java.io.File
import java.io.StringReader

class NativeAndroidProjectBuilderTest {
    @Test
    fun testHeaderFileThatWasPassedAsSource() {
        val builder = NativeAndroidProjectBuilder("project")
        val visitor = NativeAndroidProjectBuilder.JsonStreamingVisitor(builder, "variant", null)
        val reader = JsonReader(
            StringReader(
                "{\n" +
                        "  \"buildFiles\": [\n" +
                        "    \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/../../../Android/Sdk/ndk-bundle/build/cmake/android.toolchain.cmake\",\n" +
                        "    \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/CMakeLists.txt\"\n" +
                        "  ],\n" +
                        "  \"cleanCommands\": [\n" +
                        "    \"/usr/local/google/home/jomof/Android/Sdk/cmake/3.10.4604376/bin/cmake --build /usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64 --target clean\"\n" +
                        "  ],\n" +
                        "  \"libraries\": {\n" +
                        "    \"native-lib-Release-x86_64\": {\n" +
                        "      \"buildCommand\": \"/usr/local/google/home/jomof/Android/Sdk/cmake/3.10.4604376/bin/cmake --build /usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64 --target native-lib\",\n" +
                        "      \"buildType\": \"release\",\n" +
                        "      \"toolchain\": \"1351519597\",\n" +
                        "      \"abi\": \"x86_64\",\n" +
                        "      \"artifactName\": \"native-lib\",\n" +
                        "      \"files\": [\n" +
                        "        {\n" +
                        "          \"src\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/src/main/cpp/native-lib.hpp\",\n" +
                        "          \"workingDirectory\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64\"\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"src\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/src/main/cpp/native-lib.cpp\",\n" +
                        "          \"flags\": \"-isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/x86_64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security   -O2 -DNDEBUG  -fPIC  \",\n" +
                        "          \"workingDirectory\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64\"\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"output\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/build/intermediates/cmake/release/obj/x86_64/libnative-lib.so\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"toolchains\": {\n" +
                        "    \"1351519597\": {\n" +
                        "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"cFileExtensions\": [],\n" +
                        "  \"cppFileExtensions\": [\n" +
                        "    \"cpp\"\n" +
                        "  ]\n" +
                        "}"
            )
        )
        AndroidBuildGradleJsonStreamingParser(reader, visitor).parse()
        val result = builder.buildNativeAndroidProject()!!
        val sourceFiles = result.artifacts.toTypedArray()[0].sourceFiles
        assertThat(sourceFiles.size).isEqualTo(1)
        assertThat(
            sourceFiles.toTypedArray()[0].filePath.toString()
                .endsWith("cpp" + File.separatorChar + "native-lib.cpp")
        )
            .isTrue()
    }

    @Test
    fun testStringTable() {

        val builder = NativeAndroidProjectBuilder("project")
        val visitor = NativeAndroidProjectBuilder.JsonStreamingVisitor(builder, "variant", null)
        val reader = JsonReader(
            StringReader(
                "{\n" +
                        "  \"stringTable\": {\n" +
                        "    \"0\": \"-flag1 -flag2\",\n" +
                        "    \"1\": \"/my/working/directory\"\n" +
                        "  },\n" +
                        "  \"buildFiles\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkDebugVsRelease_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/../../../../../../../../../../../prebuilts/studio/sdk/linux/ndk-bundle/build/cmake/android.toolchain.cmake\",\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkDebugVsRelease_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/CMakeLists.txt\"\n" +
                        "  ],\n" +
                        "  \"cleanCommands\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkDebugVsRelease_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target clean\"\n" +
                        "  ],\n" +
                        "  \"libraries\": {\n" +
                        "    \"hello-jni-Debug-armeabi-v7a\": {\n" +
                        "      \"buildCommand\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkDebugVsRelease_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target hello-jni\",\n" +
                        "      \"buildType\": \"debug\",\n" +
                        "      \"toolchain\": \"3396088147\",\n" +
                        "      \"abi\": \"armeabi-v7a\",\n" +
                        "      \"artifactName\": \"hello-jni\",\n" +
                        "      \"files\": [\n" +
                        "        {\n" +
                        "          \"src\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkDebugVsRelease_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/src/main/cpp/hello-jni.cpp\",\n" +
                        "          \"flagsOrdinal\": 0,\n" +
                        "          \"workingDirectoryOrdinal\": 1\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"output\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkDebugVsRelease_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/build/intermediates/cmake/debug/obj/armeabi-v7a/libhello-jni.so\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"toolchains\": {\n" +
                        "    \"3396088147\": {\n" +
                        "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/studio/sdk/linux/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"cFileExtensions\": [],\n" +
                        "  \"cppFileExtensions\": [\n" +
                        "    \"cpp\"\n" +
                        "  ]\n" +
                        "}"
            )
        )
        AndroidBuildGradleJsonStreamingParser(reader, visitor).parse()
        val result = builder.buildNativeAndroidProject()!!
        val sourceFiles = result.artifacts.toTypedArray()[0].sourceFiles
        val settings = result.settings.toTypedArray()[0].compilerFlags
        assertThat(sourceFiles.size).isEqualTo(1)
        assertThat(settings)
            .isEqualTo(listOf("-flag1", "-flag2"))
    }

    @Test
    fun testStringTableWorksWithCompositeBuilder() {
        val reader = JsonReader(
            StringReader(
                "{\n" +
                        "  \"stringTable\": {\n" +
                        "    \"0\": \"-g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -DTEST_CPP_FLAG -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info  -fPIC  \",\n" +
                        "    \"1\": \"/my/working/directory\"\n" +
                        "  },\n" +
                        "  \"buildFiles\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/../../../../../../../../../../../prebuilts/studio/sdk/linux/ndk-bundle/build/cmake/android.toolchain.cmake\",\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/CMakeLists.txt\"\n" +
                        "  ],\n" +
                        "  \"cleanCommands\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target clean\"\n" +
                        "  ],\n" +
                        "  \"libraries\": {\n" +
                        "    \"hello-jni-Debug-armeabi-v7a\": {\n" +
                        "      \"buildCommand\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target hello-jni\",\n" +
                        "      \"buildType\": \"debug\",\n" +
                        "      \"toolchain\": \"3396088147\",\n" +
                        "      \"abi\": \"armeabi-v7a\",\n" +
                        "      \"artifactName\": \"hello-jni\",\n" +
                        "      \"files\": [\n" +
                        "        {\n" +
                        "          \"src\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/src/main/cpp/hello-jni.cpp\",\n" +
                        "          \"flagsOrdinal\": 0,\n" +
                        "          \"workingDirectoryOrdinal\": 1\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"output\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/build/intermediates/cmake/debug/obj/armeabi-v7a/libhello-jni.so\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"toolchains\": {\n" +
                        "    \"3396088147\": {\n" +
                        "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/studio/sdk/linux/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"cFileExtensions\": [],\n" +
                        "  \"cppFileExtensions\": [\n" +
                        "    \"cpp\"\n" +
                        "  ]\n" +
                        "}"
            )
        )

        val builder = NativeAndroidProjectBuilder("name")
        builder.addJson(
            reader,
            "variant-name",
            GradleBuildVariant.NativeBuildConfigInfo.newBuilder()
        )
        val result = builder.buildNativeAndroidProject()!!
        val flags = result.settings.toTypedArray()[0].compilerFlags
        assertThat(flags).contains("-g")

    }

    @Test
    fun testReproCaseWhereWrongVisitWorkingFolderCalledByVisitor() {
        val reader = JsonReader(
            StringReader(
                "{\n" +
                        "  \"stringTable\": {\n" +
                        "    \"0\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkModel_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a\",\n" +
                        "    \"1\": \"-g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -DTEST_CPP_FLAG -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info  -fPIC  \"\n" +
                        "  },\n" +
                        "  \"buildFiles\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkModel_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/../../../../../../../../../../../prebuilts/studio/sdk/linux/ndk-bundle/build/cmake/android.toolchain.cmake\",\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkModel_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/CMakeLists.txt\"\n" +
                        "  ],\n" +
                        "  \"cleanCommands\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkModel_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target clean\"\n" +
                        "  ],\n" +
                        "  \"libraries\": {\n" +
                        "    \"hello-jni-Debug-armeabi-v7a\": {\n" +
                        "      \"buildCommand\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkModel_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target hello-jni\",\n" +
                        "      \"buildType\": \"debug\",\n" +
                        "      \"toolchain\": \"3396088147\",\n" +
                        "      \"abi\": \"armeabi-v7a\",\n" +
                        "      \"artifactName\": \"hello-jni\",\n" +
                        "      \"files\": [\n" +
                        "        {\n" +
                        "          \"src\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkModel_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/src/main/cpp/hello-jni.cpp\",\n" +
                        "          \"flagsOrdinal\": 1,\n" +
                        "          \"workingDirectoryOrdinal\": 0\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"output\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkModel_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/build/intermediates/cmake/debug/obj/armeabi-v7a/libhello-jni.so\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"toolchains\": {\n" +
                        "    \"3396088147\": {\n" +
                        "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/studio/sdk/linux/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"cFileExtensions\": [],\n" +
                        "  \"cppFileExtensions\": [\n" +
                        "    \"cpp\"\n" +
                        "  ]\n" +
                        "}"
            )
        )
        val builder = NativeAndroidProjectBuilder("name")
        builder.addJson(
            reader,
            "variant-name",
            GradleBuildVariant.NativeBuildConfigInfo.newBuilder()
        )
        val result = builder.buildNativeAndroidProject()!!
        assertThat(result.settings.size).isEqualTo(1)
        val flags = result.settings.toTypedArray()[0].compilerFlags
        assertThat(flags).contains("-g")
    }

    @Test
    fun testBuildNativeVariantAbi() {
        val reader = JsonReader(
            StringReader(
                "{\n" +
                        "  \"stringTable\": {\n" +
                        "    \"0\": \"-g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -DTEST_CPP_FLAG -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info  -fPIC  \",\n" +
                        "    \"1\": \"/my/working/directory\"\n" +
                        "  },\n" +
                        "  \"buildFiles\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/../../../../../../../../../../../prebuilts/studio/sdk/linux/ndk-bundle/build/cmake/android.toolchain.cmake\",\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/CMakeLists.txt\"\n" +
                        "  ],\n" +
                        "  \"cleanCommands\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target clean\"\n" +
                        "  ],\n" +
                        "  \"libraries\": {\n" +
                        "    \"hello-jni-Debug-armeabi-v7a\": {\n" +
                        "      \"buildCommand\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target hello-jni\",\n" +
                        "      \"buildType\": \"debug\",\n" +
                        "      \"toolchain\": \"3396088147\",\n" +
                        "      \"abi\": \"armeabi-v7a\",\n" +
                        "      \"artifactName\": \"hello-jni\",\n" +
                        "      \"files\": [\n" +
                        "        {\n" +
                        "          \"src\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/src/main/cpp/hello-jni.cpp\",\n" +
                        "          \"flagsOrdinal\": 0,\n" +
                        "          \"workingDirectoryOrdinal\": 1\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"output\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/build/intermediates/cmake/debug/obj/armeabi-v7a/libhello-jni.so\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"toolchains\": {\n" +
                        "    \"3396088147\": {\n" +
                        "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/studio/sdk/linux/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"cFileExtensions\": [],\n" +
                        "  \"cppFileExtensions\": [\n" +
                        "    \"cpp\"\n" +
                        "  ]\n" +
                        "}"
            )
        )

        val builder = NativeAndroidProjectBuilder("name", "armeabi-v7a")
        builder.addJson(
            reader,
            "variant-name",
            GradleBuildVariant.NativeBuildConfigInfo.newBuilder()
        )
        val result = builder.buildNativeVariantAbi("my-variant")!!
        val flags = result.settings.toTypedArray()[0].compilerFlags
        assertThat(flags).contains("-g")
        assertThat(result.artifacts).hasSize(1)
    }

    @Test
    fun testBuildNativeVariantAbiUnmatchedAbiName() {
        val reader = JsonReader(
            StringReader(
                "{\n" +
                        "  \"stringTable\": {\n" +
                        "    \"0\": \"-g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -fno-integrated-as -mthumb -Wa,--noexecstack -Wformat -Werror=format-security  -DTEST_CPP_FLAG -O0 -fno-limit-debug-info -O0 -fno-limit-debug-info  -fPIC  \",\n" +
                        "    \"1\": \"/my/working/directory\"\n" +
                        "  },\n" +
                        "  \"buildFiles\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/../../../../../../../../../../../prebuilts/studio/sdk/linux/ndk-bundle/build/cmake/android.toolchain.cmake\",\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/CMakeLists.txt\"\n" +
                        "  ],\n" +
                        "  \"cleanCommands\": [\n" +
                        "    \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target clean\"\n" +
                        "  ],\n" +
                        "  \"libraries\": {\n" +
                        "    \"hello-jni-Debug-armeabi-v7a\": {\n" +
                        "      \"buildCommand\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/tools/common/cmake/linux/3.8.2/bin/cmake --build /usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/relative/path/cmake/debug/armeabi-v7a --target hello-jni\",\n" +
                        "      \"buildType\": \"debug\",\n" +
                        "      \"toolchain\": \"3396088147\",\n" +
                        "      \"abi\": \"armeabi-v7a\",\n" +
                        "      \"artifactName\": \"hello-jni\",\n" +
                        "      \"files\": [\n" +
                        "        {\n" +
                        "          \"src\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/src/main/cpp/hello-jni.cpp\",\n" +
                        "          \"flagsOrdinal\": 0,\n" +
                        "          \"workingDirectoryOrdinal\": 1\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"output\": \"/usr/local/google/home/jomof/projects/studio-master-dev/out/build/base/build-system/integration-test/application/build/tests/NativeModelTest/checkUpToDate_model___CMAKELISTS_ARGUMENTS_WITH_CMAKE_VERSION_/project/build/intermediates/cmake/debug/obj/armeabi-v7a/libhello-jni.so\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"toolchains\": {\n" +
                        "    \"3396088147\": {\n" +
                        "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/projects/studio-master-dev/prebuilts/studio/sdk/linux/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"cFileExtensions\": [],\n" +
                        "  \"cppFileExtensions\": [\n" +
                        "    \"cpp\"\n" +
                        "  ]\n" +
                        "}"
            )
        )

        val builder = NativeAndroidProjectBuilder("name", "x86")
        builder.addJson(
            reader,
            "variant-name",
            GradleBuildVariant.NativeBuildConfigInfo.newBuilder()
        )
        val result = builder.buildNativeVariantAbi("my-variant")!!
        assertThat(result.settings).hasSize(0)
        assertThat(result.artifacts).hasSize(0)
    }
}