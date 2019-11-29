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

package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TestingSupportLibraryConnectedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(helloWorldApp).create();

    public static final AndroidTestModule helloWorldApp = HelloWorldApp.noBuildFile();

    static {
        /* Junit 4 now maps tests annotated with @Ignore and tests that throw
        AssumptionFailureExceptions as skipped. */
        helloWorldApp.addFile(
                new TestSourceFile(
                        "src/androidTest/java/com/example/helloworld",
                        "FailureAssumptionTest.java",
                        "\n"
                                + "package com.example.helloworld;\n"
                                + "\n"
                                + "import android.support.test.runner.AndroidJUnit4;\n"
                                + "import android.support.test.filters.SmallTest;\n"
                                + "\n"
                                + "import org.junit.Ignore;\n"
                                + "import org.junit.Test;\n"
                                + "import org.junit.runner.RunWith;\n"
                                + "\n"
                                + "import static org.junit.Assert.fail;\n"
                                + "import static org.junit.Assume.assumeTrue;\n"
                                + "\n"
                                + "@RunWith(AndroidJUnit4.class)\n"
                                + "@SmallTest\n"
                                + "public class FailureAssumptionTest {\n"
                                + "    @Test\n"
                                + "    public void checkAssumptionIsSkipped() {\n"
                                + "        assumeTrue(false);\n"
                                + "        fail(\"Tests with failing assumptions should be skipped\");\n"
                                + "    }\n"
                                + "\n"
                                + "    @Test\n"
                                + "    @Ignore\n"
                                + "    public void checkIgnoreTestsArePossible() {\n"
                                + "        fail(\"Tests with @Ignore annotation should be skipped\");\n"
                                + "    }\n"
                                + "\n"
                                + "    @Test\n"
                                + "    public void checkThisTestPasses() {\n"
                                + "        System.err.println(\"Test executed\");\n"
                                + "    }\n"
                                + "}\n"));

        helloWorldApp.replaceFile(
                new TestSourceFile(
                        "src/main",
                        "AndroidManifest.xml",
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "      package=\"com.example.helloworld\"\n"
                                + "      android:versionCode=\"1\"\n"
                                + "      android:versionName=\"1.0\">\n"
                                + "\n"
                                + "    <application android:label=\"@string/app_name\">\n"
                                + "        <activity android:name=\".HelloWorld\"\n"
                                + "                  android:label=\"@string/app_name\">\n"
                                + "            <intent-filter>\n"
                                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                + "            </intent-filter>\n"
                                + "        </activity>\n"
                                + "    </application>\n"
                                + "</manifest>\n"));
    }

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "        testInstrumentationRunner \"android.support.test.runner.AndroidJUnitRunner\"\n"
                        + "        minSdkVersion 18\n"
                        + "    }\n"
                        + "    dependencies {\n"
                        + "        androidTestCompile 'com.android.support:support-annotations:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "        androidTestCompile 'com.android.support.test:runner:"
                        + TestVersions.TEST_SUPPORT_LIB_VERSION
                        + "'\n"
                        + "        androidTestCompile 'com.android.support.test:rules:"
                        + TestVersions.TEST_SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    @Category(DeviceTests.class)
    public void testIgnoredTestsAreNotRun() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
