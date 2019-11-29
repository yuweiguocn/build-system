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

package com.android.build.gradle.internal.matcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Paths

class NoWildcardPathMatcherTest {

    @Test
    fun testPattern() {
        assertThat(NoWildcardPathMatcher.factory().pattern().matcher("/a/b").matches()).isTrue()
        assertThat(NoWildcardPathMatcher.factory().pattern().matcher("**/a").matches()).isFalse()
        assertThat(NoWildcardPathMatcher.factory().pattern().matcher("/a/**").matches()).isFalse()
        assertThat(NoWildcardPathMatcher.factory().pattern().matcher("/a/*.java").matches()).isFalse()
        assertThat(NoWildcardPathMatcher.factory().pattern().matcher("/a/b{.class,.java}").matches()).isFalse()
    }

    @Test
    fun testMatches() {
        val noWildcardPathMatcher = NoWildcardPathMatcher(NoWildcardPathMatcher.factory().pattern().matcher("/a/b"))
        assertThat(noWildcardPathMatcher.matches(Paths.get("/a/b"))).isTrue()
        assertThat(noWildcardPathMatcher.matches(Paths.get("/a/b/c"))).isFalse()
        assertThat(noWildcardPathMatcher.matches(Paths.get("/aa/b"))).isFalse()
        assertThat(noWildcardPathMatcher.matches(Paths.get("/a/bb"))).isFalse()
        assertThat(noWildcardPathMatcher.matches(Paths.get("/a/a/b"))).isFalse()
    }
}