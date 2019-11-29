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
import static org.junit.Assert.assertNull;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for a project build with a {@link com.android.builder.sdk.PlatformLoader} rather than a
 * {@link com.android.builder.sdk.DefaultSdkLoader}
 */
public class PlatformLoaderTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    private File mPrebuiltSdk;

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(project.getLocalProp(), "android.dir=/android-dir");
        mPrebuiltSdk = project.file("android-dir/prebuilts/sdk");
        FileUtils.mkdirs(mPrebuiltSdk);
    }

    @Test
    public void runsSuccessfully_Default() throws Exception{
        // Platform development not supported on Windows
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);

        // Copy all necessary prebuilts
        File realAndroidHome = project.getAndroidHome();

        FileUtils.copyDirectoryContentToDirectory(
                FileUtils.join(
                        realAndroidHome,
                        SdkConstants.FD_PLATFORMS,
                        GradleTestProject.getCompileSdkHash()),
                FileUtils.join(mPrebuiltSdk, GradleTestProject.DEFAULT_COMPILE_SDK_VERSION));

        FileUtils.copyDirectoryToDirectory(
                FileUtils.join(
                        realAndroidHome, SdkConstants.FD_BUILD_TOOLS,
                        GradleTestProject.DEFAULT_BUILD_TOOL_VERSION, "lib"),
                FileUtils.join(mPrebuiltSdk, "tools"));

        FileUtils.copyDirectoryToDirectory(
                FileUtils.join(
                        realAndroidHome, SdkConstants.FD_BUILD_TOOLS,
                        GradleTestProject.DEFAULT_BUILD_TOOL_VERSION, "renderscript"),
                FileUtils.join(mPrebuiltSdk, "tools"));

        FileUtils.copyFileToDirectory(
                FileUtils.join(
                        realAndroidHome, SdkConstants.FD_BUILD_TOOLS,
                        GradleTestProject.DEFAULT_BUILD_TOOL_VERSION, "dx"),
                FileUtils.join(mPrebuiltSdk, "tools"));

        FileUtils.copyDirectoryContentToDirectory(
                FileUtils.join(
                        realAndroidHome, SdkConstants.FD_BUILD_TOOLS,
                        GradleTestProject.DEFAULT_BUILD_TOOL_VERSION),
                FileUtils.join(mPrebuiltSdk, "tools", getPlatform(), "bin"));

        GradleBuildResult result = project.executor().run("assembleDebug");
        assertNull(result.getException());
    }

    private String getPlatform() {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            return "darwin";
        }
        return "linux";
    }
}
