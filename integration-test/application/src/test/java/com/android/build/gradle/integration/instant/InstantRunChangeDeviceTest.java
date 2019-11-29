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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantBuildOutputUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.tools.ir.client.InstantRunArtifact;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.truth.Expect;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InstantRunChangeDeviceTest {

    private static final Set<BuildTarget> buildTargetsToTest = EnumSet.allOf(BuildTarget.class);

    @Parameterized.Parameters(name = "from {0} to {1}")
    public static Collection<Object[]> scenarios() {
        // Test all change combinations (plus packaging modes).
        // We want (BuildTarget x BuildTarget) (i.e. including id(BuildTarget))
        return Sets.cartesianProduct(
                        ImmutableList.of(
                                buildTargetsToTest,
                                buildTargetsToTest))
                .stream()
                .map(fromTo -> Iterables.toArray(fromTo, Object.class))
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter(0)
    public BuildTarget firstBuild;

    @Parameterized.Parameter(1)
    public BuildTarget secondBuild;

    @Rule
    public GradleTestProject mProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();
    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Test
    public void switchScenario() throws Exception {
        AndroidProject model = mProject.model().fetchAndroidProjects().getOnlyModel();

        AndroidArtifact debug =
                model.getVariants()
                        .stream()
                        .filter(variant -> variant.getName().equals("debug"))
                        .iterator()
                        .next()
                        .getMainArtifact();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        String startBuildId;
        mProject.execute("clean");

        if (firstBuild == BuildTarget.NO_INSTANT_RUN) {
            ProjectBuildOutput projectBuildOutput =
                    mProject.executeAndReturnOutputModel("assembleDebug");
            VariantBuildOutput debugOutput =
                    ProjectBuildOutputUtils.getDebugVariantBuildOutput(projectBuildOutput);
            File apk = VariantBuildOutputUtils.getMainOutputFile(debugOutput).getOutputFile();
            checkNormalApk(apk);
            startBuildId = null;
        } else {
            mProject.executor()
                    .withInstantRun(
                            new AndroidVersion(firstBuild.getApiLevel(), null),
                            OptionalCompilationStep.FULL_APK)
                    .run("assembleDebug");
            InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
            startBuildId = initialContext.getTimeStamp();
            checkSplitApk(initialContext.getArtifacts());

        }

        if (secondBuild == BuildTarget.NO_INSTANT_RUN) {
            ProjectBuildOutput projectBuildOutput =
                    mProject.executeAndReturnOutputModel("assembleDebug");
            VariantBuildOutput debugOutput =
                    ProjectBuildOutputUtils.getDebugVariantBuildOutput(projectBuildOutput);
            File apk = VariantBuildOutputUtils.getMainOutputFile(debugOutput).getOutputFile();
            checkNormalApk(apk);
        } else {
            mProject.executor()
                    .withInstantRun(
                            new AndroidVersion(secondBuild.getApiLevel(), null),
                            OptionalCompilationStep.FULL_APK)
                    .run("assembleDebug");
            InstantRunBuildInfo buildContext = InstantRunTestUtils.loadContext(instantRunModel);
            assertThat(buildContext.getSecretToken()).isNotEqualTo(0);
            assertThat(buildContext.getTimeStamp()).isNotEqualTo(startBuildId);
            checkSplitApk(buildContext.getArtifacts());

        }
    }

    private static void checkSplitApk(@NonNull List<InstantRunArtifact> artifacts)
            throws Exception {
        assertThat(artifacts).hasSize(11);
        InstantRunArtifact main = artifacts.stream()
                .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT_MAIN)
                .findFirst().orElseThrow(() -> new AssertionError("Main artifact not found"));

        try (Apk apk = new Apk(main.file)) {
            assertThat(apk).doesNotContainClass("Lcom/example/helloworld/HelloWorld;");
            assertThat(apk).containsClass("Lcom/android/tools/ir/server/Server;");
        }
    }

    private static void checkNormalApk(@NonNull File apkFile) throws Exception {
        try (Apk apk = new Apk(apkFile)) {
            assertThat(apk)
                    .hasMainClass("Lcom/example/helloworld/HelloWorld;")
                    .that()
                    .hasMethod("onCreate");
            assertThat(apk).doesNotContainMainClass("Lcom/android/tools/ir/server/Server;");
            assertThat(apk).doesNotContainMainClass("Lcom/android/tools/ir/server/AppInfo;");
        }
    }


    private enum BuildTarget {
        NO_INSTANT_RUN(23),
        INSTANT_RUN_MULTI_APK_23(23),
        INSTANT_RUN_MULTI_APK_24(24);

        private final int apiLevel;

        BuildTarget(int apiLevel) {
            this.apiLevel = apiLevel;
        }

        int getApiLevel() {
            return apiLevel;
        }
    }
}
