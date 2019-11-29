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

package com.android.build.gradle.internal.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for [AndroidXDependency]. */
class AndroidXDependencyTest {

    @Test
    fun testAndroidXDependency() {
        val androidXDependency = AndroidXDependency.fromPreAndroidXDependency(
            "com.android.support",
            "support-annotations"
        )
        assertThat(androidXDependency.group).isEqualTo("androidx.annotation")
        assertThat(androidXDependency.module).isEqualTo("annotation")
        assertThat(androidXDependency.oldGroup).isEqualTo("com.android.support")
        assertThat(androidXDependency.oldModule).isEqualTo("support-annotations")
    }
}