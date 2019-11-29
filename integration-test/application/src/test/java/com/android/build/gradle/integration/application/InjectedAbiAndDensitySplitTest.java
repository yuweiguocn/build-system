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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test injected ABI and density reduces the number of splits being built.
 */
public class InjectedAbiAndDensitySplitTest {

    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        FileUtils.createFile(sProject.file("src/main/jniLibs/x86/libprebuilt.so"), "");
        FileUtils.createFile(sProject.file("src/main/jniLibs/armeabi-v7a/libprebuilt.so"), "");

        Files.append(
                "android {\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include 'x86', 'armeabi-v7a'\n"
                        + "            universalApk false\n"
                        + "        }\n"
                        + "        density {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include \"ldpi\", \"hdpi\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                sProject.getBuildFile(),
                Charsets.UTF_8);
    }

    @Test
    public void checkAbi() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                .run("clean", "assembleDebug");

        assertThat(sProject.getApk("armeabi-v7a", ApkType.DEBUG)).exists();
        assertThat(sProject.getApk("armeabi-v7a", ApkType.DEBUG))
                .contains("lib/armeabi-v7a/libprebuilt.so");
        assertThat(sProject.getApk("ldpiArmeabi-v7a", ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("hdpiArmeabi-v7a", ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("x86", ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("ldpiX86", ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("hdpiX86", ApkType.DEBUG)).doesNotExist();
    }

    @Test
    public void checkAbiAndDensity() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                .with(StringOption.IDE_BUILD_TARGET_DENSITY, "ldpi")
                .run("clean", "assembleDebug");

        Apk apk;
        // Either ldpi or universal density can match the injected density.
        if (sProject.getApk("armeabi-v7a", ApkType.DEBUG).exists()) {
            apk = sProject.getApk("armeabi-v7a", ApkType.DEBUG);
            assertThat(sProject.getApk("ldpiArmeabi-v7a", ApkType.DEBUG)).doesNotExist();
        } else {
            apk = sProject.getApk("ldpiArmeabi-v7a", ApkType.DEBUG);
            assertThat(sProject.getApk("armeabi-v7a", ApkType.DEBUG)).doesNotExist();
        }
        assertThat(apk).exists();
        assertThat(apk).contains("lib/armeabi-v7a/libprebuilt.so");
        assertThat(sProject.getApk("hdpiArmeabi-v7a", ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("x86", ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("ldpiX86", ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("hdpiX86", ApkType.DEBUG)).doesNotExist();
    }

    /** All splits are built if only density is present. */
    @Test
    public void checkOnlyDensity() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_DENSITY, "ldpi")
                .run("clean", "assembleDebug");

        assertThat(sProject.getApk("armeabi-v7a", ApkType.DEBUG).exists());
        assertThat(sProject.getApk("ldpiArmeabi-v7a", ApkType.DEBUG)).exists();
        assertThat(sProject.getApk("hdpiArmeabi-v7a", ApkType.DEBUG)).exists();
        assertThat(sProject.getApk("x86", ApkType.DEBUG)).exists();
        assertThat(sProject.getApk("ldpiX86", ApkType.DEBUG)).exists();
        assertThat(sProject.getApk("hdpiX86", ApkType.DEBUG)).exists();
    }

    @Test
    public void checkError() throws Exception {
        AndroidProject model =
                sProject.model()
                        .with(StringOption.IDE_BUILD_TARGET_ABI, "mips")
                        .ignoreSyncIssues()
                        .fetchAndroidProjects()
                        .getOnlyModel();

        assertThat(model).hasIssue(SyncIssue.SEVERITY_WARNING, SyncIssue.TYPE_GENERIC);
    }
}
