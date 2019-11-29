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

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.tools.ir.client.InstantRunArtifact;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test instant run with split.
 */
public class InstantRunFullSplitApkTest {

    @Rule
    public GradleTestProject mProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    private InstantRun instantRunModel;

    @Before
    public void getModel() throws Exception {
        TestFileUtils.appendToFile(
                mProject.getBuildFile(),
                "android {\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include 'x86', 'armeabi-v7a'\n"
                        + "            universalApk false\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "ext.abiCodes = ['x86':1, 'armeabi-v7a':2]\n"
                        + "android.applicationVariants.all { variant ->\n"
                        + " variant.outputs.all { output ->\n"
                        + "    def baseAbiVersionCode =\n"
                        + "        project.ext.abiCodes.get(\n"
                        + "            output.getFilter(com.android.build.OutputFile.ABI), 0)\n"
                        + "         output.versionCodeOverride=\n"
                        + "             baseAbiVersionCode * 1000 + variant.versionCode\n"
                        + "  }\n"
                        + "}");


        mProject.execute("clean");
        AndroidProject model = mProject.model().fetchAndroidProjects().getOnlyModel();
        instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
    }

    @Test
    public void testSplit() throws Exception {
        mProject.executor()
                .withInstantRun(new AndroidVersion(24, null), OptionalCompilationStep.FULL_APK)
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                .run("assembleDebug");
        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
        List<InstantRunArtifact> artifacts = initialContext.getArtifacts();

        InstantRunArtifact main = artifacts.stream()
                .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT_MAIN)
                .findFirst().orElseThrow(() -> new AssertionError("Main artifact not found"));

        assertThat(main.file).hasName("project-armeabi-v7a-debug.apk");
        assertThat(
                        mProject.file(
                                "build/intermediates/instant_run_split_apk_resources/debug/"
                                        + "instantRunSplitApkResourcesDebug/out/"
                                        + "slice_0/AndroidManifest.xml"))
                .contains("android:versionCode=\"2001\"");

        // Run the second time as regression test for bug 62100695
        mProject.executor()
                .withInstantRun(new AndroidVersion(24, null), OptionalCompilationStep.FULL_APK)
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                .run("assembleDebug");

        // Run with several ABIs as regression test for bug 62909130
        mProject.executor()
                .withInstantRun(new AndroidVersion(24, null), OptionalCompilationStep.FULL_APK)
                .with(StringOption.IDE_BUILD_TARGET_ABI, "arm64-v8a,armeabi-v7a,armeabi")
                .run("assembleDebug");
    }
}
