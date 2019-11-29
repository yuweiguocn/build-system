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

import static com.android.testutils.truth.MoreTruth.assertThatZip;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.options.StringOption;
import com.android.testutils.apk.Zip;
import com.android.testutils.truth.ZipFileSubject;
import com.android.utils.FileUtils;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests a multi-feature project with a custom APK Location. */
public class FeatureApkLocationTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("projectWithFeatures")
                    .withoutNdk()
                    .create();

    @ClassRule public static TemporaryFolder sTempFolder = new TemporaryFolder();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        // Call the task to publish the base feature application ID.
        sProject.executor()
                .with(StringOption.IDE_APK_LOCATION, sTempFolder.getRoot().getAbsolutePath())
                .run("clean", ":bundle:assembleRelease");

        // Check that the instantApp bundle gets built properly.
        try (ZipFileSubject instantAppBundle =
                assertThatZip(
                        new Zip(
                                FileUtils.join(
                                        sTempFolder.getRoot(), "release", "bundle-release.zip")))) {
            instantAppBundle.exists();
            instantAppBundle.contains("baseFeature-release-unsigned.apk");
            instantAppBundle.contains("feature-release.apk");
        }
    }
}
