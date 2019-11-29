/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for pure splits under ndk-build. */
public class NdkBuildJniPureSplitLibTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("ndkJniPureSplitLib")
                    .addFile(HelloWorldJniApp.androidMkC("lib/src/main/jni"))
                    .create();

    @Before
    public void setup() throws Exception {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        assertTrue(
                new File(project.getTestDir(), "lib/src/main/jni")
                        .renameTo(new File(project.getTestDir(), "lib/src/main/cxx")));
        GradleTestProject lib = project.getSubproject("lib");
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "apply plugin: 'com.android.library'\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path file(\"src/main/cxx/Android.mk\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");
        project.execute("clean", ":app:assembleDebug");
    }

    @Test
    public void checkVersionCodeAndSo() throws Exception {
        checkVersionCode();
        checkSo();
    }

    private void checkVersionCode() throws IOException {
        GradleTestProject app = project.getSubproject("app");
        assertThat(app.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "free"))
                .hasVersionCode(123);
        assertThat(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "free")).hasVersionCode(123);
        assertThat(app.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "paid"))
                .hasVersionCode(123);
        assertThat(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "paid")).hasVersionCode(123);
    }

    private void checkSo() throws IOException {
        GradleTestProject app = project.getSubproject("app");
        assertThat(app.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "free")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThat(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "paid"))
                .contains("lib/x86/libhello-jni.so");
    }
}
