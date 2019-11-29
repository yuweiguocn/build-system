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

package com.android.build.gradle.integration.feature;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class FeatureLibraryDepTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("projectWithFeatures")
                    .withoutNdk()
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(sProject.getSettingsFile(), "include 'libfeat'\n");
        TestFileUtils.appendToFile(
                sProject.getSubproject("feature").getBuildFile(),
                "dependencies {\n" + "    implementation project(':libfeat')\n" + "}\n");

        TestFileUtils.appendToFile(
                sProject.getSubproject("baseFeature").getBuildFile(),
                "dependencies {\n"
                        + "    implementation \"com.android.support:support-v4:${rootProject.supportLibVersion}\"\n"
                        + "}");
    }

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        // Build all the things.
        sProject.executor().run("clean", "assemble");

        // Check the library class was not packaged in the feature APK.
        GradleTestProject featureProject = sProject.getSubproject(":feature");
        try (ApkSubject featureApk =
                assertThatApk(featureProject.getFeatureApk(GradleTestProject.ApkType.DEBUG))) {
            featureApk.exists();
            featureApk.containsClass("Lcom/example/android/multiproject/libfeat/R;");
            featureApk.containsClass("Lcom/example/android/multiproject/libfeat/LibFeatView;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/library/PersonView;");

            // Ensure external dependencies are packaged properly in their respective features.
            featureApk.containsClass("Landroid/support/v7/appcompat/R;");
            featureApk.doesNotContainClass("Landroid/support/v4/R;");
        }

        // Check the external library was packaged in the base APK.
        GradleTestProject baseProject = sProject.getSubproject(":baseFeature");
        try (ApkSubject baseFeatureApk =
                assertThatApk(baseProject.getFeatureApk(GradleTestProject.ApkType.DEBUG))) {
            baseFeatureApk.exists();
            baseFeatureApk.containsClass("Landroid/support/v4/R;");
        }
    }
}
