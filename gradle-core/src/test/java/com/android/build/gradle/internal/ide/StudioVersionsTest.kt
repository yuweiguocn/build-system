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

package com.android.build.gradle.internal.ide

import com.google.common.truth.Truth.assertThat
import org.gradle.api.InvalidUserDataException
import org.junit.Test
import kotlin.test.assertFailsWith

class StudioVersionsTest {

    private val oldVersion = MajorMinorVersion(1, 1)

    @Test
    fun testNotInjected() {
        // Check is lenient when no studio version is injected.
        verifyStudioIsNotOld(null, oldVersion)
    }

    @Test
    fun testInvalidVersionsInjected() {
        assertFailsWith<InvalidUserDataException> {
            verifyStudioIsNotOld("", oldVersion)
        }
    }

    @Test
    fun testNewerStudio() {
        verifyStudioIsNotOld("3.3.1.6", MajorMinorVersion(3, 2))
    }

    @Test
    fun testMatchingVersion() {
        verifyStudioIsNotOld("3.2.1.6", MajorMinorVersion(3, 2))
    }

    @Test
    fun testTooOldStudioVersion() {
        val exception = assertFailsWith<RuntimeException> {
            verifyStudioIsNotOld("3.1.3.6", MajorMinorVersion(3, 2))
        }

        assertThat(exception)
            .hasMessageThat()
            .contains("please retry with Android Studio 3.2 or newer.")
    }

    @Test
    fun checkMajorMinorVersionOrdering() {
        val versionsInOrder = listOf<MajorMinorVersion>(
            MajorMinorVersion(1, 2),
            MajorMinorVersion(1, 3),
            MajorMinorVersion(2, 2),
            MajorMinorVersion(2, 3)
        )

        for (version in versionsInOrder) {
            assertThat(version).isEquivalentAccordingToCompareTo(version)
        }

        assertThat(versionsInOrder.asReversed().sorted())
            .isEqualTo(versionsInOrder)
    }

    @Test
    fun checkValidVersionParsing() {
        assertThat(parseVersion("3.3.0.6")).isEqualTo(MajorMinorVersion(3, 3))
        assertThat(parseVersion("3.3.0-beta1")).isEqualTo(MajorMinorVersion(3, 3))
    }

    @Test
    fun checkInvalidVersionParsing() {
        assertThat(parseVersion("")).isNull()
        assertThat(parseVersion("1")).isNull()
        assertThat(parseVersion("A")).isNull()
        assertThat(parseVersion("-1")).isNull()
        assertThat(parseVersion("A.2")).isNull()
        assertThat(parseVersion("-1.2")).isNull()
        assertThat(parseVersion("1.B")).isNull()
        assertThat(parseVersion("1.-2")).isNull()
    }
}