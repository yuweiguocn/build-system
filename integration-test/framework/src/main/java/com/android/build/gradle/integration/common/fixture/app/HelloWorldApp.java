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



package com.android.build.gradle.integration.common.fixture.app;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;

/**
 * Simple test application that prints "hello world!".
 *
 * <p>Using this in a test application as a rule is usually done as:
 *
 * <pre>
 * {@literal @}Rule
 * public GradleTestProject project = GradleTestProject.builder()
 *     .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
 *     .create();
 * </pre>
 */
public class HelloWorldApp extends AbstractAndroidTestModule implements AndroidTestModule {

    public static final String APP_ID = "com.example.helloworld";

    protected TestSourceFile getSource() {
        return new TestSourceFile(
                "src/main/java/com/example/helloworld",
                "HelloWorld.java",
                "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.os.Bundle;\n"
                        + "\n"
                        + "public class HelloWorld extends Activity {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    @Override\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        setContentView(R.layout.main);\n"
                        + "        // onCreate\n"
                        + "    }\n"
                        + "}\n");
    }

    protected TestSourceFile getResValuesSource() {
        return new TestSourceFile(
                "src/main/res/values",
                "strings.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <string name=\"app_name\">HelloWorld</string>\n"
                        + "</resources>\n");
    }

    protected TestSourceFile getResLayoutSource() {
        return new TestSourceFile(
                "src/main/res/layout",
                "main.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:orientation=\"vertical\"\n"
                        + "    android:layout_width=\"fill_parent\"\n"
                        + "    android:layout_height=\"fill_parent\"\n"
                        + "    >\n"
                        + "<TextView\n"
                        + "    android:layout_width=\"fill_parent\"\n"
                        + "    android:layout_height=\"wrap_content\"\n"
                        + "    android:text=\"hello world!\"\n"
                        + "    android:id=\"@+id/text\"\n"
                        + "    />\n"
                        + "</LinearLayout>\n");
    }

    protected TestSourceFile getManifest() {
        return new TestSourceFile(
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
                        + "</manifest>\n");
    }

    protected TestSourceFile getAndroidTestSource() {
        return new TestSourceFile(
                "src/androidTest/java/com/example/helloworld",
                "HelloWorldTest.java",
                "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.support.test.filters.MediumTest;\n"
                        + "import android.support.test.rule.ActivityTestRule;\n"
                        + "import android.support.test.runner.AndroidJUnit4;\n"
                        + "import android.widget.TextView;\n"
                        + "import org.junit.Assert;\n"
                        + "import org.junit.Before;\n"
                        + "import org.junit.Rule;\n"
                        + "import org.junit.Test;\n"
                        + "import org.junit.runner.RunWith;\n"
                        + "\n"
                        + "@RunWith(AndroidJUnit4.class)\n"
                        + "public class HelloWorldTest {\n"
                        + "    @Rule public ActivityTestRule<HelloWorld> rule = new ActivityTestRule<>(HelloWorld.class);\n"
                        + "    private TextView mTextView;\n"
                        + "\n"
                        + "    @Before\n"
                        + "    public void setUp() throws Exception {\n"
                        + "        final HelloWorld a = rule.getActivity();\n"
                        + "        // ensure a valid handle to the activity has been returned\n"
                        + "        Assert.assertNotNull(a);\n"
                        + "        mTextView = (TextView) a.findViewById(R.id.text);\n"
                        + "\n"
                        + "    }\n"
                        + "\n"
                        + "    @Test\n"
                        + "    @MediumTest\n"
                        + "    public void testPreconditions() {\n"
                        + "        Assert.assertNotNull(mTextView);\n"
                        + "    }\n"
                        + "}\n");
    }

    protected HelloWorldApp() {
        addFiles(
                getSource(),
                getResValuesSource(),
                getResLayoutSource(),
                getManifest(),
                getAndroidTestSource());
    }

    protected HelloWorldApp(String plugin) {
        this(plugin, TestVersions.SUPPORT_LIB_MIN_SDK);
    }

    protected HelloWorldApp(String plugin, int minSdkVersion) {
        this();

        TestSourceFile buildFile =
                new TestSourceFile(
                        "build.gradle",
                        ""
                                + "apply plugin: '"
                                + plugin
                                + "'\n"
                                + "\n"
                                + "android {\n"
                                + "    defaultConfig.minSdkVersion "
                                + minSdkVersion
                                + "\n"
                                + "    compileSdkVersion "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "    lintOptions.checkReleaseBuilds = false\n"
                                + "    defaultConfig {\n"
                                + "        minSdkVersion rootProject.supportLibMinSdk\n"
                                + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                                + "    }\n"
                                + "}\n"
                                + "dependencies {\n"
                                + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                                + "    androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                                + "}\n");

        addFile(buildFile);
    }

    public static HelloWorldApp noBuildFile() {
        return new HelloWorldApp();
    }

    public static HelloWorldApp forPlugin(String plugin) {
        return new HelloWorldApp(plugin);
    }

    public static HelloWorldApp forPluginWithMinSdkVersion(String plugin, int minSdkVersion) {
        return new HelloWorldApp(plugin, minSdkVersion);
    }
}
