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

package com.android.build.gradle.integration.ndk;

import static com.android.build.gradle.integration.common.utils.AndroidVersionMatcher.anyAndroidVersion;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.StringOption;
import com.android.ddmlib.IDevice;
import java.io.File;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NoSplitNdkVariantsConnectedTest {
    @Rule public Adb adb = new Adb();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(new HelloWorldJniApp()).create();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "      minSdkVersion rootProject.supportLibMinSdk\n"
                        + "      testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path 'Android.mk'\n"
                        + "        }\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        release\n"
                        + "        debug {\n"
                        + "            jniDebuggable true\n"
                        + "        }\n"
                        + "    }\n"
                        + "    flavorDimensions 'abi'\n"
                        + "    productFlavors {\n"
                        + "        x86 {\n"
                        + "            ndk {\n"
                        + "                abiFilter 'x86'\n"
                        + "            }\n"
                        + "        }\n"
                        + "        arm {\n"
                        + "            ndk {\n"
                        + "                abiFilters 'armeabi-v7a'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "  androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "  androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n"
                        + "\n");
        TestFileUtils.appendToFile(
                new File(project.getBuildFile().getParentFile(), "Android.mk"),
                "LOCAL_PATH := $(call my-dir)\n"
                        + "include $(CLEAR_VARS)\n"
                        + "\n"
                        + "LOCAL_MODULE := hello-jni\n"
                        + "LOCAL_SRC_FILES := src/main/jni/hello-jni.c\n"
                        + "\n"
                        + "include $(BUILD_SHARED_LIBRARY)");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() throws Exception {
        project.executor()
                .run(
                        "assembleX86Debug", "assembleX86DebugAndroidTest",
                        "assembleArmDebug", "assembleArmDebugAndroidTest");
        IDevice testDevice = adb.getDevice(anyAndroidVersion());
        Collection<String> abis = testDevice.getAbis();
        String taskName =
                abis.contains("x86")
                        ? "devicePoolX86DebugAndroidTest"
                        : "devicePoolArmDebugAndroidTest";
        project.executor()
                .with(StringOption.DEVICE_POOL_SERIAL, testDevice.getSerialNumber())
                .run(taskName);
    }
}
