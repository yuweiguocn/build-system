/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.nio.file.Path;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test to ensure CMake project works with instant run.
 */
@Ignore("b/79563107")
public class CmakeInstantRunTest {

    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().withCmake().build())
                    .create();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(
                sProject.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        externalNativeBuild {\n"
                        + "            cmake {\n"
                        + "               path 'CMakeLists.txt'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n");
    }

    @AfterClass
    public static void tearDown() {
        sProject = null;
    }

    @Test
    public void checkHotSwapBuild() throws Exception {
        sProject.executor()
                .withInstantRun(new AndroidVersion(23, null))
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                .run("clean", "assembleDebug");

        AndroidProject model = sProject.model().fetchAndroidProjects().getOnlyModel();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        try (SplitApks apks = InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel)) {
            assertThat(apks.getEntries("lib/x86/libhello-jni.so")).hasSize(1);
        }

        TemporaryProjectModification.doTest(
                sProject,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "src/main/java/com/example/hellojni/HelloJni.java",
                            "tv.setText\\(.*\\)",
                            "tv.setText(\"Hello from Java\")");

                    sProject.executor()
                            .withInstantRun(new AndroidVersion(23, null))
                            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                            .run("assembleDebug");
                    InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
                    assertThat(context.getVerifierStatus())
                            .isEqualTo(InstantRunVerifierStatus.COMPATIBLE.toString());
                });
    }

    @Test
    public void checkFullBuildIsTriggered() throws Exception {
        sProject.executor()
                .withInstantRun(new AndroidVersion(23, null))
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                .run("clean", "assembleDebug");

        AndroidProject model = sProject.model().fetchAndroidProjects().getOnlyModel();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);

        byte[] lib = getSo(instantRunModel);
        File src = sProject.file("src/main/jni/hello-jni.c");
        Files.append("\nvoid foo() {}\n", src, Charsets.UTF_8);

        sProject.executor()
                .withInstantRun(new AndroidVersion(23, null))
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                .run("assembleDebug");
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED.toString());
        assertThat(context.getArtifacts()).hasSize(1);

        assertThat(getSo(instantRunModel)).isNotEqualTo(lib);
    }

    private static byte[] getSo(InstantRun instantRunModel) throws Exception {
        try (SplitApks apks = InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel)) {
            Path so = Iterables.getOnlyElement(apks.getEntries("lib/x86/libhello-jni.so"));
            return java.nio.file.Files.readAllBytes(so);
        }
    }

}
