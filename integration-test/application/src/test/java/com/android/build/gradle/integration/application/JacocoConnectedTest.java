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

package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class JacocoConnectedTest {
    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public Adb adb = new Adb();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.testCoverageEnabled true");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        adb.exclusiveAccess();
        project.executor().run("connectedCheck");
        assertThat(project.file("build/reports/coverage/debug/index.html")).exists();
        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.kt.html"))
                .exists();
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheckNamespacedRClasses() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.aaptOptions.namespaced = true\n");

        adb.exclusiveAccess();
        project.executor().run("connectedCheck");

        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.kt.html"))
                .exists();
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheckWithOrchestrator() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion 16\n"
                        + "android.defaultConfig.testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "android.testOptions.execution 'ANDROID_TEST_ORCHESTRATOR'\n"
                        + "dependencies {\n"
                        + "  androidTestImplementation 'com.android.support.test:runner:1.0.2'\n"
                        + "  androidTestUtil 'com.android.support.test:orchestrator:1.0.2'\n"
                        + "}");

        String testSrc =
                "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.support.test.runner.AndroidJUnit4;\n"
                        + "import org.junit.Test;\n"
                        + "import org.junit.runner.RunWith;\n"
                        + "\n"
                        + "@RunWith(AndroidJUnit4.class)\n"
                        + "public class ExampleTest {\n"
                        + "    @Test\n"
                        + "    public void test1() { }\n"
                        + "\n"
                        + "    @Test\n"
                        + "    public void test2() { }\n"
                        + "}\n";
        Path exampleTest =
                project.getTestDir()
                        .toPath()
                        .resolve("src/androidTest/java/com/example/helloworld/ExampleTest.java");
        Files.createDirectories(exampleTest.getParent());
        Files.write(exampleTest, testSrc.getBytes());

        adb.exclusiveAccess();
        project.executor().run("connectedCheck");
        List<File> files =
                FileUtils.find(
                        project.file("build/outputs/code_coverage"), Pattern.compile(".*\\.ec"));

        // ExampleTest has 2 methods, and there should be at least 2 .ec files
        Truth.assertThat(files.size()).isAtLeast(2);
        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.kt.html"))
                .exists();
    }
}
