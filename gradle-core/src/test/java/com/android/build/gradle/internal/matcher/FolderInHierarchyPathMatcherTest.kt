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

/**
 * Tests for [FolderInHierarchyPathMatcher]
 */
class FolderInHierarchyPathMatcherTest {

    @Test
    internal fun testPatternMarching() {
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/foo/**").matches()).isTrue()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/foo*/**").matches()).isTrue()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/_*/**").matches()).isTrue()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/foo/bar/**").matches()).isFalse()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/*bar/**").matches()).isTrue()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("*bar/**").matches()).isFalse()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("bar/**").matches()).isFalse()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("bar*/**").matches()).isFalse()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/*bar").matches()).isFalse()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/bar").matches()).isFalse()
        assertThat(FolderInHierarchyPathMatcher.pattern.matcher("**/bar*").matches()).isFalse()
    }

    @Test
    fun testSingleCharacterFolder() {
        val matcher = FolderInHierarchyPathMatcher.factory().pattern().matcher("**/c/**")
        assertThat(matcher.matches())
        val folderInHierarchyPathMatcher = FolderInHierarchyPathMatcher(matcher)
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/c/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/b/c/d"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("a/a/a/b/c/d"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("a/b/c/d/d/d/d"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("a/b/c/d/d/d/d/"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("c/d/d/d/d/"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("a/b/c"))).isFalse()

        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("a/b/d/d/d/d/"))).isFalse()
    }

    @Test
    fun testSingleCharacterPrefixFolder() {
        val matcher = FolderInHierarchyPathMatcher.factory().pattern().matcher("**/_*/**")
        assertThat(matcher.matches())
        val folderInHierarchyPathMatcher = FolderInHierarchyPathMatcher(matcher)
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/c/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/c_c/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/c_/d/e"))).isFalse()

        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/_c/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("_c/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("a/_c/d"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("a/_c"))).isFalse()
    }

    @Test
    fun testSuffixFolder() {
        val matcher = FolderInHierarchyPathMatcher.factory().pattern().matcher("**/*bar/**")
        assertThat(matcher.matches())
        val folderInHierarchyPathMatcher = FolderInHierarchyPathMatcher(matcher)
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/bar/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/abar/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/abbar/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/barbar/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/bard/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/bar"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("bar/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/abbar"))).isFalse()
    }

    @Test
    fun testPrefixFolder() {
        val matcher = FolderInHierarchyPathMatcher.factory().pattern().matcher("**/bar*/**")
        assertThat(matcher.matches())
        val folderInHierarchyPathMatcher = FolderInHierarchyPathMatcher(matcher)
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/bar/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/barb/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/barab/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/barbar/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/dbar/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/bar"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("bar/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/barab"))).isFalse()
    }

    @Test
    fun testFolderName() {
        val matcher = FolderInHierarchyPathMatcher.factory().pattern().matcher("**/SCCS/**")
        assertThat(matcher.matches())
        val folderInHierarchyPathMatcher = FolderInHierarchyPathMatcher(matcher)
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/SCCS/d/e"))).isTrue()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/SCCS.1/d/e"))).isFalse()
        assertThat(folderInHierarchyPathMatcher.matches(Paths.get("/a/b/1.SCCS/d/e"))).isFalse()
    }
}
