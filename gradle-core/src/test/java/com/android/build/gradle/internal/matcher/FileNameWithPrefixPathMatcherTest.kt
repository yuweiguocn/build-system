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

open class FileNameWithPrefixPathMatcherTest {

    @Test
    fun testPattern() {
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("**/_*").matches()).isTrue()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("**/.*").matches()).isTrue()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("**/some_prefix*").matches()).isTrue()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("**/_*/**").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("**/_*/").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("_*/").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("/_*/").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("*/_*/").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("/_*/*").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("a/_*/*").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("a/_*/b").matches()).isFalse()
        assertThat(FileNameWithPrefixPathMatcher.factory().pattern().matcher("_*/b").matches()).isFalse()
    }

    @Test
    fun testMatching() {
        val pathMatcher =
            FileNameWithPrefixPathMatcher(FileNameWithPrefixPathMatcher.factory().pattern().matcher("**/_*"))
        assertThat(pathMatcher.matches(Paths.get("a/_b"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("ab/_b"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("a/b/_b"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("a/b/c/_b"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("_b"))).isTrue()

        assertThat(pathMatcher.matches(Paths.get("_b/c"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("_b/c"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("a_b"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("p/aa_b"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("p/aa_"))).isFalse()
    }
}