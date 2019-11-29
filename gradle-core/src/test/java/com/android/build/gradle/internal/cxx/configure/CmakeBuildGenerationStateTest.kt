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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule

import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CmakeBuildGenerationStateTest {

    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    @Test
    fun toAndFromFile() {
        val file = tmpFolder.newFile("./file.json")
        val state = CmakeBuildGenerationState(listOf(CmakePropertyValue("a", "b")))
        state.toFile(file)
        val state2 = CmakeBuildGenerationState.fromFile(file)
        assertThat(state2).isEqualTo(state)
    }
}