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

import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.*
import com.google.common.truth.Truth.assertThat
import org.junit.Rule

import org.junit.Test
import org.junit.rules.TemporaryFolder

class SdkSourcePropertiesTest {

    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    @Test
    fun fromFile() {
        val file = tmpFolder.newFile("source.properties")
        file.parentFile.mkdirs()
        file.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val properties = SdkSourceProperties.fromInstallFolder(file.parentFile)
        assertThat(properties.getValue(SDK_PKG_DESC))
            .isEqualTo("Android NDK")
        assertThat(properties.getValue(SDK_PKG_REVISION))
            .isEqualTo("17.2.4988734")
    }
}