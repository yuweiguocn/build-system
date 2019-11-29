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
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.SplitApks;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Smoke test for cold swap builds of Kotlin apps. */
public class KotlinColdSwapTest {

    private static final InstantRunVerifierStatus EXPECTED_STATUS =
            InstantRunVerifierStatus.METHOD_ADDED;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void activityClass() throws Exception {
        createActivityClass("", "");
    }

    @Test
    public void withMultiApk() throws Exception {
        new ColdSwapTester(project)
                .testMultiApk(
                        new ColdSwapTester.Steps() {
                            @Override
                            public void checkApks(@NonNull SplitApks apk) throws Exception {}

                            @Override
                            public void makeChange() throws Exception {
                                makeColdSwapChange();
                            }

                            @Override
                            public void checkVerifierStatus(
                                    @NonNull InstantRunVerifierStatus status) throws Exception {
                                assertThat(status).isEqualTo(EXPECTED_STATUS);
                            }

                            @Override
                            public void checkBuildMode(@NonNull InstantRunBuildMode buildMode)
                                    throws Exception {
                                // for api 24 a cold build mode is triggered
                                assertEquals(InstantRunBuildMode.COLD, buildMode);
                            }

                            @Override
                            public void checkArtifacts(
                                    @NonNull List<InstantRunBuildContext.Artifact> artifacts)
                                    throws Exception {
                                assertThat(artifacts).hasSize(1);
                                for (InstantRunBuildContext.Artifact artifact : artifacts) {
                                    expect.that(artifact.getType()).isEqualTo(FileType.SPLIT);
                                    checkUpdatedClassPresence(
                                            new SplitApks(
                                                    ImmutableList.of(
                                                            new Apk(artifact.getLocation()))));
                                }
                            }
                        });
    }

    private void makeColdSwapChange() throws Exception {
        createActivityClass(
                "import java.util.logging.Logger",
                "newMethod()\n"
                        + "    }\n"
                        + "    fun newMethod() {\n"
                        + "        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)\n"
                        + "                .warning(\"Added some logging\")\n"
                        + "");
    }

    private static void checkUpdatedClassPresence(@NonNull SplitApks apks) throws Exception {
        assertThat(apks)
                .hasClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasMethods("onCreate", "newMethod");
    }

    private void createActivityClass(@NonNull String imports, @NonNull String newMethodBody)
            throws Exception {
        String kotlinCompile =
                "package com.example.helloworld\n"
                        + imports
                        + "\n"
                        + "import android.app.Activity\n"
                        + "import android.os.Bundle\n"
                        + "\n"
                        + "class HelloWorld : Activity() {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    override fun onCreate(savedInstanceState: Bundle?) {\n"
                        + "        super.onCreate(savedInstanceState)\n"
                        + "        setContentView(R.layout.main)\n"
                        + "        "
                        + newMethodBody
                        + "    }\n"
                        + "}";
        Files.asCharSink(
                        project.file("src/main/kotlin/com/example/helloworld/HelloWorld.kt"),
                        Charsets.UTF_8)
                .write(kotlinCompile);
    }
}
