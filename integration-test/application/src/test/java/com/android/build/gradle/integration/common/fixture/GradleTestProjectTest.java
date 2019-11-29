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

package com.android.build.gradle.integration.common.fixture;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.gradle.tooling.BuildException;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GradleTestProjectTest {

    @Test
    public void smokeTest() throws Throwable {
        HelloWorldApp helloWorldApp = HelloWorldApp.forPlugin("com.android.application");
        GradleTestProject project = GradleTestProject.builder().fromTestApp(helloWorldApp).create();

        run(
                project,
                () -> {
                    project.execute("help");
                });
    }

    @Test
    public void checkAssertionHandling() throws Throwable {
        HelloWorldApp helloWorldApp = HelloWorldApp.forPlugin("com.android.application");
        GradleTestProject project = GradleTestProject.builder().fromTestApp(helloWorldApp).create();

        AssertionError error = new AssertionError("should be caught");

        PrintStream stderr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture));
        try {
            run(
                    project,
                    () -> {
                        project.execute("help");
                        throw error;
                    });
            fail("Expected assertion to propagate");
        } catch (AssertionError e) {
            assertThat(e).isEqualTo(error);
            assertThat(capture.toString()).contains("Tasks to be executed: [task ':help']");
        } finally {
            System.setErr(stderr);
        }
    }

    @Test
    public void checkBuildFailureHandling() throws Throwable {
        HelloWorldApp helloWorldApp = HelloWorldApp.forPlugin("com.android.application");
        GradleTestProject project = GradleTestProject.builder().fromTestApp(helloWorldApp).create();

        PrintStream stderr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture));
        try {
            run(
                    project,
                    () -> {
                        project.executor().run("help2");
                    });
            fail("Expected task not to be found");
        } catch (BuildException e) {
            assertThat(capture.toString()).contains("Task 'help2' not found");
        } finally {
            System.setErr(stderr);
        }
    }

    @Test
    public void checkModelFailureHandling() throws Throwable {
        HelloWorldApp helloWorldApp = HelloWorldApp.forPlugin("com.android.application_typo");
        GradleTestProject project = GradleTestProject.builder().fromTestApp(helloWorldApp).create();

        PrintStream stderr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture));
        try {
            run(
                    project,
                    () -> {
                        project.model().fetchAndroidProjects();
                    });
            fail("Expected model get to fail");
        } catch (BuildException e) {
            assertThat(capture.toString())
                    .contains("Plugin with id 'com.android.application_typo' not found");
        } finally {
            System.setErr(stderr);
        }
    }

    private static void run(@NonNull GradleTestProject project, @NonNull ProjectAction action)
            throws Throwable {
        project.apply(
                        new Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                action.run();
                            }
                        },
                        Description.createTestDescription(
                                GradleTestProjectTest.class, "checkPerformanceDataGiven"))
                .evaluate();
    }

    private interface ProjectAction {
        void run() throws Exception;
    }
}
