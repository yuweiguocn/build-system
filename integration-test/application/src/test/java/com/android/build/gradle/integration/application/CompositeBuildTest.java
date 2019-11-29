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

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Integration test for composite build. */
public class CompositeBuildTest {

    @Rule
    public GradleTestProject app =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .withName("app")
                    .withDependencyChecker(false)
                    .create();

    @Rule
    public GradleTestProject lib =
            GradleTestProject.builder()
                    .fromTestApp(new EmptyAndroidTestApp())
                    .withName("lib")
                    .create();

    @Rule
    public GradleTestProject androidLib =
            GradleTestProject.builder()
                    .fromTestApp(
                            new MultiModuleTestProject(
                                    ImmutableMap.of(
                                            "androidLib1",
                                            new EmptyAndroidTestApp("com.example.androidLib1"),
                                            "androidLib2",
                                            new EmptyAndroidTestApp("com.example.androidLib2"))))
                    .withName("androidLib")
                    .withDependencyChecker(false)
                    .create();

    @Before
    public void setUp() throws IOException {
        Files.write(
                app.file("settings.gradle").toPath(),
                ("includeBuild('../lib') {\n"
                                + "    dependencySubstitution {\n"
                                + "        substitute module('com.example:lib') with project(':')\n"
                                + "    }\n"
                                + "}\n"
                                + "includeBuild('../androidLib') {\n"
                                + "    dependencySubstitution {\n"
                                + "        substitute module('com.example:androidLib1') with project(':androidLib1')\n"
                                + "        substitute module('com.example:androidLib2') with project(':androidLib2')\n"
                                + "    }\n"
                                + "}\n")
                        .getBytes());

        TestFileUtils.appendToFile(
                app.getBuildFile(),
                "android {\n"
                        + "    buildTypes.debug.testCoverageEnabled true\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    compile 'com.example:lib'\n"
                        + "    compile 'com.example:androidLib1'\n"
                        + "    compile 'com.example:androidLib2'\n"
                        + "}\n");

        // lib is just an empty project.
        lib.file("settings.gradle").createNewFile();
        TestFileUtils.appendToFile(lib.getBuildFile(), "apply plugin: 'java'\n");

        // b/62428620 - Add a dummy file to the classpath to cause androidLib project to be loaded
        // in a different classloader.  This used to cause problem in composite build in older
        // Gradle plugin.
        TestFileUtils.searchAndReplace(
                androidLib.getBuildFile(),
                "buildscript { apply from: \"../commonBuildScript.gradle\" }",
                "buildscript {"
                        + "    apply from: \"../commonBuildScript.gradle\"\n"
                        + "    dependencies {"
                        + "        classpath files('dummy')\n"
                        + "    }\n"
                        + "}\n");

        // androidLib1 and androidLib2 are empty aar project.
        Files.write(
                androidLib.file("settings.gradle").toPath(),
                ("include 'androidLib1'\n" + "include 'androidLib2'\n").getBytes());
        String androidLibBuildGradle =
                "apply plugin: 'com.android.library'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "}\n";
        TestFileUtils.appendToFile(
                androidLib.getSubproject(":androidLib1").getBuildFile(), androidLibBuildGradle);

        TestFileUtils.appendToFile(
                androidLib.getSubproject(":androidLib2").getBuildFile(), androidLibBuildGradle);
    }

    @Test
    public void assembleDebug() throws Exception {
        app.execute(":assembleDebug");
        assertThat(app.getApk(GradleTestProject.ApkType.DEBUG)).exists();
    }
}
