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

package com.android.build.gradle.integration.testing;

import static org.junit.Assert.assertEquals;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.TestOptions.Execution;
import com.android.builder.model.Variant;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class TestOptionsExecutionTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void returnsAto() throws Exception {
        //noinspection SpellCheckingInspection
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android { testOptions.execution \"android_test_orchestrator\" }");

        check(Execution.ANDROID_TEST_ORCHESTRATOR);
    }

    @Test
    public void returnsAto_androidx() throws Exception {
        //noinspection SpellCheckingInspection
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android { testOptions.execution \"androidx_test_orchestrator\" }");

        check(Execution.ANDROIDX_TEST_ORCHESTRATOR);
    }

    @Test
    public void returnsHostByDefault() throws Exception {
        check(Execution.HOST);
    }

    private void check(Execution androidTestOrchestrator) throws IOException {
        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();
        Variant debugVariant = AndroidProjectUtils.getVariantByName(model, "debug");

        Execution execution =
                VariantUtils.getAndroidTestArtifact(debugVariant).getTestOptions().getExecution();

        assertEquals(execution, androidTestOrchestrator);
    }
}
