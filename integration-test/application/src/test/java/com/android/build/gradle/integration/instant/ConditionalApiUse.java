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
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.builder.model.InstantRun;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunArtifact;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test that applications that use APIs not available in the minSdkVersion are properly
 * instrumented and packaged depending on the target deployment version.
 */
public class ConditionalApiUse {

    @Rule
    public final Adb adb = new Adb();

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("conditionalApiUse")
            .create();

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Test
    public void buildFor23() throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.doInitialBuild(project, new AndroidVersion(23, null));

        SplitApks apks = InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel);

        assertThat(apks)
                .hasClass("Lcom/android/tests/conditionalApiUse/MyException;")
                .that()
                .hasField("$change");
        // since we are building for 23, and the super class exists, the exception class should
        // be instrumented.

        makeHotswapCompatibleChange();

        project.executor().withInstantRun(new AndroidVersion(23, null)).run("assembleDebug");

        InstantRunArtifact reloadDexArtifact = InstantRunTestUtils
                .getReloadDexArtifact(instantRunModel);

        assertThatDex(reloadDexArtifact.file)
                .containsClass("Lcom/android/tests/conditionalApiUse/MyException$override;")
                .that()
                .hasMethod("toString");
    }

    private void makeHotswapCompatibleChange() throws Exception {
        String updatedClass = "package com.android.tests.conditionalApiUse;\n"
                + "\n"
                + "import android.hardware.camera2.CameraAccessException;\n"
                + "import android.os.Build;\n"
                + "\n"
                + "public class MyException extends CameraAccessException {\n"
                + "\n"
                + "    public MyException(int problem, String message, Throwable cause) {\n"
                + "        super(problem, message, cause);\n"
                + "    }\n"
                + "\n"
                + "    public String toString() {\n"
                + "        return \"Some string\";\n"
                + "    }\n"
                + "}";
        Files.asCharSink(
                        project.file(
                                "src/main/java/com/android/tests/conditionalApiUse/MyException.java"),
                        Charsets.UTF_8)
                .write(updatedClass);
    }
}
