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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunArtifact;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ButterKnifeTest {
    private static final String ACTIVITY_DESC = "Lcom/example/bk/Activ;";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("butterknife").create();

    private File mActiv;

    @Before
    public void setUp() throws Exception {
        mActiv = project.file("src/main/java/com/example/bk/Activ.java");
    }

    @Test
    public void normalBuild() throws Exception {
        project.execute("assembleDebug");
    }

    @Test
    public void coldSwapBuild() throws Exception {
        new ColdSwapTester(project)
                .testMultiApk(
                        new ColdSwapTester.Steps() {
                            @Override
                            public void checkApks(@NonNull SplitApks apks) throws Exception {
                                assertThat(apks).hasClass(ACTIVITY_DESC);
                            }

                            @Override
                            public void makeChange() throws Exception {
                                TestFileUtils.searchAndReplace(
                                        mActiv.getAbsoluteFile(),
                                        "text.getText().toString()",
                                        "getMessage()");
                                TestFileUtils.addMethod(
                                        mActiv,
                                        "public String getMessage() { return text.getText().toString(); }");
                            }

                            @Override
                            public void checkVerifierStatus(
                                    @NonNull InstantRunVerifierStatus status) {
                                assertThat(status).isEqualTo(InstantRunVerifierStatus.METHOD_ADDED);
                            }

                            @Override
                            public void checkBuildMode(@NonNull InstantRunBuildMode buildMode) {
                                // for multi dex cold build mode is triggered
                                assertEquals(InstantRunBuildMode.COLD, buildMode);
                            }

                            @Override
                            public void checkArtifacts(
                                    @NonNull List<InstantRunBuildContext.Artifact> artifacts)
                                    throws Exception {
                                InstantRunBuildContext.Artifact artifact =
                                        Iterables.getOnlyElement(artifacts);
                                assertThatApk(new Apk(artifact.getLocation()))
                                        .hasClass(ACTIVITY_DESC)
                                        .that()
                                        .hasMethod("getMessage");
                            }
                        });
    }

    @Test
    public void hotSwap() throws Exception {
        AndroidVersion androidVersion = new AndroidVersion(23, null);

        InstantRun instantRunModel = InstantRunTestUtils.doInitialBuild(project, androidVersion);

        TestFileUtils.searchAndReplace(mActiv, "text.getText().toString()", "\"CHANGE\"");

        project.executor().withInstantRun(androidVersion).run("assembleDebug");

        InstantRunArtifact artifact = InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file).containsClass("Lcom/example/bk/Activ$override;");
    }
}
