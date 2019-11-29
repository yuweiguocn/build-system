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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.testutils.apk.Apk;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test that a library Android.mk referenced from a base Android.mk builds correctly. This
 * reproduces the conditions of b.android.com/219225.
 */
public class NdkBuildAndroidMkLibraryTest {

    private static final TestSourceFile includedAndroidMkFoo =
            new TestSourceFile(
                    "src/main/jni/foo",
                    "Android.mk",
                    "LOCAL_PATH := $(call my-dir)\n"
                            + "\n"
                            + "include $(CLEAR_VARS)\n"
                            + "\n"
                            + "LOCAL_MODULE := foo\n"
                            + "LOCAL_SRC_FILES := ../hello-jni.c\n"
                            + "LOCAL_STATIC_LIBRARIES := bar\n"
                            + "LOCAL_LDLIBS := -llog\n"
                            + "include $(BUILD_SHARED_LIBRARY)");
    private static final TestSourceFile includedAndroidMkBar =
            new TestSourceFile(
                    "src/main/jni/bar",
                    "Android.mk",
                    "LOCAL_PATH := $(call my-dir)\n"
                            + "\n"
                            + "include $(CLEAR_VARS)\n"
                            + "\n"
                            + "LOCAL_MODULE := bar\n"
                            + "LOCAL_SRC_FILES := ../hello-jni.c\n"
                            + "\n"
                            + "include $(BUILD_STATIC_LIBRARY)");
    private static final TestSourceFile includingAndroidMk =
            new TestSourceFile(
                    "src/main/jni",
                    "Android.mk",
                    "LOCAL_PATH := $(call my-dir)\n"
                            + "\n"
                            + "include $(call all-subdir-makefiles)");
    private static final TestSourceFile applicationMk =
            new TestSourceFile(
                    "src/main/jni",
                    "Application.mk",
                    "APP_MODULES\t:= foo bar\n"
                            + "APP_SHORT_COMMANDS := true\n"
                            + "APP_PLATFORM := android-14\n"
                            + "APP_STL := gnustl_static\n"
                            + "NDK_TOOLCHAIN_VERSION := clang");

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().build())
                    .addFile(includedAndroidMkFoo)
                    .addFile(includedAndroidMkBar)
                    .addFile(includingAndroidMk)
                    .addFile(applicationMk)
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "        apply plugin: 'com.android.application'\n"
                        + "        android {\n"
                        + "            compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "            buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "            defaultConfig {\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        abiFilters.addAll(\"armeabi-v7a\", \"armeabi\", \"x86\")\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "            externalNativeBuild {\n"
                        + "                ndkBuild {\n"
                        + "                    path \"src/main/jni/Android.mk\"\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n");
    }

    @Test
    public void checkApkContent() throws IOException, InterruptedException {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        project.execute("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libfoo.so");
        assertThatApk(apk).contains("lib/armeabi/libfoo.so");
        assertThatApk(apk).contains("lib/x86/libfoo.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libfoo.so");
        assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/armeabi/libfoo.so");
        assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/x86/libfoo.so");
        assertThatNativeLib(lib).isStripped();
    }
}
