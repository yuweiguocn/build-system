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

package com.android.build.gradle.integration.testing;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.StringOption;
import com.android.ddmlib.IDevice;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnectedCheckTest {
    @Rule
    public GradleTestProject project;

    @Rule
    public Adb adb = new Adb();

    public ConnectedCheckTest() {
        project = GradleTestProject.builder()
                .fromTestProject("appWithTests")
                .create();
    }

    @Category(DeviceTests.class)
    @Test
    public void connectedCheckOnAllDevices() throws Exception {
        project.execute("assembleDebug", "assembleDebugAndroidTest");
        adb.exclusiveAccess();
        GradleBuildResult result = project.executor().run("connectedCheck");
        assertThat(result.getStdout().contains("Starting 3 tests on"));
    }

    @Category(DeviceTests.class)
    @Test
    public void connectedCheckShardedOn1Device() throws Exception {
        project.execute("assembleDebug", "assembleDebugAndroidTest");
        IDevice device = adb.getDevice(AndroidVersionMatcher.anyAndroidVersion());
        // Provide a single device to check.
        project.executor()
                .with(BooleanOption.ENABLE_TEST_SHARDING, true)
                .with(StringOption.DEVICE_POOL_SERIAL, device.getSerialNumber())
                .executeConnectedCheck();
        GradleBuildResult result = project.getBuildResult();
        String stdout = result.getStdout();
        assertThat(stdout).contains("will shard tests into 1 shards");
        assertThat(stdout).contains("Starting 3 tests on");
        assertThat(stdout).contains("finished 1 of estimated 3 tests");
        assertThat(stdout).contains("finished 2 of estimated 3 tests");
        assertThat(stdout).contains("finished 3 of estimated 3 tests");
    }

    @Category(DeviceTests.class)
    @Test
    public void connectedCheckIn7Shards() throws Exception {
        project.execute("assembleDebug", "assembleDebugAndroidTest");
        adb.exclusiveAccess();
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.ENABLE_TEST_SHARDING, true)
                        .with(IntegerOption.ANDROID_TEST_SHARD_COUNT, 7)
                        .run("connectedCheck");
        assertThat(result.getStdout()).contains("will shard tests into 7 shards");
    }
}
