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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.options.StringOption;
import com.android.testutils.apk.Apk;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Check that the built apk contains the correct resources.
 *
 * As specified by the build property injected by studio.
 */
public class InjectedDensityTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new EmptyAndroidTestApp("com.example.app.densities"))
                    .create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void setup() throws Exception {
        String buildScript =
                GradleTestProject.getGradleBuildscript()
                        + "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15"
                        + "    }\n"
                        + "    dependencies {\n"
                        + "        compile 'com.android.support:appcompat-v7:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    }"
                        + "}";

        Files.asCharSink(project.getBuildFile(), Charsets.UTF_8).write(buildScript);
    }

    @Test
    public void buildNormallyThenFiltered() throws Exception {
        project.execute("clean");
        checkFilteredBuild();
        checkFullBuild();
    }

    private void checkFullBuild() throws Exception {
        project.execute("assembleDebug");
        Apk debug = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(debug)
                .containsResource("drawable-xxxhdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug).containsResource("drawable-xxhdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug).containsResource("drawable-xhdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug).containsResource("drawable-hdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug).containsResource("drawable-mdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
    }

    private void checkFilteredBuild() throws Exception {
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_DENSITY, "xxhdpi")
                .run("assembleDebug");
        Apk debug = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(debug)
                .doesNotContainResource("drawable-xxxhdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug).containsResource("drawable-xxhdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug)
                .doesNotContainResource("drawable-xhdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug)
                .doesNotContainResource("drawable-hdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
        assertThat(debug)
                .doesNotContainResource("drawable-mdpi-v4/abc_ic_menu_copy_mtrl_am_alpha.png");
    }
}
