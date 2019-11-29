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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Test for the standalone lint plugin.
 *
 *
 * To run just this test:
 * ./gradlew :base:build-system:integration-test:application:test -D:base:build-system:integration-test:application:test.single=LintStandaloneCustomRuleTest
 */
class LintStandaloneCustomRuleTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder().fromTestProject("lintStandaloneCustomRules").create()

    @Test
    @Throws(Exception::class)
    fun checkStandaloneLint() {
        project.execute("clean", "lint")

        val file = project.getSubproject("library").file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("MyClass.java:3: Error: Do not implement java.util.List directly [UnitTestLintCheck2]")
        assertThat(file).contains("1 errors, 0 warnings")
    }
}