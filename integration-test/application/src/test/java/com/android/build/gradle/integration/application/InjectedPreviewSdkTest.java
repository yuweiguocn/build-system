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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.IntegerOption;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Check that apps can be built with injected preview SDK. */
@Ignore
public class InjectedPreviewSdkTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Test
    public void buildWithO() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    compileSdkVersion 'android-O'\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 'O'\n"
                        + "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n"
                        + "        targetSdkVersion 'O'\n"
                        + "        versionCode 1\n"
                        + "        versionName '1.0'\n"
                        + "        testInstrumentationRunner \"android.support.test.runner.AndroidJUnitRunner\"\n"
                        + "    }\n\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    androidTestCompile('com.android.support.test.espresso:espresso-core:3.0.2', {\n"
                        + "        exclude group: 'com.android.support', module: 'support-annotations'\n"
                        + "    })\n"
                        + "}\n"
                        + "\n");

        project.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 25)
                .run("assembleDebugAndroidTest");
    }
}
