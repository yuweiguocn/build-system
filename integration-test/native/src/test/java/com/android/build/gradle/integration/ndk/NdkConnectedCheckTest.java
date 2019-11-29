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

package com.android.build.gradle.integration.ndk;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Test AndroidTest with NDK. */
@Ignore(
        "NDK Compile is being deprecated and external native build "
                + "doesn't support assembleAndroidTest")
public class NdkConnectedCheckTest {

    private static AndroidTestModule app = new HelloWorldJniApp();
    static {
        app.addFile(new TestSourceFile("src/androidTest/jni", "hello-jni-test.c",
                "#include <string.h>\n"
                        + "#include <jni.h>\n"
                        + "\n"
                        + "jstring\n"
                        + "Java_com_example_hellojni_HelloJniTest_expectedString(JNIEnv* env, jobject thiz)\n"
                        + "{\n"
                        + "    return (*env)->NewStringUTF(env, \"hello world!\");\n"
                        + "}\n"));
        app.addFile(new TestSourceFile("src/androidTest/java/com/example/hellojni", "HelloJniTest.java",
                "package com.example.hellojni;\n"
                        + "\n"
                        + "import android.test.ActivityInstrumentationTestCase;\n"
                        + "\n"
                        + "public class HelloJniTest extends ActivityInstrumentationTestCase<HelloJni> {\n"
                        + "\n"
                        + "    public HelloJniTest() {\n"
                        + "        super(\"com.example.hellojni\", HelloJni.class);\n"
                        + "    }\n"
                        + "\n"
                        + "    // Get expected string from JNI.\n"
                        + "    public native String expectedString();\n"
                        + "\n"
                        + "    static {\n"
                        + "        System.loadLibrary(\"hello-jni_test\");\n"
                        + "    }\n"
                        + "\n"
                        + "    public void testJniName() {\n"
                        + "        final HelloJni a = getActivity();\n"
                        + "        // ensure a valid handle to the activity has been returned\n"
                        + "        assertNotNull(a);\n"
                        + "\n"
                        + "        assertTrue(expectedString().equals(a.stringFromJNI()));\n"
                        + "    }\n"
                        + "}\n"));
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(app)
            .addGradleProperties("android.useDeprecatedNdk=true")
            .create();


    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "    buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "    defaultConfig {\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.execute("clean", "assembleAndroidTest");
        Apk apk = project.getTestApk();
        assertThat(apk).contains("lib/x86/libhello-jni_test.so");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
    }
}
