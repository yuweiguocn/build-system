/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.gradle.tooling.BuildException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DslTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "apply plugin: 'com.android.library'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "}");
    }

    @Test
    public void applicationIdInDefaultConfig() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        applicationId = 'foo'\n"
                        + "    }\n"
                        + "}\n");

        // Just need to run the 'tasks' task to trigger error
        try {
            project.execute("tasks");
            fail("Broken build file did not throw exception");
        } catch (BuildException e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String expectedMsg =
                    "Library projects cannot set applicationId. "
                            + "applicationId is set to 'foo' in default config.";
            assertEquals(expectedMsg, cause.getMessage());
        }
    }

    @Test
    public void applicationIdSuffix() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            applicationIdSuffix = 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // Just need to run the 'tasks' task to trigger error
        try {
            project.execute("tasks");
            fail("Broken build file did not throw exception");
        } catch (BuildException e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String expectedMsg =
                    "Library projects cannot set applicationIdSuffix."
                            + " applicationIdSuffix is set to 'foo' in build type 'debug'.";
            assertEquals(expectedMsg, cause.getMessage());
        }
    }

    @Test
    public void applicationIdInProductFlavor() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "android {\n"
                        + "    productFlavors {\n"
                        + "        myFlavor {\n"
                        + "            applicationId = 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // Just need to run the 'tasks' task to trigger error
        try {
            project.execute("tasks");
            fail("Broken build file did not throw exception");
        } catch (BuildException e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String expectedMsg =
                    "Library projects cannot set applicationId."
                            + " applicationId is set to 'foo' in flavor 'myFlavor'.";
            assertEquals(expectedMsg, cause.getMessage());
        }
    }
}
