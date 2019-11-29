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

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*
import com.google.common.truth.Truth.assertThat

import org.junit.Test

class CmakeCommandLineKtTest {

    @Test
    fun checkDefine() {
        val parsed = parseCmakeArguments(listOf("-DX=Y"))
        assertThat(parsed).containsExactly(
            DefineProperty("-DX=Y", "X", "Y"))
        val arg = parsed.single() as DefineProperty
        assertThat(arg.sourceArgument).isEqualTo("-DX=Y")
        assertThat(arg.propertyName).isEqualTo("X")
        assertThat(arg.propertyValue).isEqualTo("Y")
    }

    @Test
    fun checkCmakeListsPath() {
        val parsed = parseCmakeArguments(listOf("-H<path-to-cmakelists>"))
        assertThat(parsed).containsExactly(
            CmakeListsPath("-H<path-to-cmakelists>", "<path-to-cmakelists>"))
        val arg = parsed.single() as CmakeListsPath
        assertThat(arg.sourceArgument).isEqualTo("-H<path-to-cmakelists>")
        assertThat(arg.path).isEqualTo("<path-to-cmakelists>")
    }

    @Test
    fun checkBinaryOutputPath() {
        val parsed = parseCmakeArguments(listOf("-B<path-to-binary>"))
        assertThat(parsed).containsExactly(
            BinaryOutputPath("-B<path-to-binary>", "<path-to-binary>"))
        val arg = parsed.single() as BinaryOutputPath
        assertThat(arg.sourceArgument).isEqualTo("-B<path-to-binary>")
        assertThat(arg.path).isEqualTo("<path-to-binary>")
    }

    @Test
    fun checkGeneratorName() {
        val parsed = parseCmakeArguments(listOf("-GAndroid Gradle - Ninja"))
        assertThat(parsed).containsExactly(
            GeneratorName("-GAndroid Gradle - Ninja", "Android Gradle - Ninja"))
        val arg = parsed.single() as GeneratorName
        assertThat(arg.sourceArgument).isEqualTo("-GAndroid Gradle - Ninja")
        assertThat(arg.generator).isEqualTo("Android Gradle - Ninja")
    }

    @Test
    fun checkUnknownArgument() {
        val parsed = parseCmakeArguments(listOf("-X"))
        assertThat(parsed).containsExactly(
            UnknownArgument("-X"))
        val arg = parsed.single() as UnknownArgument
        assertThat(arg.sourceArgument).isEqualTo("-X")
    }

    @Test
    fun definePropertyFrom() {
        val property = DefineProperty.from(ANDROID_NDK, "xyz")
        assertThat(property.sourceArgument).isEqualTo("-DANDROID_NDK=xyz")
    }

    @Test
    fun cmakeListsFrom() {
        val property = CmakeListsPath.from("xyz")
        assertThat(property.sourceArgument).isEqualTo("-Hxyz")
    }

    @Test
    fun hasBooleanPropertySet() {
        val prop = ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED
        val definedTrue = DefineProperty.from(prop, "true")
        val definedFalse = DefineProperty.from(prop, "false")
        assertThat(listOf<CommandLineArgument>().getCmakeBooleanProperty(prop)).isNull()
        assertThat(listOf(definedTrue).getCmakeBooleanProperty(prop)).isTrue()
        assertThat(listOf(definedFalse).getCmakeBooleanProperty(prop)).isFalse()
        assertThat(listOf(definedFalse, definedTrue).getCmakeBooleanProperty(prop)).isTrue()
        assertThat(listOf(definedTrue, definedFalse).getCmakeBooleanProperty(prop)).isFalse()
    }

    @Test
    fun getCmakeProperty() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).getCmakeProperty(CMAKE_BUILD_TYPE)
        assertThat(got).isEqualTo("type")
    }

    @Test
    fun getCmakeListsPathValue() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).getCmakeListsPathValue()
        assertThat(got).isEqualTo("path")
    }

    @Test
    fun removeProperty() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).removeProperty(CMAKE_BUILD_TYPE)
        assertThat(got).isEqualTo(listOf(toolchain))
    }
}