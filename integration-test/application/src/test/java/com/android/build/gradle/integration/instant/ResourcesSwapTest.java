/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.InstantRun;
import com.android.sdklib.AndroidVersion;
import com.android.tools.ir.client.InstantRunArtifact;
import com.google.common.io.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;

/** Test for changing resources with Instant Run. */
public class ResourcesSwapTest {

    @Rule
    public GradleTestProject mProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void artifactContents() throws Exception {
        File asset = mProject.file("src/main/assets/movie.mp4");
        Files.createParentDirs(asset);
        Files.asCharSink(asset, StandardCharsets.UTF_8).write("this is a movie");

        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        mProject.model().fetchAndroidProjects().getOnlyModel());

        InstantRunTestUtils.doInitialBuild(mProject, new AndroidVersion(21, null));
        InstantRunApk apk = InstantRunTestUtils.getMainApk(instantRunModel);
        assertThat(apk).contains("assets/movie.mp4");
        assertThat(apk).contains("classes.dex");

        assertThat(mProject.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist();

        TestFileUtils.appendToFile(asset, " upgraded");

        mProject.executor().withInstantRun(new AndroidVersion(21, null)).run("assembleDebug");

        InstantRunArtifact artifact = InstantRunTestUtils.getResourcesArtifact(instantRunModel);

        assertThat(artifact.file.getName()).endsWith(".ir.ap_");
        assertThatZip(artifact.file).contains("assets/movie.mp4");
        assertThatZip(artifact.file).doesNotContain("classes.dex");
    }
}
