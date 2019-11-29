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

package com.android.build.gradle.integration.gradlecompat;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.google.common.base.Throwables;
import org.junit.Rule;
import org.junit.Test;

/** Tests whether the Gradle version check takes effect. */
public class GradleVersionCheckTest {

    /** An old version of Gradle to use in this test. */
    private static final String OLD_GRADLE_VERSION = "4.5";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .setTargetGradleVersion(OLD_GRADLE_VERSION)
                    .create();

    @Test
    public void testGradleVersionCheck() {


        try {
            project.executor().run("help");
        } catch (Exception e) {
            assertThat(Throwables.getRootCause(e).getMessage())
                    .contains(
                            String.format(
                                    "Minimum supported Gradle version is %s."
                                            + " Current version is %s.",
                                    SdkConstants.GRADLE_MINIMUM_VERSION, OLD_GRADLE_VERSION));
        }
    }
}
