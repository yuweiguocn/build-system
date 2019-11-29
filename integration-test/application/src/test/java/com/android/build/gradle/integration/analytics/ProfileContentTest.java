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

package com.android.build.gradle.integration.analytics;

import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_MIN_SDK;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ProfileCapturer;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.util.HashSet;
import org.junit.Rule;
import org.junit.Test;

/**
 * This test exists to make sure that the profiles we get back from the Android Gradle Plugin meet
 * the expectations we have for them in our benchmarking infrastructure.
 */
public class ProfileContentTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .enableProfileOutput()
                    .create();

    @Test
    public void testProfileProtoContentMakesSense() throws Exception {
        ProfileCapturer capturer = new ProfileCapturer(project);

        GradleBuildProfile getModel =
                Iterables.getOnlyElement(
                        capturer.capture(
                                () -> {
                                    project.model().fetchAndroidProjects();
                                }));

        GradleBuildProfile cleanBuild =
                Iterables.getOnlyElement(
                        capturer.capture(
                                () -> {
                                    project.execute("assembleDebug");
                                }));

        GradleBuildProfile noOpBuild =
                Iterables.getOnlyElement(
                        capturer.capture(
                                () -> {
                                    project.execute("assembleDebug");
                                }));

        for (GradleBuildProfile profile : ImmutableList.of(getModel, cleanBuild, noOpBuild)) {
            assertThat(profile.getSpanCount()).isGreaterThan(0);

            assertThat(profile.getProjectCount()).isGreaterThan(0);
            GradleBuildProject gbp = profile.getProject(0);
            assertThat(gbp.getCompileSdk()).isEqualTo(GradleTestProject.getCompileSdkHash());
            assertThat(gbp.getKotlinPluginVersion()).isEqualTo(project.getKotlinVersion());
            assertThat(gbp.getPluginList())
                    .contains(
                            GradleBuildProject.GradlePlugin
                                    .ORG_JETBRAINS_KOTLIN_GRADLE_PLUGIN_KOTLINANDROIDPLUGINWRAPPER);
            assertThat(gbp.getPluginList())
                    .doesNotContain(GradleBuildProject.GradlePlugin.UNKNOWN_GRADLE_PLUGIN);
            assertThat(gbp.getVariantCount()).isGreaterThan(0);
            GradleBuildVariant gbv = gbp.getVariant(0);
            assertThat(gbv.getMinSdkVersion().getApiLevel()).isEqualTo(SUPPORT_LIB_MIN_SDK);
            assertThat(gbv.hasTargetSdkVersion()).named("has target sdk version").isFalse();
            assertThat(gbv.hasMaxSdkVersion()).named("has max sdk version").isFalse();
            assertThat(new HashSet<>(profile.getRawProjectIdList()))
                    .containsExactly("com.example.helloworld");
        }
    }
}
