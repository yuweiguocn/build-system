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
import static com.android.build.gradle.integration.desugar.DesugaringProjectConfigurator.configureR8Desugaring;
import static com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport.D8;
import static com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport.DESUGAR;
import static com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport.R8;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Checks that app depending on library module using Java 8 language builds. */
@RunWith(Parameterized.class)
public class DesugarAppWithLibraryTest {

    @Parameterized.Parameters(name = "tool = {0}")
    public static Object[] getParameters() {
        // noinspection unchecked
        return new Object[] {D8, DESUGAR, R8};
    }

    @Parameterized.Parameter public VariantScope.Java8LangSupport java8LangSupport;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("applibtest").create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nsubprojects {\n"
                        + "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n"
                        + "}\n");
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "android.compileOptions {\n"
                        + "    sourceCompatibility 1.8\n"
                        + "    targetCompatibility 1.8\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    compile project(':lib')\n"
                        + "}");
        TestFileUtils.appendToFile(
                project.getSubproject("lib").getBuildFile(),
                "android.compileOptions {\n"
                        + "    sourceCompatibility 1.8\n"
                        + "    targetCompatibility 1.8\n"
                        + "}");
    }

    @Test
    public void libraryUsingJava8() throws IOException, InterruptedException, ProcessException {
        Path added =
                project.getSubproject("lib")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/Data.java");
        Files.createDirectories(added.getParent());
        Files.write(
                added,
                ImmutableList.of(
                        "package com.example;",
                        "class Data {",
                        "    public void doLambda() {",
                        "         Runnable r = () -> {};",
                        "    }",
                        "}"));
        if (java8LangSupport == R8) {
            configureR8Desugaring(project.getSubproject("app"));
        }
        project.executor()
                .with(BooleanOption.ENABLE_D8_DESUGARING, java8LangSupport == D8)
                .with(BooleanOption.ENABLE_R8, java8LangSupport == R8)
                .with(BooleanOption.ENABLE_R8_DESUGARING, java8LangSupport == R8)
                .run(":app:assembleDebug");
        Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsClass("Lcom/example/Data;");
    }
}
