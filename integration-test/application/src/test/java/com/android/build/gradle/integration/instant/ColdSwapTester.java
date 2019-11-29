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
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunBuildInfo;
import java.util.List;

/** Helper class for testing cold-swap scenarios. */
class ColdSwapTester {
    private final GradleTestProject mProject;

    public ColdSwapTester(GradleTestProject project) {
        mProject = project;
    }

    void testDalvik(Steps steps) throws Exception {
        doTest(steps, new AndroidVersion(19, null));
    }

    private void doTest(Steps steps, AndroidVersion androidVersion) throws Exception {
        InstantRun instantRunModel = InstantRunTestUtils.doInitialBuild(mProject, androidVersion);

        steps.checkApks(InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel));

        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        String startBuildId = initialContext.getTimeStamp();

        steps.makeChange();

        mProject.executor().withInstantRun(androidVersion).run("assembleDebug");

        InstantRunBuildContext buildContext =
                InstantRunTestUtils.loadBuildContext(androidVersion, instantRunModel);

        InstantRunBuildContext.Build lastBuild = buildContext.getLastBuild();
        assertNotNull(lastBuild);
        assertThat(lastBuild.getBuildId()).isNotEqualTo(startBuildId);

        steps.checkVerifierStatus(lastBuild.getVerifierStatus());
        steps.checkBuildMode(lastBuild.getBuildMode());
        steps.checkArtifacts(lastBuild.getArtifacts());
    }

    void testMultiApk(Steps steps) throws Exception {
        doTest(steps, new AndroidVersion(24, null));
    }

    interface Steps {
        void checkApks(@NonNull SplitApks apks) throws Exception;

        void makeChange() throws Exception;

        void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception;

        void checkBuildMode(@NonNull InstantRunBuildMode buildMode) throws Exception;

        void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts)
                throws Exception;
    }
}
