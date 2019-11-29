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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.options.BooleanOption
import com.google.common.base.Throwables
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Integration test for the dependency checker.  */
class DependencyCheckerTest {

    private val testProject =
        MinimalSubProject.app("com.example.app").apply {
            appendToBuild("afterEvaluate { configurations.debugRuntimeClasspath.files() }")
        }

    @get:Rule
    val app = GradleTestProject.builder()
        .fromTestApp(testProject)
        .create()

    @Test
    fun checkFailureAndWarning() {
        val failure = app.executor().expectFailure().run("tasks")
        val rootCause = Throwables.getRootCause(failure.exception!!)
        assertThat(rootCause).hasMessageThat()
            .contains("Configuration 'debugRuntimeClasspath' was resolved")

        val warning = app.executor()
            .with(BooleanOption.DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION, false)
            .with(
                BooleanOption.WARN_ABOUT_DEPENDENCY_RESOLUTION_AT_CONFIGURATION,
                true
            )
            .run("tasks")
        assertThat(warning.stdout)
            .contains("Configuration 'debugRuntimeClasspath' was resolved")
        assertThat(warning.stdout)
            .contains(app.buildFile.absolutePath.toString() + ":")

        // Assert no exceptions while fetching the models
        val models = app.model().fetchAndroidProjects()
        assertThat(models).isNotNull()
    }
}
