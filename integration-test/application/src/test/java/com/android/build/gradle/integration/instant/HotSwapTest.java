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
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunArtifact;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Smoke test for hot swap builds.
 */
public class HotSwapTest {

    private static final String LOG_TAG = "hotswapTest";
    private static final String ORIGINAL_MESSAGE = "Original";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void activityClass() throws Exception {
        createActivityClass(ORIGINAL_MESSAGE);
    }

    @Test
    public void buildIncrementallyWithInstantRun() throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().fetchAndroidProjects().getOnlyModel());

        InstantRunTestUtils.doInitialBuild(project, new AndroidVersion(21, null));

        SplitApks apks = InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel);
        assertThat(apks).hasSize(11);

        assertThat(apks)
                .hasClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasMethod("onCreate");
        assertThat(apks).hasClass("Lcom/android/tools/ir/server/InstantRunContentProvider;");

        createActivityClass("CHANGE");

        project.executor().withInstantRun(new AndroidVersion(21, null)).run("assembleDebug");

        InstantRunArtifact artifact =
                InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file)
                .containsClass("Lcom/example/helloworld/HelloWorld$1$override;")
                .that()
                .hasMethod("call");
    }

    @Test
    public void testBuildEligibilityWithColdSwapRequested() throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().fetchAndroidProjects().getOnlyModel());

        InstantRunTestUtils.doInitialBuild(project, new AndroidVersion(21, null));

        createActivityClass("CHANGE");

        project.executor()
                .withInstantRun(new AndroidVersion(21, null), OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");

        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus())
                .isEqualTo(InstantRunVerifierStatus.COLD_SWAP_REQUESTED.toString());

        assertThat(context.getBuildInstantRunEligibility())
                .isEqualTo(InstantRunVerifierStatus.COMPATIBLE.toString());
    }

    @Test
    public void testJava8LangFeatures() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.compileOptions {\n"
                        + "  sourceCompatibility 1.8\n"
                        + "  targetCompatibility 1.8\n"
                        + "}");
        File source = project.file("src/main/java/com/example/helloworld/HelloWorld.java");
        TestFileUtils.addMethod(
                source,
                "\n"
                        + "public void foo() {\n"
                        + "  new Thread(() -> {}).start();\n"
                        + "  System.out.println(\"replaceThisMessage1234\");\n"
                        + "}");
        InstantRunTestUtils.doInitialBuild(project, new AndroidVersion(21, null));

        TestFileUtils.searchAndReplace(source, "replaceThisMessage1234", "newMsg");
        project.executor().withInstantRun(new AndroidVersion(21, null)).run("assembleDebug");
    }

    private void createActivityClass(String message) throws Exception {
        String javaCompile = "package com.example.helloworld;\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "import java.util.logging.Logger;\n"
                + "\n"
                + "import java.util.concurrent.Callable;\n"
                + "\n"
                + "public class HelloWorld extends Activity {\n"
                + "    /** Called when the activity is first created. */\n"
                + "    @Override\n"
                + "    public void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.main);\n"
                + "        Callable<Void> callable = new Callable<Void>() {\n"
                + "            @Override\n"
                + "            public Void call() throws Exception {\n"
                + "                Logger.getLogger(\"" + LOG_TAG + "\")\n"
                + "                        .warning(\"" + message + "\");"
                + "                return null;\n"
                + "            }\n"
                + "        };\n"
                + "        try {\n"
                + "            callable.call();\n"
                + "        } catch (Exception e) {\n"
                + "            throw new RuntimeException(e);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        Files.asCharSink(
                        project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                        Charsets.UTF_8)
                .write(javaCompile);
    }
}
