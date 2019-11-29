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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.instant.InstantRunTestUtils.PORTS;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ddmlib.IDevice;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DaggerConnectedTest {
    private static final String ORIGINAL_MESSAGE = "from module";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{"daggerOne"}, {"daggerTwo"}});
    }

    @Rule public Logcat logcat = Logcat.create();

    @Rule public final Adb adb = new Adb();

    @Rule public GradleTestProject project;

    private final String testProject;

    private File mAppModule;

    public DaggerConnectedTest(String testProject) {
        this.testProject = testProject;

        project = GradleTestProject.builder().fromTestProject(testProject).create();

        mAppModule = project.file("src/main/java/com/android/tests/AppModule.java");
    }

    @Test
    @Category(DeviceTests.class)
    @Ignore("b/68305039")
    public void hotSwap_art() throws Exception {
        doTestHotSwap(adb.getDevice(AndroidVersionMatcher.thatUsesArt()));
    }

    private void doTestHotSwap(IDevice iDevice) throws Exception {
        HotSwapTester tester =
                new HotSwapTester(
                        project,
                        "com.android.tests",
                        "MainActivity",
                        this.testProject,
                        iDevice,
                        logcat,
                        PORTS.get(DaggerTest.class.getSimpleName()));

        tester.run(
                () -> assertThat(logcat).containsMessageWithText(ORIGINAL_MESSAGE),
                new HotSwapTester.LogcatChange(1, ORIGINAL_MESSAGE) {
                    @Override
                    public void makeChange() throws Exception {
                        TestFileUtils.searchAndReplace(
                                mAppModule, "from module", CHANGE_PREFIX + 1);
                    }
                },
                new HotSwapTester.LogcatChange(2, ORIGINAL_MESSAGE) {
                    @Override
                    public void makeChange() throws Exception {
                        TestFileUtils.searchAndReplace(
                                mAppModule, CHANGE_PREFIX + 1, CHANGE_PREFIX + 2);
                    }
                });
    }
}
