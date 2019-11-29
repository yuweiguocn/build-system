/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.desugar;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;
import static com.android.builder.core.DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;

/** Checks that we are able to build library modules using Java 8 language features. */
public class DesugarLibraryWithDesugarToolTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
                    .create();

    @Test
    public void doesNotRun() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("assembleDebug");
        Truth.assertThat(result.findTask(":transformClassesWithDesugarForDebug")).isNull();
    }

    @Test
    public void testUsingJava8() throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "interface Data {",
                        "    public static void doLambda() {",
                        "        Runnable r = () -> { };",
                        "    }",
                        "    default void defaultMethod () {}",
                        "}"));
        project.executor().with(BooleanOption.ENABLE_D8_DESUGARING, false).run("assembleDebug");
        assertThatAar(project.getAar("debug")).containsClass("Lcom/example/helloworld/Data;");
    }

    @Test
    public void testTryWithResourcesForTest()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n" + "android.defaultConfig.minSdkVersion %d\n",
                        MIN_SUPPORTED_API_TRY_WITH_RESOURCES - 1));
        project.executor()
                .with(BooleanOption.ENABLE_D8_DESUGARING, false)
                .run("assembleDebugAndroidTest");
        Apk testApk = project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
        for (String klass : DesugarAppWithDesugarToolTest.TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(testApk).containsClass(klass);
        }
    }

    private void enableDesugar() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.compileOptions.sourceCompatibility 1.8\n"
                        + "android.compileOptions.targetCompatibility 1.8");
    }
}
