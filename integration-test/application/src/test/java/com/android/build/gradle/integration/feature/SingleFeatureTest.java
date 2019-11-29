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
import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.testutils.apk.Apk;
import java.io.File;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test a project with a single feature. */
public class SingleFeatureTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder().fromTestProject("singleFeature").withoutNdk().create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        // Build all the things.
        sProject.executor().run("assemble");

        // Check the feature module output contents.
        GradleTestProject featureProject = sProject.getSubproject(":feature");
        checkManifestContents(
                featureProject.getIntermediateFile(
                        "merged_manifests",
                        "debugFeature",
                        "AndroidManifest.xml"));
        checkApkContents(featureProject.getFeatureApk(GradleTestProject.ApkType.DEBUG));

        // Check the app module output contents.
        GradleTestProject appProject = sProject.getSubproject(":app");
        checkManifestContents(
                appProject.getIntermediateFile(
                        "merged_manifests",
                        "debug",
                        "AndroidManifest.xml"));
        checkApkContents(appProject.getApk(GradleTestProject.ApkType.DEBUG));
    }

    private static void checkManifestContents(File manifestFile) {
        assertThat(manifestFile).exists();
        assertThat(manifestFile)
                .containsAllOf(
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "package=\"com.android.tests.singlefeature\"",
                        "<activity",
                        "android:name=\"com.android.tests.singlefeature.feature.FeatureActivity\"",
                        "android:label=\"@string/app_name\"");
        assertThat(manifestFile).doesNotContain("split=");
    }

    private static void checkApkContents(Apk apk) throws Exception {
        try (ApkSubject apkSubject = assertThatApk(apk)) {
            apkSubject.exists();
            apkSubject.containsClass("Lcom/android/tests/singlefeature/R;");
            apkSubject.containsClass("Lcom/android/tests/singlefeature/feature/FeatureActivity;");
            apkSubject.contains("AndroidManifest.xml");
            apkSubject.contains("resources.arsc");
        }
    }
}
