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

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Paths

/**
 * Tests for [FileExtensionWithPrefixPathMatcher]
 */
class FileExtensionWithPrefixPathMatcherTest {

    @Test
    fun testPatternMatching() {
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("/META-INF/services/*.xml").matches()).isTrue()
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("/META-INF/*.xml").matches()).isTrue()
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("/META-INF/services/*.xml_2").matches()).isTrue()

        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("/META-INF/services/**").matches()).isFalse()
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("META-INF/services/**").matches()).isFalse()
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("*.xml").matches()).isFalse()
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("/*.xml").matches()).isFalse()
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("/META-INF/*").matches()).isFalse()
        Truth.assertThat(FileExtensionWithPrefixPathMatcher.pattern
            .matcher("/*xml").matches()).isFalse()
    }

    @Test
    fun testCrossingFolderBoundaryPrefixMatching() {
        val matcher = FileExtensionWithPrefixPathMatcher.factory().pattern()
            .matcher("/META-INF/services/*.xml")
        val pathMatcher = FileExtensionWithPrefixPathMatcher.factory().build(matcher)
        assertThat(pathMatcher.matches(Paths.get("/META-INF/services/foo.xml"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/META-INF/foo.xml"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("foo.xml"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("META-INF/services/foo.xml"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/META-INF/services/foo.bar"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/META-INF/services/foo"))).isFalse()
    }

    @Test
    fun testSingleFolderPrefixMatching() {
        val matcher = FileExtensionWithPrefixPathMatcher.factory().pattern()
            .matcher("/META-INF/*.xml")
        val pathMatcher = FileExtensionWithPrefixPathMatcher.factory().build(matcher)
        assertThat(pathMatcher.matches(Paths.get("/META-INF/foo.xml"))).isTrue()
        assertThat(pathMatcher.matches(Paths.get("/foo.xml"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("foo.xml"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/META-INF/services/foo.xml"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("META-INF/foo.xml"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/META-INF/foo.bar"))).isFalse()
        assertThat(pathMatcher.matches(Paths.get("/META-INF/foo"))).isFalse()
    }
}