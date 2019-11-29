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

import static com.android.build.gradle.integration.testing.unit.JUnitResults.Outcome.PASSED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.base.Throwables;
import org.gradle.tooling.BuildException;
import org.junit.ClassRule;
import org.junit.Test;

/** Meta-level tests for the app-level unit testing support. */
public class UnitTestingBuildTypesSupportTest {
    @ClassRule
    public static GradleTestProject flavorsProject =
            GradleTestProject.builder().fromTestProject("unitTestingBuildTypes").create();

    @Test
    public void testsForAGivenBuildTypeAreOnlyCompiledAgainstTheBuildType() throws Exception {
        flavorsProject.execute("clean", "testDebug");

        JUnitResults results =
                new JUnitResults(
                        flavorsProject.file(
                                "build/test-results/testDebugUnitTest/TEST-com.android.tests.UnitTest.xml"));

        assertThat(results.outcome("referenceProductionCode")).isEqualTo(PASSED);
        assertThat(results.outcome("resourcesOnClasspath")).isEqualTo(PASSED);
        assertThat(results.outcome("useDebugOnlyDependency")).isEqualTo(PASSED);

        flavorsProject.execute("clean", "testBuildTypeWithResource");
        results =
                new JUnitResults(
                        flavorsProject.file(
                                "build/test-results/testBuildTypeWithResourceUnitTest/TEST-com.android.tests.UnitTest.xml"));

        assertThat(results.outcome("javaResourcesOnClasspath")).isEqualTo(PASSED);
        assertThat(results.outcome("prodJavaResourcesOnClasspath")).isEqualTo(PASSED);

        try {
            // Tests for release try to compile against a debug-only class.
            flavorsProject.execute("testRelease");
            fail();
        } catch (BuildException e) {
            assertThat(Throwables.getRootCause(e).toString())
                    .contains("CompilationFailedException");
        }
    }
}
