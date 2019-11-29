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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JacocoTest {

    private static final String CLASS_NAME = "com/example/B";
    private static final String CLASS_FULL_TYPE = "L" + CLASS_NAME + ";";
    private static final String CLASS_SRC_LOCATION = "src/main/java/" + CLASS_NAME + ".java";

    private static final String CLASS_CONTENT =
            "package com.example;\n"
                    + "public class B { }";

    private static final AndroidTestModule TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder().fromTestApp(TEST_APP).create();

    @Before
    public void enableCodeCoverage() throws Exception {
        Files.append(
                "\nandroid.buildTypes.debug.testCoverageEnabled true\n",
                project.getBuildFile(),
                Charsets.UTF_8);
    }

    @Test
    public void addAndRemoveClass() throws Exception {
        project.execute("assembleDebug");
        assertThat(project.getApk("debug")).doesNotContainClass(CLASS_FULL_TYPE);

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.addFile(CLASS_SRC_LOCATION, CLASS_CONTENT);
                    project.execute("assembleDebug");
                    assertThat(project.getApk("debug")).containsClass(CLASS_FULL_TYPE);
                });
        project.execute("assembleDebug");
        assertThat(project.getApk("debug")).doesNotContainClass(CLASS_FULL_TYPE);
    }

    /** Regression test for http://b/65573026. */
    @Test
    public void testDisablingAndEnablingJacoco() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.testCoverageEnabled false\n");
        project.executor().run("assembleDebug");

        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.testCoverageEnabled true\n");
        project.executor().run("assembleDebug");
    }
}
