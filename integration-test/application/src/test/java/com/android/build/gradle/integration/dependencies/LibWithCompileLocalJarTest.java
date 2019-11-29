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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for compile local jar in libs
 */
public class LibWithCompileLocalJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        appendToFile(
                project.getBuildFile(),
                "\n" +
                        "apply plugin: \"com.android.library\"\n" +
                        "\n" +
                        "android {\n" +
                        "    compileSdkVersion " + DEFAULT_COMPILE_SDK_VERSION + "\n" +
                        "    buildToolsVersion \"" + DEFAULT_BUILD_TOOL_VERSION + "\"\n" +
                        "}\n" +
                        "\n" +
                        "dependencies {\n" +
                        "    compile files(\"libs/util-1.0.jar\")\n" +
                        "}\n");

        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkCompileLocalJarIsPackaged() throws Exception {
        // search in secondary jars only.
        TruthHelper.assertThat(project.getAar("debug"))
                .containsSecondaryClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void testLibraryTestContainsLocalJarClasses() throws Exception {
        project.execute("assembleDebugAndroidTest");

        TruthHelper.assertThat(project.getTestApk())
                .containsClass("Lcom/example/android/multiproject/person/People;");
    }
}
