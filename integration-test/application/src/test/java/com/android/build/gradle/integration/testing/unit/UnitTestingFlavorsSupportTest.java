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

import static com.android.build.gradle.integration.testing.unit.JUnitResults.Outcome.FAILED;
import static com.android.build.gradle.integration.testing.unit.JUnitResults.Outcome.PASSED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Throwables;
import org.gradle.tooling.BuildException;
import org.junit.ClassRule;
import org.junit.Test;

/** Meta-level tests for the app-level unit testing support. */
public class UnitTestingFlavorsSupportTest {
    @ClassRule
    public static GradleTestProject flavorsProject =
            GradleTestProject.builder().fromTestProject("unitTestingFlavors").create();

    @Test
    public void testsForAGivenFlavorAreOnlyCompiledAgainstTheFlavor() throws Exception {
        TestFileUtils.appendToFile(
                flavorsProject.file("src/doesntBuild/java/com/android/tests/Broken.java"),
                "this is broken");

        flavorsProject.execute("clean", "testBuildsPassesDebug");

        JUnitResults results =
                new JUnitResults(
                        flavorsProject.file(
                                "build/test-results/testBuildsPassesDebugUnitTest/TEST-com.android.tests.PassingTest.xml"));

        assertThat(results.outcome("referenceFlavorSpecificCode")).isEqualTo(PASSED);

        try {
            flavorsProject.execute("testDoesntBuildPassesDebug");
            fail();
        } catch (BuildException e) {
            assertThat(Throwables.getRootCause(e).toString())
                    .contains("CompilationFailedException: ");
        }
    }

    @Test
    public void taskForAGivenFlavorOnlyRunsTheCorrectTests() throws Exception {
        flavorsProject.execute("clean", "testBuildsPassesDebug");

        try {
            flavorsProject.execute("testBuildsFailsDebug");
            fail();
        } catch (BuildException e) {
            assertThat(Throwables.getRootCause(e).getMessage())
                    .startsWith("There were failing tests.");

            JUnitResults results =
                    new JUnitResults(
                            flavorsProject.file(
                                    "build/test-results/testBuildsFailsDebugUnitTest/TEST-com.android.tests.FailingTest.xml"));
            assertThat(results.outcome("failingTest")).isEqualTo(FAILED);
        }
    }
}
