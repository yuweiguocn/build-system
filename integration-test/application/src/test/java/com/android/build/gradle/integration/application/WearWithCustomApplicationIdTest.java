/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Throwables;
import java.io.File;
import org.gradle.tooling.BuildException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Debug builds with a wearApp with applicationId that does not match that of the main application
 * should fail.
 */
public class WearWithCustomApplicationIdTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("embedded")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        File mainAppBuildGradle = project.file("main/build.gradle");

        TestFileUtils.searchAndReplace(
                mainAppBuildGradle,
                "flavor1 {",
                "flavor1 {\n" + "applicationId \"com.example.change.application.id.breaks.embed\"");
    }

    @Test
    public void buildShouldFailOnApplicationIdMismatch() throws Exception {
        try {
            project.execute("clean", ":main:assembleFlavor1Release");
            fail("Build should fail: applicationId of wear app does not match the main application");
        } catch (BuildException e) {
            assertThat(Throwables.getRootCause(e).getMessage())
                    .contains("The main and the micro apps do not have the same package name");
        }
    }
}
