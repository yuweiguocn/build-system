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

package com.android.build.gradle.internal.scope;

import com.android.build.VariantOutput.FilterType
import com.android.build.gradle.internal.variant.MultiOutputPolicy
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the {@link SplitList} class.
 */
class SplitListTest {

    class SplitActionTest(private val filterTypes: ImmutableSet<FilterType>): SplitList.SplitAction {
        val collectedValues: MutableList<String> = mutableListOf()

        override fun apply(filterType: FilterType, filters: ImmutableSet<String>) {
            if (filterTypes.contains(filterType)) collectedValues.addAll(filters)
        }
    }

    @Test
    fun testForEach() {
        val splitList = SplitList(
            ImmutableSet.of("mdpi", "xxhdpi"),
            ImmutableSet.of("en", "pt"),
            ImmutableSet.of("x86", "arm64-v8a"),
            ImmutableSet.of("resConfigFilter"))

        val action = SplitActionTest(ImmutableSet.of(FilterType.ABI))

        splitList.forEach(action)
        assertThat(action.collectedValues).containsExactly("x86", "arm64-v8a")
    }

    @Test
    fun testGetSplits() {
        val splitList = SplitList(
            ImmutableSet.of("mdpi", "xxhdpi"),
            ImmutableSet.of("en", "pt"),
            ImmutableSet.of("x86", "arm64-v8a"),
            ImmutableSet.of("resConfigFilter"))

        assertThat(splitList.getSplits(MultiOutputPolicy.MULTI_APK)).isEmpty()
        assertThat(splitList.getSplits(MultiOutputPolicy.SPLITS))
            .containsExactly("mdpi", "xxhdpi", "en", "pt")
    }
}
