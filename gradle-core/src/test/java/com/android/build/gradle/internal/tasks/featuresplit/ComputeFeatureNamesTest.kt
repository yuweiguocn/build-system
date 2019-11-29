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

package com.android.build.gradle.internal.tasks.featuresplit

import com.google.common.truth.Truth.assertThat
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/** Tests for the [computeFeatureNames] method  */
class ComputeFeatureNamesTest {

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @Test
    fun testComputeFeatureNames() {
        val features =
                listOf(
                        FeatureSplitDeclaration(":A", "id"),
                        FeatureSplitDeclaration(":foo:B", "id"),
                        FeatureSplitDeclaration(":C", "id"))

        assertThat(computeFeatureNames(features).values).containsExactly("A", "B", "C")
    }

    @Test
    fun testRootFeatureModule() {
        val features = listOf(FeatureSplitDeclaration(":", "id"))
        exception.expect(RTEMatcher("Root module ':' is used as a feature module. This is not supported."))
        computeFeatureNames(features)
    }

    @Test
    fun testComputeInvalidFeatureNames() {
        val features =
            listOf(
                FeatureSplitDeclaration(":A$", "id"),
                FeatureSplitDeclaration(":foo:B-C", "id"),
                FeatureSplitDeclaration(":C", "id"))

        exception.expect(
            RTEMatcher(
                "The following feature module names contain invalid characters. Feature module " +
                        "names can only contain letters, digits and underscores.\n" +
                        "\t-> A\$\n" +
                        "\t-> B-C"
            )
        )
        computeFeatureNames(features)
    }


    @Test
    fun testDuplicatedFeatureNames() {
        val features =
            listOf(
                FeatureSplitDeclaration(":A", "id"),
                FeatureSplitDeclaration(":foo:A", "id"))

        exception.expect(RTEMatcher("Module name 'A' is used by multiple modules. All dynamic features must have a unique name.\n" +
                "\t-> :A\n" +
                "\t-> :foo:A"))
        computeFeatureNames(features)
    }
}

/**
 * custom [BaseMatcher] for RuntimeException with message.
 */
private class RTEMatcher(private val message: String): BaseMatcher<Any>() {
    override fun matches(item: Any): Boolean {
        if (item !is RuntimeException) {
            return false
        }

        return item.message == message
    }

    override fun describeTo(description: Description) {
        description.appendText(message)
    }
}
