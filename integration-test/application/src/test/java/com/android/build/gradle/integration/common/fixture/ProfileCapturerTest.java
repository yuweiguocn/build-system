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

package com.android.build.gradle.integration.common.fixture;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ProfileCapturerTest {
    HelloWorldApp app = HelloWorldApp.forPlugin("com.android.application");
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(app).enableProfileOutput().create();
    ProfileCapturer capturer;

    @Before
    public void setUp() throws Exception {
        capturer = new ProfileCapturer(project);
    }

    @Test
    public void noNewProfilesAtFirst() throws Throwable {
        assertThat(capturer.findNewProfiles()).isEmpty();
    }

    @Test
    public void capturesSingleProfile() throws Throwable {
        project.execute("tasks");
        assertThat(capturer.findNewProfiles()).hasSize(1);
    }

    @Test
    public void capturesMultipleProfiles() throws Throwable {
        project.execute("tasks");
        project.execute("tasks");
        assertThat(capturer.findNewProfiles()).hasSize(2);
    }

    @Test
    public void resetsCapturedProfilesWhenFetched() throws Throwable {
        project.execute("tasks");
        assertThat(capturer.findNewProfiles()).hasSize(1);
        assertThat(capturer.findNewProfiles()).hasSize(0);
        project.execute("tasks");
        assertThat(capturer.findNewProfiles()).hasSize(1);
        assertThat(capturer.findNewProfiles()).hasSize(0);
    }

    @Test
    public void captureLambdaSingleWorksAsExpected() throws Throwable {
        Collection<GradleBuildProfile> profiles = capturer.capture(() -> project.execute("tasks"));

        assertThat(profiles).hasSize(1);
        for (GradleBuildProfile profile : profiles) {
            assertThat(profile.getBuildTime()).isGreaterThan(0L);
            assertThat(profile.getSpanCount()).isGreaterThan(0);
        }
    }

    @Test
    public void captureLambdaMultipleWorksAsExpected() throws Throwable {
        Collection<GradleBuildProfile> profiles =
                capturer.capture(
                        () -> {
                            project.execute("tasks");
                            project.execute("tasks");
                        });

        assertThat(profiles).hasSize(2);
        for (GradleBuildProfile profile : profiles) {
            assertThat(profile.getBuildTime()).isGreaterThan(0L);
            assertThat(profile.getSpanCount()).isGreaterThan(0);
        }
    }
}
