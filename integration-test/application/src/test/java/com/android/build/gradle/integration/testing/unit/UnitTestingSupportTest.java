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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

/** Meta-level tests for the app-level unit testing support. Checks the default values mode. */
public class UnitTestingSupportTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTesting").create();

    @Test
    public void appProject() throws Exception {
        doTestProject(project);
    }

    @Test
    public void libProject() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "com.android.application", "com.android.library");
        doTestProject(project);
    }

    private static void doTestProject(GradleTestProject project) throws Exception {
        project.execute("clean", "test");

        for (String variant : ImmutableList.of("Debug", "Release")) {
            String dirName = "test" + variant + "UnitTest";
            String unitTestXml =
                    "build/test-results/" + dirName + "/TEST-com.android.tests.UnitTest.xml";
            JUnitResults unitTextResults = new JUnitResults(project.file(unitTestXml));

            assertThat(unitTextResults.getStdErr()).contains("INFO: I can use commons-logging");

            checkResults(
                    unitTestXml,
                    ImmutableSet.of(
                            "aarDependencies",
                            "commonsLogging",
                            "enums",
                            "exceptions",
                            "instanceFields",
                            "javaResourcesOnClasspath",
                            "kotlinProductionCode",
                            "mockFinalClass",
                            "mockFinalMethod",
                            "mockInnerClass",
                            "prodJavaResourcesOnClasspath",
                            "prodRClass",
                            "referenceProductionCode",
                            "taskConfiguration"),
                    ImmutableSet.of("thisIsIgnored"),
                    project);

            checkResults(
                    "build/test-results/" + dirName + "/TEST-com.android.tests.NonStandardName.xml",
                    ImmutableSet.of("passingTest"),
                    ImmutableSet.of(),
                    project);

            checkResults(
                    "build/test-results/" + dirName + "/TEST-com.android.tests.TestInKotlin.xml",
                    ImmutableSet.of("passesInKotlin"),
                    ImmutableSet.of(),
                    project);
        }
    }

    private static void checkResults(
            String xmlPath, Set<String> passed, Set<String> ignored, GradleTestProject project)
            throws Exception {
        JUnitResults results = new JUnitResults(project.file(xmlPath));
        assertThat(results.getAllTestCases())
                .containsExactlyElementsIn(Sets.union(ignored, passed));
        for (String pass : passed) {
            assertThat(results.outcome(pass)).isEqualTo(JUnitResults.Outcome.PASSED);
        }
        for (String ignore : ignored) {
            assertThat(results.outcome(ignore)).isEqualTo(JUnitResults.Outcome.SKIPPED);
        }
    }
}
