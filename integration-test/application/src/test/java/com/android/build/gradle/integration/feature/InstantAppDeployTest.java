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

package com.android.build.gradle.integration.feature;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.options.StringOption;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Connected tests for instant app deploy tasks. */
public class InstantAppDeployTest {
    @Rule public Adb adb = new Adb();

    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("instantAppSimpleProject")
                    .withoutNdk()
                    .create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    @Category(DeviceTests.class)
    @Ignore("http://b/64600037")
    public void provision() throws Exception {
        sProject.execute("provisionInstantApp");
    }

    @Test
    @Category(DeviceTests.class)
    @Ignore("http://b/64600037")
    public void sideLoadPostO() throws Exception {
        IDevice device = adb.getDevice(AndroidVersion.VersionCodes.O);
        sProject.executor()
                .with(StringOption.DEVICE_POOL_SERIAL, device.getSerialNumber())
                .run("provisionInstantApp");
        sProject.executor()
                .with(StringOption.DEVICE_POOL_SERIAL, device.getSerialNumber())
                .run("sideLoadDebugInstantApp");
    }

    @Test
    @Category(DeviceTests.class)
    @Ignore("http://b/64600037")
    public void sideLoadPreO() throws Exception {
        IDevice device = adb.getDevice(AndroidVersion.VersionCodes.N);
        sProject.executor()
                .with(StringOption.DEVICE_POOL_SERIAL, device.getSerialNumber())
                .run("provisionInstantApp");
        sProject.executor()
                .with(StringOption.DEVICE_POOL_SERIAL, device.getSerialNumber())
                .run("sideLoadDebugInstantApp");
    }
}
