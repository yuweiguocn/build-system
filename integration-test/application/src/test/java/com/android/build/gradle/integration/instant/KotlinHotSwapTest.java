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
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.builder.model.InstantRun;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunArtifact;
import com.google.common.truth.Expect;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Smoke test for Kotlin hot swaps. */
public class KotlinHotSwapTest {
    private static final String LOG_TAG = "kotlinHotswapTest";
    private static final String ORIGINAL_MESSAGE = "Original";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

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

        // As no injected API level, will default to no splits.
        assertThat(apks)
                .hasClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasMethod("onCreate");
        assertThat(apks).hasClass("Lcom/android/tools/ir/server/InstantRunContentProvider;");

        createActivityClass("CHANGE");

        project.executor().withInstantRun(new AndroidVersion(21, null)).run("assembleDebug");

        InstantRunArtifact artifact = InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file)
                .containsClass("Lcom/example/helloworld/HelloWorld$onCreate$callable$1$override;")
                .that()
                .hasMethod("call");
    }

    @Test
    public void testModel() throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().fetchAndroidProjects().getOnlyModel());

        assertTrue(instantRunModel.isSupportedByArtifact());

        // TODO:  The kotlin android gradle plugin is very unhappy with the test
        // from ./HotSwapTest#testModel that checks that Jack is not supported.
        // It cannot process the build file at all.
    }

    private void createActivityClass(String message) throws Exception {
        String kotlinCompile =
                "package com.example.helloworld\n"
                        + "import android.app.Activity\n"
                        + "import android.os.Bundle\n"
                        + "import com.example.helloworld.R\n"
                        + "import java.util.concurrent.Callable\n"
                        + "import java.util.logging.Logger\n"
                        + "\n"
                        + "class HelloWorld : Activity() {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    override fun onCreate(savedInstanceState: Bundle?) {\n"
                        + "        super.onCreate(savedInstanceState)\n"
                        + "        setContentView(R.layout.main)\n"
                        + "        val callable = object : Callable<Unit> {\n"
                        + "            override fun call() {\n"
                        + "                Logger.getLogger(\""
                        + LOG_TAG
                        + "\")\n"
                        + "                        .warning(\""
                        + message
                        + "\")"
                        + "            }\n"
                        + "        }\n"
                        + "        try {\n"
                        + "            callable.call()\n"
                        + "        } catch (e: Exception) {\n"
                        + "            throw RuntimeException(e)\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n";

        FileUtils.write(
                project.file("src/main/kotlin/com/example/helloworld/HelloWorld.kt"),
                kotlinCompile);
    }
}
