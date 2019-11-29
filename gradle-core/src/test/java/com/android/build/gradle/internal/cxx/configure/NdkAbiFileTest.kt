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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.ndk.AbiInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NdkAbiFileTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun ndkAbiInfosNdk14() : List<AbiInfo> {
        val file = ndkMetaAbisFile(File(temporaryFolder.root, "./ndk-14"))
        file.parentFile.mkdirs()
        return NdkAbiFile(file).abiInfoList
    }

    private fun ndkAbiInfosNdk16() : List<AbiInfo> {
        val contents = "{\n" +
                "  \"armeabi\": {\n" +
                "    \"bitness\": 32,\n" +
                "    \"default\": false,\n" +
                "    \"deprecated\": true\n" +
                "  },\n" +
                "  \"armeabi-v7a\": {\n" +
                "    \"bitness\": 32,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  },\n" +
                "  \"arm64-v8a\": {\n" +
                "    \"bitness\": 64,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  },\n" +
                "  \"mips\": {\n" +
                "    \"bitness\": 32,\n" +
                "    \"default\": false,\n" +
                "    \"deprecated\": true\n" +
                "  },\n" +
                "  \"mips64\": {\n" +
                "    \"bitness\": 64,\n" +
                "    \"default\": false,\n" +
                "    \"deprecated\": true\n" +
                "  },\n" +
                "  \"x86\": {\n" +
                "    \"bitness\": 32,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  },\n" +
                "  \"x86_64\": {\n" +
                "    \"bitness\": 64,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  }\n" +
                "}"

        val file = ndkMetaAbisFile(File(temporaryFolder.root, "./ndk-16"))
        file.parentFile.mkdirs()
        file.writeText(contents)
        return NdkAbiFile(file).abiInfoList
    }

    private fun ndkAbiInfosNdk17() : List<AbiInfo> {
        val contents = "{\n" +
                "  \"armeabi-v7a\": {\n" +
                "    \"bitness\": 32,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  },\n" +
                "  \"arm64-v8a\": {\n" +
                "    \"bitness\": 64,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  },\n" +
                "  \"x86\": {\n" +
                "    \"bitness\": 32,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  },\n" +
                "  \"x86_64\": {\n" +
                "    \"bitness\": 64,\n" +
                "    \"default\": true,\n" +
                "    \"deprecated\": false\n" +
                "  }\n" +
                "}"

        val file = ndkMetaAbisFile(File(temporaryFolder.root, "./ndk-17"))
        file.parentFile.mkdirs()
        file.writeText(contents)
        return NdkAbiFile(file).abiInfoList
    }

    @Test
    fun testNdk14() {
        val ndkAbiInfos = ndkAbiInfosNdk14()
        assertThat(ndkAbiInfos).hasSize(7)
        val first = ndkAbiInfos.first()
        assertThat(first.abi).isEqualTo(Abi.ARMEABI)
        assertThat(first.isDeprecated).isEqualTo(false)
        assertThat(first.isDefault).isEqualTo(true)
    }

    @Test
    fun testNdk16() {
        val ndkAbiInfos = ndkAbiInfosNdk16()
        assertThat(ndkAbiInfos).hasSize(7)
        val first = ndkAbiInfos.first()
        assertThat(first.abi).isEqualTo(Abi.ARMEABI)
        assertThat(first.isDeprecated).isEqualTo(true)
        assertThat(first.isDefault).isEqualTo(false)
    }

    @Test
    fun testNdk17() {
        val ndkAbiInfos = ndkAbiInfosNdk17()
        assertThat(ndkAbiInfos).hasSize(4)
        val first = ndkAbiInfos.first()
        assertThat(first.abi).isEqualTo(Abi.ARMEABI_V7A)
        assertThat(first.isDeprecated).isEqualTo(false)
        assertThat(first.isDefault).isEqualTo(true)
    }
}