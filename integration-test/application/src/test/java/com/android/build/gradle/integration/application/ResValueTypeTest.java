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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test resValue for string type is treated as String. */
public class ResValueTypeTest {
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
                                + "import android.test.AndroidTestCase;\n"
                                + "\n"
                                + "public class ResValueTest extends AndroidTestCase {\n"
                                + "    public void testResValue() {\n"
                                + "        assertEquals(\"00\", getContext().getString(R.string.resString));\n"
                                + "    }\n"
                                + "}\n"));
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().fromTestApp(app).create();

    @BeforeClass
    public static void setUp() throws IOException {
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
                        + "        resValue \"id\",                \"resId\",               \"42\"\n"
                        + "        resValue \"integer\",           \"resInteger\",          \"42\"\n"
                        + "        resValue \"plurals\",           \"resPlurals\",          \"s\"\n"
                        + "        resValue \"string\",            \"resString\",           \"00\"  // resString becomes \"0\" if it is incorrectly treated  as int.\n"
                        + "        resValue \"style\",             \"resStyle\",            \"foo\"\n"
                        + "    }\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        app = null;
    }

    @Test
    public void checkStringTagIsUsedInGeneratedDotXmlFile()
            throws IOException, InterruptedException {
        project.execute("clean", "generateDebugResValue");
        File outputFile = project.file("build/generated/res/resValues/debug/values/generated.xml");
        assertTrue("Missing file: " + outputFile, outputFile.isFile());
        assertEquals(
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Values from default config. -->\n"
                        + "    <array name=\"resArray\">foo</array>\n"
                        + "\n"
                        + "    <attr name=\"resAttr\">foo</attr>\n"
                        + "\n"
                        + "    <bool name=\"resBool\">true</bool>\n"
                        + "\n"
                        + "    <color name=\"resColor\">#ffffff</color>\n"
                        + "\n"
                        + "    <declare-styleable name=\"resDeclareStyleable\">foo</declare-styleable>\n"
                        + "\n"
                        + "    <dimen name=\"resDimen\">42px</dimen>\n"
                        + "\n"
                        + "    <fraction name=\"resFraction\">42%</fraction>\n"
                        + "\n"
                        + "    <item name=\"resId\" type=\"id\">42</item>\n"
                        + "\n"
                        + "    <integer name=\"resInteger\">42</integer>\n"
                        + "\n"
                        + "    <plurals name=\"resPlurals\">s</plurals>\n"
                        + "\n"
                        + "    <string name=\"resString\" translatable=\"false\">00</string>\n"
                        + "\n"
                        + "    <style name=\"resStyle\">foo</style>\n"
                        + "\n"
                        + "</resources>",
                FileUtils.loadFileWithUnixLineSeparators(outputFile));
    }
}
