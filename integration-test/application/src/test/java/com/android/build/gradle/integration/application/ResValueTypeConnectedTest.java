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

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResValueTypeConnectedTest {
    public static AndroidTestModule app = HelloWorldApp.noBuildFile();

    static {
        app.removeFileByName("HelloWorldTest.java");
        app.addFile(
                new TestSourceFile(
                        "src/androidTest/java/com/example/helloworld",
                        "ResValueTest.java",
                        "\n"
                                + "package com.example.helloworld;\n"
                                + "\n"
                                + "import android.support.test.InstrumentationRegistry;\n"
                                + "import android.support.test.runner.AndroidJUnit4;\n"
                                + "import org.junit.Assert;\n"
                                + "import org.junit.Test;\n"
                                + "import org.junit.runner.RunWith;\n"
                                + "\n"
                                + "@RunWith(AndroidJUnit4.class)\n"
                                + "public class ResValueTest {\n"
                                + "    @Test\n"
                                + "    public void testResValue() {\n"
                                + "        Assert.assertEquals(\"00\", InstrumentationRegistry.getTargetContext().getString(R.string.resString));\n"
                                + "    }\n"
                                + "}\n"));
    }

    @Rule public GradleTestProject project = GradleTestProject.builder().fromTestApp(app).create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        resValue \"array\",             \"resArray\",            \"foo\"\n"
                        + "        resValue \"attr\",              \"resAttr\",             \"foo\"\n"
                        + "        resValue \"bool\",              \"resBool\",             \"true\"\n"
                        + "        resValue \"color\",             \"resColor\",            \"#ffffff\"\n"
                        + "        resValue \"declare-styleable\", \"resDeclareStyleable\", \"foo\"\n"
                        + "        resValue \"dimen\",             \"resDimen\",            \"42px\"\n"
                        + "        resValue \"fraction\",          \"resFraction\",         \"42%\"\n"
                        + "        resValue \"id\",                \"resId\",               \"\"\n // needs to be empty or a resource reference"
                        + "        resValue \"integer\",           \"resInteger\",          \"42\"\n"
                        + "        resValue \"plurals\",           \"resPlurals\",          \"s\"\n"
                        + "        resValue \"string\",            \"resString\",           \"00\"  // resString becomes \"0\" if it is incorrectly treated  as int.\n"
                        + "        resValue \"style\",             \"resStyle\",            \"foo\"\n"
                        + "\n"
                        + "        minSdkVersion rootProject.supportLibMinSdk\n"
                        + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "}\n"
                        + "\n");
    }

    @Test
    @Category(DeviceTests.class)
    public void checkResValueIsTreatedAsAString() throws IOException, InterruptedException {
        project.execute("clean");
        project.executeConnectedCheck();
    }
}
