/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.resources.Density
import com.google.common.truth.Truth
import org.junit.Test

class DensitySplitOptionsTest {

    @Test
    fun testDisabled() {
        val options = DensitySplitOptions()

        val values = options.applicableFilters
        Truth.assertThat(values).isEmpty()
    }

    @Test
    fun testUniversal() {
        val options = DensitySplitOptions()
        options.isEnable = true

        val values = options.applicableFilters
        // at this time we have 6 densities, maybe more later.
        Truth.assertThat(values.size).isAtLeast(6)
    }

    @Test
    fun testNonDefaultInclude() {
        val options = DensitySplitOptions()
        options.isEnable = true

        options.include(Density.TV.resourceValue)

        val values = options.applicableFilters

        // test TV is showing up.
        Truth.assertThat(values).contains(Density.TV.resourceValue)
        // test another default value also shows up
        Truth.assertThat(values).contains(Density.HIGH.resourceValue)
    }

    @Test
    fun testUnallowedInclude() {
        val options = DensitySplitOptions()
        options.isEnable = true

        options.include(Density.ANYDPI.resourceValue)

        val values = options.applicableFilters

        // test ANYDPI isn't there.
        Truth.assertThat(values).doesNotContain(Density.ANYDPI.resourceValue)
        // test another default value shows up
        Truth.assertThat(values).contains(Density.XHIGH.resourceValue)
    }

    @Test
    fun testExclude() {
        val options = DensitySplitOptions()
        options.isEnable = true

        options.exclude(Density.XXHIGH.resourceValue)

        val values = options.applicableFilters
        Truth.assertThat(values).doesNotContain(Density.XXHIGH.resourceValue)
    }
}