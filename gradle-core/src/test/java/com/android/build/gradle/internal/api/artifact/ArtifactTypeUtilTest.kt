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

package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [ArtifactTypeUtil].
 */
class ArtifactTypeUtilTest {

    @Test
    fun valueOf() {
        assertThat("ANDROID_RESOURCES".toArtifactType())
                .isSameAs(SourceArtifactType.ANDROID_RESOURCES)
        assertThat("JAVA_COMPILE_CLASSPATH".toArtifactType())
                .isSameAs(BuildArtifactType.JAVA_COMPILE_CLASSPATH)
    }

    /**
     * Check that names is unique for all implementation of ArtifactType.
     */
    @Test
    fun uniqueArtifactTypeEnum() {
        val artifactTypes = mutableMapOf<String, ArtifactType>()
        checkArtifactType(artifactTypes, SourceArtifactType.values())
    }

    private fun checkArtifactType(
            allArtifactTypes : MutableMap<String, ArtifactType>,
            typesToCheck : Array<out ArtifactType>) {
        for (type in typesToCheck) {
            val existingType = allArtifactTypes[type.name()]
            if (existingType != null) {
                fail(
                        "Duplicated ArtifactType found. ${existingType::class.simpleName} and " +
                                "${type::class.simpleName} both contains ArtifactType with name " +
                                "'${type.name()}'.")
            }
        }
    }
}
