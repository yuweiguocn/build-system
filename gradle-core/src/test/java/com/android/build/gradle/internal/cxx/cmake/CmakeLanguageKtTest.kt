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

package com.android.build.gradle.internal.cxx.cmake

import com.google.common.truth.Truth.assertThat

import org.junit.Test

class CmakeLanguageKtTest {

    @Test
    fun isCmakeConstantTruthyTrue() {
        assertThat(isCmakeConstantTruthy("1")).isTrue()
        assertThat(isCmakeConstantTruthy("ON")).isTrue()
        assertThat(isCmakeConstantTruthy("YES")).isTrue()
        assertThat(isCmakeConstantTruthy("TRUE")).isTrue()
        assertThat(isCmakeConstantTruthy("Y")).isTrue()

        assertThat(isCmakeConstantTruthy("on")).isTrue()
        assertThat(isCmakeConstantTruthy("yes")).isTrue()
        assertThat(isCmakeConstantTruthy("true")).isTrue()
        assertThat(isCmakeConstantTruthy("y")).isTrue()

        assertThat(isCmakeConstantTruthy("192")).isTrue()
        assertThat(isCmakeConstantTruthy("-192")).isTrue()
    }

    @Test
    fun isCmakeConstantTruthyFalse() {
        assertThat(isCmakeConstantTruthy("0")).isFalse()
        assertThat(isCmakeConstantTruthy("OFF")).isFalse()
        assertThat(isCmakeConstantTruthy("NO")).isFalse()
        assertThat(isCmakeConstantTruthy("FALSE")).isFalse()
        assertThat(isCmakeConstantTruthy("N")).isFalse()
        assertThat(isCmakeConstantTruthy("IGNORE")).isFalse()
        assertThat(isCmakeConstantTruthy("NOTFOUND")).isFalse()
        assertThat(isCmakeConstantTruthy("MYFLAG-NOTFOUND")).isFalse()

        assertThat(isCmakeConstantTruthy("off")).isFalse()
        assertThat(isCmakeConstantTruthy("no")).isFalse()
        assertThat(isCmakeConstantTruthy("false")).isFalse()
        assertThat(isCmakeConstantTruthy("n")).isFalse()
        assertThat(isCmakeConstantTruthy("ignore")).isFalse()
        assertThat(isCmakeConstantTruthy("notfound")).isFalse()
        assertThat(isCmakeConstantTruthy("MYFLAG-notfound")).isFalse()

        assertThat(isCmakeConstantTruthy("ABC")).isFalse()
    }
}