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

package com.android.build.gradle.integration.application;


import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Check that a project can depend on a jar dependency published by another app project.
 */
public class ExternalTestProjectTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder().create();

    private File app2BuildFile;

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(project.getSettingsFile(),
                "include ':app1'\ninclude ':app2'\n");

        File rootFile = project.getTestDir();

        // app1 module
        File app1 = new File(rootFile, "app1");
        HelloWorldApp.noBuildFile().write(app1, null);
        TestFileUtils.appendToFile(new File(app1, "build.gradle"),
                "apply plugin: 'com.android.application'\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "}\n"
                + "\n"
                + "task testJar(type: Jar, dependsOn: 'assembleRelease') {\n"
                + "\n"
                + "}\n"
                + "\n"
                + "configurations {\n"
                + "    testLib\n"
                + "}\n"
                + "\n"
                + "artifacts {\n"
                + "    testLib testJar\n"
                + "}\n");

        // app2 module
        File app2 = new File(rootFile, "app2");
        HelloWorldApp.noBuildFile().write(app2, null);
        app2BuildFile = new File(app2, "build.gradle");
    }

    @Test
    public void testExtraJarDependency() throws Exception {
        TestFileUtils.appendToFile(app2BuildFile,
                "apply plugin: 'com.android.application'\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(path: ':app1', configuration: 'testLib')\n"
                + "}\n");

        project.execute("clean", "app2:assembleDebug");
    }
}
