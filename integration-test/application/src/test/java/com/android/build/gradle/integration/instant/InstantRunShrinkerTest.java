/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.google.common.truth.Expect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Check that the shrinker does not run when Instant run is used. */
public class InstantRunShrinkerTest {

    private static final AndroidTestModule TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    static {
        TEST_APP.addFile(new TestSourceFile("src/main/java/com/example/helloworld",
                "MyApplication.java",
                "package com.example.helloworld;" +
                "public class MyApplication extends android.app.Application{\n" +
                "    public void onCreate() {\n" +
                "        super.onCreate();" +
                "    }\n" +
                "}\n"));
        TEST_APP.replaceFile(
                new TestSourceFile(
                        "src/main/AndroidManifest.xml",
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "        package=\"com.example.helloworld\"\n"
                                + "        android:versionCode=\"1\"\n"
                                + "        android:versionName=\"1.0\">\n"
                                + "\n"
                                + "    <application android:label=\"@string/app_name\""
                                + "            android:name=\".MyApplication\">\n"
                                + "        <activity android:name=\".HelloWorld\"\n"
                                + "                android:label=\"@string/app_name\">\n"
                                + "            <intent-filter>\n"
                                + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                                + "                    <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                                + "            </intent-filter>\n"
                                + "        </activity>\n"
                                + "    </application>\n"
                                + "</manifest>"));
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(TEST_APP)
                    .create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void addProvidedLibrary() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "android.buildTypes.debug {\n"
                + "    minifyEnabled true\n"
                + "    useProguard false\n"
                + "}\n");


    }

    @Test
    public void checkApplicationIsNotRemoved() throws Exception {
        project.execute("clean");
        GradleBuildResult result =
                project.executor()
                        .withInstantRun(
                                new AndroidVersion(23, null), OptionalCompilationStep.FULL_APK)
                        .run("assembleDebug");

        assertThat(result.getStdout()).contains("R8 is disabled for variant");
    }

}
