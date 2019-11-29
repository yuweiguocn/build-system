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

package com.android.build.gradle.integration.testing.unit;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Meta-level tests for the app-level unit testing support. Tests default values mode. */
@Category(SmokeTests.class)
public class UnitTestingDefaultValuesTest {
    @ClassRule
    public static GradleTestProject simpleProject =
            GradleTestProject.builder().fromTestProject("unitTestingDefaultValues").create();

    @Test
    public void testSimpleScenario() throws Exception {
        simpleProject.execute("testDebug");

        JUnitResults results =
                new JUnitResults(
                        simpleProject.file(
                                "build/test-results/testDebugUnitTest/TEST-com.android.tests.UnitTest.xml"));

        assertThat(results.getAllTestCases()).containsExactly("defaultValues");
        assertThat(results.outcome("defaultValues")).isEqualTo(JUnitResults.Outcome.PASSED);
    }
}
