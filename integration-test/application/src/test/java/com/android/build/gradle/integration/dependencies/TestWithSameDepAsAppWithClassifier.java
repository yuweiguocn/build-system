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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.testutils.apk.Apk;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests the handling of test dependency, when both the app and test app have a dependency on the
 * same artifact but with a different classifier
 */
public class TestWithSameDepAsAppWithClassifier {

    private static AndroidTestModule testApp = HelloWorldApp.noBuildFile();

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestApp(testApp).create();

    @BeforeClass
    public static void setUp() throws Exception {
        appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
                        + "\n"
                        + "android {\n"
                        + "  compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "  buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "  defaultConfig {\n"
                        + "    minSdkVersion 21\n"
                        + "    testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "  }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "  implementation 'org.threeten:threetenbp:1.3.3:no-tzdb'\n"
                        + "  androidTestImplementation 'org.threeten:threetenbp:1.3.3'\n"
                        + "  androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "  androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkTestApkContainsTzbd() throws Exception {
        project.execute("clean", "assembleDebugAndroidTest");

        Apk apk = project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);

        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResource("org/threeten/bp/TZDB.dat");
    }
}
