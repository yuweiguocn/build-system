/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.SplitApks;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests how Instant Run cleans up state after a cold swap.
 */
public class HotSwapWithNoChangesTest {

    private static final String LOG_TAG = "NoCodeChangeAfterCompatibleChangeTest";

    public static final String ACTIVITY_PATH =
            "src/main/java/com/example/helloworld/HelloWorld.java";

    private static final AndroidVersion VERSION_UNDER_TEST = new AndroidVersion(23, null);

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void activityClass() throws Exception {
        createActivityClass("Original");
    }

    @Test
    public void testRestartOnly() throws Exception {
        doTestArtifacts(
                () -> {
                    // Force cold swap.
                    project.executor()
                            .withInstantRun(
                                    VERSION_UNDER_TEST, OptionalCompilationStep.RESTART_ONLY)
                            .run("assembleDebug");
                });
    }

    @Test
    public void testIncompatibleChange() throws Exception {
        doTestArtifacts(
                () -> {
                    String newPath = ACTIVITY_PATH.replace("HelloWorld", "HelloWorldCopy");
                    File newFile = project.file(newPath);
                    Files.copy(project.file(ACTIVITY_PATH), newFile);
                    TestFileUtils.searchAndReplace(
                            newFile, "class HelloWorld", "class HelloWorldCopy");

                    // Adding a new class will force a cold swap.
                    project.executor().withInstantRun(VERSION_UNDER_TEST).run("assembleDebug");
                });
    }

    private void doTestArtifacts(BuildRunnable runColdSwapBuild) throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().fetchAndroidProjects().getOnlyModel());

        project.executor()
                .withInstantRun(VERSION_UNDER_TEST, OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");


        SplitApks allApks = InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel);

        assertThat(allApks)
                .hasClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasMethod("onCreate");

        assertThat(allApks).hasClass("Lcom/android/tools/ir/server/Server;");

        makeHotSwapChange();
        runColdSwapBuild.run();

        SplitApks splitApks = InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel);
        // one split and main APK since we are < N.
        assertThat(splitApks).hasSize(2);


        // now run again the incremental build.
        project.executor().withInstantRun(VERSION_UNDER_TEST).run("assembleDebug");

        InstantRunBuildContext buildContext =
                InstantRunTestUtils.loadBuildContext(VERSION_UNDER_TEST, instantRunModel);

        assertThat(buildContext.getLastBuild().getArtifacts()).hasSize(0);
        assertThat(buildContext.getLastBuild().getVerifierStatus())
                .isEqualTo(InstantRunVerifierStatus.NO_CHANGES);
    }

    private void makeHotSwapChange() throws Exception {
        createActivityClass("HOT SWAP!");
    }

    private void createActivityClass(String message) throws Exception {
        String javaCompile = "package com.example.helloworld;\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "import java.util.logging.Logger;\n"
                + "\n"
                + "public class HelloWorld extends Activity {\n"
                + "    /** Called when the activity is first created. */\n"
                + "    @Override\n"
                + "    public void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.main);\n"
                + "        Logger.getLogger(\"" + LOG_TAG + "\")\n"
                + "                .warning(\"" + message + "\");"
                + "    }\n"
                + "}\n";
        Files.asCharSink(project.file(ACTIVITY_PATH), Charsets.UTF_8).write(javaCompile);
    }

    @FunctionalInterface
    private interface BuildRunnable {
        void run() throws IOException, InterruptedException;
    }
}
