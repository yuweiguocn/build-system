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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test desugaring for multi-project setups. */
@RunWith(FilterableParameterized.class)
public class DesugarMultiProjectTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<VariantScope.Java8LangSupport> getParams() {
        return ImmutableList.of(R8, D8, DESUGAR);
    }

    @Parameterized.Parameter public VariantScope.Java8LangSupport tool;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("multiproject").create();

    @Before
    public void setUp() throws IOException {
        if (tool == R8) {
            configureR8Desugaring(project.getSubproject("app"));
        }
        compileWithJava8Target();
        addSources();
    }

    @Test
    public void testIncrementalBuilds_changeExisting()
            throws Exception {
        executor().run("assembleDebug");

        TestFileUtils.addMethod(
                project.getSubproject("baseLibrary")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/CarbonForm.java")
                        .toFile(),
                "default void name() {}");
        executor().run("assembleDebug");
        try (Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)) {

            assertThat(apk).hasClass("Lcom/example/Cat;").that().hasMethod("name");
        }

        TestFileUtils.searchAndReplace(
                project.getSubproject("baseLibrary")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/CarbonForm.java")
                        .toFile(),
                "default void name() {}",
                "");
        executor().run("assembleDebug");
        try (Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)) {

            assertThat(apk).hasClass("Lcom/example/Cat;").that().doesNotHaveMethod("name");
        }

        executor().run("clean");

        TestFileUtils.addMethod(
                project.getSubproject("library")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/Animal.java")
                        .toFile(),
                "default void animalName() {}");
        executor().run("assembleDebug");
        try (Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)) {

            assertThat(apk).hasClass("Lcom/example/Cat;").that().hasMethod("animalName");
        }
    }

    @Test
    public void testIncrementalBuilds_addToDirectDependency()
            throws Exception {
        executor().run("assembleDebug");

        Path newLibSrc =
                project.getSubproject("library")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/IAnimal.java");
        String newLibSrcContent =
                "package com.example;\n"
                        + "public interface IAnimal {\n"
                        + "    default void kind() {}\n"
                        + "}";
        Files.write(newLibSrc, newLibSrcContent.getBytes());
        TestFileUtils.searchAndReplace(
                project.getSubproject("library")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/Animal.java")
                        .toFile(),
                "extends CarbonForm {",
                "extends CarbonForm, IAnimal {");
        executor().run("assembleDebug");
        try (Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).hasClass("Lcom/example/Cat;").that().hasMethod("kind");
        }

        Files.delete(newLibSrc);
        TestFileUtils.searchAndReplace(
                project.getSubproject("library")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/Animal.java")
                        .toFile(),
                ", IAnimal {",
                "{");
        executor().run("assembleDebug");
        try (Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).hasClass("Lcom/example/Cat;").that().doesNotHaveMethod("kind");
        }
    }

    @Test
    public void testIncrementalBuilds_addToTransitiveDependency()
            throws Exception {
        executor().run("assembleDebug");

        Path newBaseLibSrc =
                project.getSubproject("baseLibrary")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/ICarbonForm.java");
        String newBaseLibSrcContent =
                "package com.example;\n"
                        + "public interface ICarbonForm {\n"
                        + "    default void latinName() {}\n"
                        + "}";
        Files.write(newBaseLibSrc, newBaseLibSrcContent.getBytes());
        TestFileUtils.searchAndReplace(
                project.getSubproject("baseLibrary")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/CarbonForm.java")
                        .toFile(),
                "interface CarbonForm {",
                "interface CarbonForm extends ICarbonForm {");
        executor().run("assembleDebug");
        try (Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).hasClass("Lcom/example/Cat;").that().hasMethod("latinName");
        }

        Files.delete(newBaseLibSrc);
        TestFileUtils.searchAndReplace(
                project.getSubproject("baseLibrary")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/CarbonForm.java")
                        .toFile(),
                "extends ICarbonForm {",
                "{");
        executor().run("assembleDebug");
        try (Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).hasClass("Lcom/example/Cat;").that().doesNotHaveMethod("latinName");
        }
    }

    private void compileWithJava8Target() throws IOException {
        for (String subproject : ImmutableList.of("app", "baseLibrary", "library")) {
            TestFileUtils.appendToFile(
                    project.getSubproject(subproject).getBuildFile(),
                    "android.compileOptions.sourceCompatibility 1.8\n"
                            + "android.compileOptions.targetCompatibility 1.8");
        }
    }

    private void addSources() throws IOException {
        Path baseLibSrc =
                project.getSubproject("baseLibrary")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/CarbonForm.java");
        Files.createDirectories(baseLibSrc.getParent());
        String baseLibSrcContent =
                "package com.example;\n" + "public interface CarbonForm {\n" + "}";
        Files.write(baseLibSrc, baseLibSrcContent.getBytes());

        Path baseLibSrcFunctional =
                project.getSubproject("baseLibrary")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/Toy.java");
        Files.createDirectories(baseLibSrcFunctional.getParent());
        String baseLibSrcFunctionalContent =
                "package com.example;\n" + "public interface Toy {\n" + "    void play();\n" + "}";
        Files.write(baseLibSrcFunctional, baseLibSrcFunctionalContent.getBytes());

        Path libSrc =
                project.getSubproject("library")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/Animal.java");
        Files.createDirectories(libSrc.getParent());
        String libSrcContent =
                "package com.example;\n" + "public interface Animal extends CarbonForm {\n" + "}";
        Files.write(libSrc, libSrcContent.getBytes());

        Path appSrc =
                project.getSubproject("app")
                        .getMainSrcDir()
                        .toPath()
                        .resolve("com/example/Cat.java");
        Files.createDirectories(appSrc.getParent());
        String appSrcContent =
                "package com.example;\n"
                        + "public abstract class Cat implements Animal {\n"
                        + "    Toy t = () -> {};\n"
                        + "}";
        Files.write(appSrc, appSrcContent.getBytes());
    }

    @NonNull
    private GradleTaskExecutor executor() {
        return project.executor()
                .with(BooleanOption.ENABLE_D8_DESUGARING, tool == D8)
                .with(BooleanOption.ENABLE_R8, tool == R8)
                .with(BooleanOption.ENABLE_R8_DESUGARING, tool == R8);
    }
}
