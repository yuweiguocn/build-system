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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AndroidTestResourcesConnectedTest {
    @Rule
    public GradleTestProject appProject =
            GradleTestProject.builder()
                    .withName("application")
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .create();

    @Before
    public void setUp() throws IOException {
        setUpProject(appProject);
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion rootProject.supportLibMinSdk\n"
                        + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "    androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n");
    }

    private static void setUpProject(GradleTestProject project) throws IOException {
        Path layout =
                project.getTestDir()
                        .toPath()
                        .resolve("src/androidTest/res/layout/test_layout_1.xml");

        String testLayout =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        android:layout_width=\"match_parent\"\n"
                        + "        android:layout_height=\"match_parent\"\n"
                        + "        android:orientation=\"vertical\" >\n"
                        + "    <TextView android:id=\"@+id/test_layout_1_textview\"\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"Hello, I am a TextView\" />\n"
                        + "</LinearLayout>\n";

        Files.createDirectories(layout.getParent());
        Files.write(layout, testLayout.getBytes());

        // This class exists to prevent the resource from being automatically removed,
        // if we start filtering test resources by default.
        Path resourcesTest =
                project.getTestDir()
                        .toPath()
                        .resolve(
                                "src/androidTest/java/com/example/helloworld/HelloWorldResourceTest.java");

        Files.createDirectories(resourcesTest.getParent());
        String sourcesTestContent =
                "package com.example.helloworld;\n"
                        + "                import android.support.test.filters.MediumTest;\n"
                        + "                import android.support.test.rule.ActivityTestRule;\n"
                        + "                import android.support.test.runner.AndroidJUnit4;\n"
                        + "                import android.widget.TextView;\n"
                        + "                import org.junit.Assert;\n"
                        + "                import org.junit.Before;\n"
                        + "                import org.junit.Rule;\n"
                        + "                import org.junit.Test;\n"
                        + "                import org.junit.runner.RunWith;\n"
                        + "\n"
                        + "                @RunWith(AndroidJUnit4.class)\n"
                        + "                public class HelloWorldResourceTest {\n"
                        + "                    @Rule public ActivityTestRule<HelloWorld> rule = new ActivityTestRule<>(HelloWorld.class);\n"
                        + "                    private TextView mainAppTextView;\n"
                        + "                    private Object testLayout;\n"
                        + "\n"
                        + "\n                  @Before"
                        + "                    public void setUp() {\n"
                        + "                        final HelloWorld a = rule.getActivity();\n"
                        + "                        mainAppTextView = (TextView) a.findViewById(\n"
                        + "                                com.example.helloworld.R.id.text);\n"
                        + "                        testLayout = rule.getActivity().getResources()\n"
                        + "                                .getLayout(com.example.helloworld.test.R.layout.test_layout_1);\n"
                        + "                    }\n"
                        + "\n"
                        + "                    @Test\n"
                        + "                    @MediumTest\n"
                        + "                    public void testPreconditions() {\n"
                        + "                        Assert.assertNotNull(\"Should find test test_layout_1.\", testLayout);\n"
                        + "                        Assert.assertNotNull(\"Should find main app text view.\", mainAppTextView);\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                ";
        Files.write(resourcesTest, sourcesTestContent.getBytes());
    }

    @Test
    @Category(DeviceTests.class)
    public void checkTestLayoutCanBeUsedInDeviceTests() throws IOException, InterruptedException {
        appProject.executeConnectedCheck();
    }
}
