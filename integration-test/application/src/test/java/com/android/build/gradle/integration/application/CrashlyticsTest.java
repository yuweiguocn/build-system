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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test basic project with crashlytics. */
public class CrashlyticsTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(
                            HelloWorldApp.forPluginWithMinSdkVersion("com.android.application", 16))
                    .create();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "android {\n",
                "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath 'io.fabric.tools:gradle:1.25.4'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "apply plugin: 'io.fabric'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.crashlytics.sdk.android:crashlytics:2.6.7@aar'\n"
                        + "}\n"
                        + ""
                        + "android {\n"
                        + "    buildTypes.debug {\n"
                        + "        // Enable crashlytics for test variants\n"
                        + "        ext.enableCrashlytics = true\n"
                        + "    }\n"
                        + "");

        TestFileUtils.searchAndReplace(
                project.file("src/main/AndroidManifest.xml"),
                "</application>",
                "    <meta-data\n"
                        + "        android:name=\"io.fabric.ApiKey\"\n"
                        + "        android:value=\"testkey\"\n"
                        + "    />\n"
                        + "</application>");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void assembleDebug() throws IOException, InterruptedException {
        project.executor()
                .withArgument("-Duser.home=" + getCustomUserHomeForCrashlytics(project))
                .run("assembleDebug");
    }

    /**
     * Returns a custom user home directory inside the project directory, creating it first before
     * returning.
     *
     * <p>This is needed because the Crashlytics plugin creates stuff in the user home directory,
     * and we want the tests to be isolated.
     */
    @NonNull
    public static File getCustomUserHomeForCrashlytics(@NonNull GradleTestProject project) {
        File userHomeDir = new File(project.getTestDir(), "user-home");
        FileUtils.mkdirs(userHomeDir);

        // On Mac, the Crashlytics plugin actually uses user-home/Library/Caches, so we need to
        // create that directory as well
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_DARWIN) {
            FileUtils.mkdirs(FileUtils.join(userHomeDir, "Library", "Caches"));
        }

        return userHomeDir;
    }
}
