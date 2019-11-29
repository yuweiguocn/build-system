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

class FileNameWithSuffixPathMatcherTest {

    @Test
    fun testPattern() {
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/foo.bar").matches()).isTrue()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/*~").matches()).isTrue()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/*~/").matches()).isFalse()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/*~/*").matches()).isFalse()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/*~/**").matches()).isFalse()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("*/*~/**").matches()).isFalse()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/*~/**").matches()).isFalse()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("*~/**").matches()).isFalse()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("/a/b/*~").matches()).isFalse()
        assertThat(FileNameWithSuffixPathMatcher.factory().pattern().matcher("/a/*~").matches()).isFalse()
    }

    @Test
    fun testFullNameMatching() {
        val matcher = FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/foo.bar")
        val pathMatcher = FileNameWithSuffixPathMatcher(matcher)
        assertThat(pathMatcher.matches(Paths.get("/a/b/foo.bar"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/a/foo.bar"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/a/b/foobar"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/a/foobar"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("foobar"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("foo.bar"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/a/b/foo.bar2"))).isFalse()
    }

    @Test
    fun testFileSuffixMatching() {
        val matcher = FileNameWithSuffixPathMatcher.factory().pattern().matcher("**/*~")
        val pathMatcher = FileNameWithSuffixPathMatcher(matcher)
        assertThat(pathMatcher.matches(Paths.get("/a/b/foo.bar~"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/a/b/foo~"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/a/foo~"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/a/foo~~"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/a/foo.bar~"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/a/b/foo.bar"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/a/b/foo.bar~2"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("foo.bar~"))).isFalse()
    }
}
