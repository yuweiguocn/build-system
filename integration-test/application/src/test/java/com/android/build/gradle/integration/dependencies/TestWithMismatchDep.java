/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.utils.StringHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
/**
 * Tests the handling of test dependencies.
 */
public class TestWithMismatchDep {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("testDependency")
            .create();

    @Before
    public void setUp() throws Exception {
        appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation 'com.google.guava:guava:19.0'\n"
                        + "}\n");
    }

    private static final String EXCEPTION_MSG =
            "Cannot find a version of 'com.google.guava:guava' that satisfies the version constraints: \n"
                    + "   Dependency path ':testDependency:unspecified' --> 'com.google.guava:guava:19.0'\n"
                    + "   Dependency path ':testDependency:unspecified' --> 'com.google.guava:guava:18.0'\n"
                    + "   Constraint path ':testDependency:unspecified' --> 'com.google.guava:guava:{strictly 18.0}' because of the following reason: debugRuntimeClasspath uses version 18.0\n";
    private static final String ERROR_MSG =
            StringHelper.toSystemLineSeparator(
                            "Could not resolve all files for configuration ':debugAndroidTestRuntimeClasspath'.\n")
                    + StringHelper.toSystemLineSeparator(
                            "   > Could not resolve com.google.guava:guava:19.0.\n")
                    + StringHelper.toSystemLineSeparator("     Required by:\n")
                    + StringHelper.toSystemLineSeparator("         project :\n")
                    + "      > Cannot find a version of 'com.google.guava:guava' that satisfies the version constraints: \n"
                    + "           Dependency path ':testDependency:unspecified' --> 'com.google.guava:guava:19.0'\n"
                    + "           Dependency path ':testDependency:unspecified' --> 'com.google.guava:guava:18.0'\n"
                    + "           Constraint path ':testDependency:unspecified' --> 'com.google.guava:guava:{strictly 18.0}' because of the following reason: debugRuntimeClasspath uses version 18.0";
    @Test
    public void testMismatchDependencyBreaksTestBuild() throws Exception {
        // want to check the log, so can't use Junit's expected exception mechanism.

        GradleBuildResult result =
                project.executor().expectFailure().run("assembleAndroidTest");
        Throwable t = result.getException();
        while (t.getCause() != null) {
            t = t.getCause();
        }

        // looks like we can't actually test the instance t against GradleException
        // due to it coming through the tooling API from a different class loader.
        assertThat(t.getClass().getCanonicalName()).isEqualTo("org.gradle.api.GradleException");
        assertThat(t.getMessage()).isEqualTo(EXCEPTION_MSG);

        // check there is a version of the error, after the task name:
        assertThat(result.getStderr()).contains(ERROR_MSG);
    }

    @Test
    public void testMismatchDependencyDoesNotBreakDebugBuild() throws Exception {
        project.executor().run("assembleDebug");
    }
}
