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

package com.android.build.gradle.integration.instant

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ParameterizedAndroidProject
import com.android.builder.model.Variant
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.BuildActionFailureException
import org.hamcrest.core.IsInstanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/* Tests for getting builder models with parameters. */
class ParameterizedModelTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestProject("instantAppSimpleProject")
        .withoutNdk()
        .create()

    @Rule
    @JvmField
    var exception: ExpectedException = ExpectedException.none()

    @Test
    @Throws(Exception::class)
    fun getVariantModelWithNonParameterizedAPI() {
        exception.expect(BuildActionFailureException::class.java)
        exception.expectCause(IsInstanceOf.instanceOf(RuntimeException::class.java))
        // Get Variant model with non-parameterized API.
        project.model().fetch(Variant::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun getParameterizedAndroidProject() {
        // Get AndroidProject with parameterized API.
        val (androidProject, variants) = project.model().fetchMulti(
            ParameterizedAndroidProject::class.java
        )[":app"]!!

        // Verify that AndroidProject model doesn't contain Variant instance.
        assertThat(androidProject.variants).isEmpty()
        // Verify that AndroidProject model contains a list of variant names.
        assertThat(androidProject.variantNames).containsExactly("debug", "release")
        // Verify that Variant models have the correct name.
        assertThat(variants.map { it.name }).containsExactly("debug", "release")
    }
}
