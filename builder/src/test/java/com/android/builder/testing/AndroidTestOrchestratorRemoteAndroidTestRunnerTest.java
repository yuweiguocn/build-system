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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.builder.testing.api.DeviceConnector;
import com.android.ddmlib.testrunner.AndroidTestOrchestratorRemoteAndroidTestRunner;
import com.google.common.base.CharMatcher;
import com.google.common.truth.Truth;
import org.junit.Test;
import org.mockito.Mockito;

public class AndroidTestOrchestratorRemoteAndroidTestRunnerTest {
    @Test
    public void amInstrumentCommand_old() throws Exception {
        checkAdbCommand(
                "android.support.test.runner.AndroidJUnitRunner",
                false,
                "CLASSPATH=$(pm path android.support.test.services) "
                        + "app_process / android.support.test.services.shellexecutor.ShellMain "
                        + "am instrument -r -w "
                        + "-e targetInstrumentation com.example.app/android.support.test.runner.AndroidJUnitRunner "
                        + "-e foo bar "
                        + "android.support.test.orchestrator/android.support.test.orchestrator.AndroidTestOrchestrator");
    }

    @Test
    public void amInstrumentCommand_new() throws Exception {
        checkAdbCommand(
                "androidx.test.runner.AndroidJUnitRunner",
                true,
                "CLASSPATH=$(pm path androidx.test.services) "
                        + "app_process / androidx.test.services.shellexecutor.ShellMain "
                        + "am instrument -r -w "
                        + "-e targetInstrumentation com.example.app/androidx.test.runner.AndroidJUnitRunner "
                        + "-e foo bar "
                        + "androidx.test.orchestrator/androidx.test.orchestrator.AndroidTestOrchestrator");
    }

    private static void checkAdbCommand(
            @NonNull String instrumentationRunner,
            @NonNull boolean useAndroidx,
            @NonNull String expected) {
        TestData testData = new StubTestData("com.example.app", instrumentationRunner);
        DeviceConnector deviceConnector = Mockito.mock(DeviceConnector.class);

        AndroidTestOrchestratorRemoteAndroidTestRunner odoRunner =
                new AndroidTestOrchestratorRemoteAndroidTestRunner(
                        testData.getApplicationId(),
                        testData.getInstrumentationRunner(),
                        deviceConnector,
                        useAndroidx);

        odoRunner.addInstrumentationArg("foo", "bar");

        String normalizedCommand =
                CharMatcher.whitespace().collapseFrom(odoRunner.getAmInstrumentCommand(), ' ');

        Truth.assertThat(normalizedCommand).isEqualTo(expected);
    }
}
