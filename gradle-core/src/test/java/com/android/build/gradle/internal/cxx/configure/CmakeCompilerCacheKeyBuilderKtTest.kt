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

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.*
import com.android.builder.model.Version
import com.google.common.truth.Truth.assertThat
import org.junit.After

import org.junit.Test
import java.io.File

class CmakeCompilerCacheKeyBuilderKtTest {
    private val ndkFolder = File("./my-ndk")
    private val sourceProperties = File(ndkFolder, "source.properties")
    private val logger = RecordingLoggingEnvironment()

    @After
    fun after() {
        logger.close()
    }

    @Test
    fun gradlePluginVersionExists() {
        ndkFolder.deleteRecursively()
        ndkFolder.mkdirs()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val key = makeCmakeCompilerCacheKey(
            listOf(
                DefineProperty.from(ANDROID_NDK, ndkFolder.path)
            )
        )!!
        assertThat(key.gradlePluginVersion).isEqualTo(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        assertThat(logger.messageCount).isEqualTo(0)
    }

    @Test
    fun androidNdkFolderDoesntExist() {
        ndkFolder.deleteRecursively()
        val key = makeCmakeCompilerCacheKey(
            listOf(
                DefineProperty.from(ANDROID_NDK, ndkFolder.path)
            )
        )
        assertThat(key).isNull()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.warnings).containsExactly(
            "ANDROID_NDK location (.${File.separatorChar}my-ndk) had no source.properties")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun sourcePropertiesDoesntExist() {
        ndkFolder.deleteRecursively()
        ndkFolder.mkdirs()
        val key = makeCmakeCompilerCacheKey(
            listOf(
                DefineProperty.from(ANDROID_NDK, ndkFolder.path)
            )
        )
        assertThat(key).isNull()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.warnings).containsExactly(
            "ANDROID_NDK location (.${File.separatorChar}my-ndk) had no source.properties")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun sourcePropertiesExists() {
        ndkFolder.deleteRecursively()
        ndkFolder.mkdirs()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val key = makeCmakeCompilerCacheKey(
            listOf(
                DefineProperty.from(ANDROID_NDK, ndkFolder.path)
            )
        )!!
        assertThat(key.ndkInstallationFolder).isNotNull()
        assertThat(key.args).isEmpty()
        assertThat(key.ndkSourceProperties).isNotNull()
        assertThat(key.ndkSourceProperties.getValue(SDK_PKG_DESC))
            .isEqualTo("Android NDK")
        assertThat(key.ndkSourceProperties.getValue(SDK_PKG_REVISION))
            .isEqualTo("17.2.4988734")
        assertThat(logger.messageCount).isEqualTo(0)
    }

    @Test
    fun realWorldExample() {
        ndkFolder.deleteRecursively()
        ndkFolder.mkdirs()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val args = """
            -HC:\Users\jomof\AndroidStudioProjects\MyApplication10\app
            -BC:\Users\jomof\AndroidStudioProjects\MyApplication10\app\.externalNativeBuild\cmake\debug\x86
            -DANDROID_ABI=x86
            -DANDROID_PLATFORM=android-16
            -DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\Users\jomof\AndroidStudioProjects\MyApplication10\app\build\intermediates\cmake\debug\obj\x86
            -DCMAKE_BUILD_TYPE=Debug
            -DANDROID_NDK=${ndkFolder.path}
            -DCMAKE_CXX_FLAGS=-DX=Y
            -DCMAKE_SYSTEM_NAME=Android
            -DCMAKE_ANDROID_ARCH_ABI=x86
            -DCMAKE_SYSTEM_VERSION=16
            -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
            -DCMAKE_ANDROID_NDK=${ndkFolder.path}
            -DCMAKE_TOOLCHAIN_FILE=C:\Users\jomof\AppData\Local\Android\Sdk\ndk-bundle\build\cmake\android.toolchain.cmake
            -G Ninja
            -DCMAKE_MAKE_PROGRAM=C:\Users\jomof\AppData\Local\Android\Sdk\cmake\3.10.2.4988404\bin\ninja.exe"""
            .trimIndent().split("\n")
        val commandLine = parseCmakeArguments(args)
        val key = makeCmakeCompilerCacheKey(commandLine)!!
        println(key.toJsonString())
        assertThat(key.ndkInstallationFolder).isNotNull()
        assertThat(key.args).containsExactly(
            "-DANDROID_ABI\u003dx86",
            "-DANDROID_PLATFORM\u003dandroid-16",
            "-DCMAKE_CXX_FLAGS\u003d-DX\u003dY",
            "-DCMAKE_SYSTEM_NAME\u003dAndroid",
            "-DCMAKE_ANDROID_ARCH_ABI\u003dx86",
            "-DCMAKE_SYSTEM_VERSION\u003d16",
            "-DCMAKE_ANDROID_NDK\u003d\${ANDROID_NDK}"
        )
        assertThat(key.ndkSourceProperties).isNotNull()
        assertThat(key.ndkSourceProperties.getValue(SDK_PKG_DESC))
            .isEqualTo("Android NDK")
        assertThat(key.ndkSourceProperties.getValue(SDK_PKG_REVISION))
            .isEqualTo("17.2.4988734")
    }
}