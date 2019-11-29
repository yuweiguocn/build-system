/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/**
 * Check that the plugin remains compatible with the builder testing DeviceProvider API.
 *
 * <p>Run deviceCheck even without devices, since we use a fake DeviceProvider that doesn't use a
 * device, but only record the calls made to the DeviceProvider and the DeviceConnector.
 */
public class BuilderTestingApiTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("builderTestingApiUse").create();

    @Test
    public void deviceCheck() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("deviceCheck");
        List<String> lines = result.getStdoutAsLines();

        List<String> expectedActionsDevice1 =
                ImmutableList.of(
                        "INIT CALLED",
                        "CONNECT(DEVICE1) CALLED",
                        "INSTALL(DEVICE1) CALLED", // Install the test...
                        "INSTALL(DEVICE1) CALLED", // ...and the tested APK
                        "EXECSHELL(DEVICE1) CALLED", // Run test,
                        "EXECSHELL(DEVICE1) CALLED", // Collect coverage data file (1)
                        "PULL_FILE(DEVICE1) CALLED", // Collect coverage data file (2)
                        "EXECSHELL(DEVICE1) CALLED", // Collect coverage data file (3)
                        "UNINSTALL(DEVICE1) CALLED", // Uninstall the test...
                        "UNINSTALL(DEVICE1) CALLED", // ...and the tested APK.
                        "DISCONNECTED(DEVICE1) CALLED",
                        "TERMINATE CALLED");
        List<String> expectedActionsDevice2 =
                expectedActionsDevice1
                        .stream()
                        .map(s -> s.replace('1', '2'))
                        .collect(Collectors.toList());

        // Allow for interleaving of device1 and device2.
        assertThat(lines).containsAllIn(expectedActionsDevice1);
        assertThat(lines).containsAllIn(expectedActionsDevice2);
    }
}
