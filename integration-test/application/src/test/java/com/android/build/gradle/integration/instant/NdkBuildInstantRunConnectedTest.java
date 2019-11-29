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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.utils.AndroidVersionMatcher.thatUsesArt;
import static com.android.build.gradle.integration.instant.InstantRunTestUtils.PORTS;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.UninstallOnClose;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.packaging.PackagingUtils;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.ir.client.InstantRunClient;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.io.Closeable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NdkBuildInstantRunConnectedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().build())
                    .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
                    .create();

    private final ILogger iLogger = new StdLogger(StdLogger.Level.INFO);

    @Rule public Adb adb = new Adb();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "               path 'src/main/jni/Android.mk'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n");
    }

    @Test
    @Category(DeviceTests.class)
    public void checkItRunsOnDevice() throws Exception {
        IDevice device = adb.getDevice(thatUsesArt());
        try (Closeable ignored = new UninstallOnClose(device, "com.example.hellojni")) {
            AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();
            InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
            project.executor()
                    .withInstantRun(
                            new AndroidVersion(device.getVersion().getApiLevel(), null),
                            OptionalCompilationStep.RESTART_ONLY)
                    .run("assembleDebug");
            InstantRunBuildInfo info = InstantRunTestUtils.loadContext(instantRunModel);
            device.uninstallPackage("com.example.hellojni");
            InstantRunTestUtils.doInstall(device, info);

            // Run app
            InstantRunTestUtils.unlockDevice(device);

            InstantRunTestUtils.runApp(device, "com.example.hellojni/.HelloJni");

            long token = PackagingUtils.computeApplicationHash(model.getBuildFolder());

            // Connect to device
            InstantRunClient client =
                    new InstantRunClient(
                            "com.example.hellojni",
                            iLogger,
                            token,
                            PORTS.get(NdkBuildInstantRunConnectedTest.class.getSimpleName()));

            // Give the app a chance to start
            InstantRunTestUtils.waitForAppStart(client, device);
        }
    }
}
