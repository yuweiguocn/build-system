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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for the jarjar integration.
 */
public class JarJarTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("jarjarIntegration").create();

    @AfterClass
    public static void tearDown() {
        project = null;
    }

    @Test
    public void checkRepackagedGsonLibraryFormonodex() throws Exception {
        project.executeAndReturnModel("clean", "assembleDebug");
        verifyApk();
    }

    @Test
    public void checkRepackagedForNativeMultidex() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.defaultConfig {\n"
                        + "    minSdkVersion 21\n"
                        + "    targetSdkVersion 21\n"
                        + "    multiDexEnabled true\n"
                        + "}\n");

        project.executeAndReturnModel("clean", "assembleDebug");
        verifyApk();
    }

    @Test
    public void checkRepackagedForLegacyMultidex() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "\n"
                        + "android.defaultConfig {\n"
                        + "    minSdkVersion 19\n"
                        + "    multiDexEnabled true\n"
                        + "}\n");

        project.executor().run("clean", "assembleDebug");
        project.model().fetchAndroidProjects().getOnlyModel();
        verifyApk();
    }

    private void verifyApk() throws Exception {
        // make sure the Gson library has been renamed and the original one is not present.
        Apk outputFile = project.getApk("debug");
        TruthHelper.assertThatApk(outputFile)
                .containsClass("Lcom/google/repacked/gson/Gson;");
        TruthHelper.assertThatApk(outputFile)
                .doesNotContainClass("Lcom/google/gson/Gson;");
    }
}
