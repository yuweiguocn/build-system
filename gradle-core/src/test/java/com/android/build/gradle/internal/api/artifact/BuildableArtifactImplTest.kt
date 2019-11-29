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

package com.android.build.gradle.internal.api.artifact

import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeFilesProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test for [BuildableArtifactImpl]
 */
class BuildableArtifactImplTest {
    private val provider = FakeFilesProvider()

    @Test
    fun default() {
        val collection = BuildableArtifactImpl(provider.files())
        assertThat(collection.fileCollection).isNotNull()
        assertThat(collection.isEmpty()).isTrue()
        assertThat(collection.files).isEmpty()
        assertThat(collection.iterator().hasNext()).isFalse()
    }

    @Test
    fun singleFile() {
        val file = provider.file("foo")
        val collection = BuildableArtifactImpl(provider.files(file))
        assertThat(collection.isEmpty()).isFalse()
        assertThat(collection.files).hasSize(1)
        assertThat(collection).containsExactly(file)
    }

    @Test
    fun multipleFiles() {
        val files = listOf(provider.file("foo"), provider.file("bar"))
        val collection = BuildableArtifactImpl(provider.files(files))
        assertThat(collection.isEmpty()).isFalse()
        assertThat(collection.files).containsExactlyElementsIn(files)
        assertThat(collection).containsExactlyElementsIn(files)
    }
}