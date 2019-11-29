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

package com.android.build.gradle.integration.api;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.testutils.apk.Aar;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** test to verify the hook to register custom pre-javac compilers */
@Category(SmokeTests.class)
public class BytecodeGenerationHooksTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("bytecodeGenerationHooks").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void buildApp() throws Exception {
        GradleBuildResult result = project.executor().run("clean", "app:assembleDebug");

        // check that the app's dex file contains the App class.
        Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();
        Optional<Dex> dexOptional = apk.getMainDexFile();
        assertThat(dexOptional).isPresent();
        //noinspection OptionalGetWithoutIsPresent
        final Dex dexFile = dexOptional.get();
        assertThat(dexFile).containsClasses("Lcom/example/bytecode/App;");
        assertThat(dexFile).containsClasses("Lcom/example/bytecode/PostJavacApp;");

        assertThat(apk).contains("META-INF/app.kotlin_module");
        assertThat(apk).contains("META-INF/post-app.kotlin_module");
        assertThat(apk).contains("META-INF/lib.kotlin_module");
        assertThat(apk).contains("META-INF/post-lib.kotlin_module");

        // also verify that the kotlin module files are present in the intermediate classes.jar
        // published by the library

        File classesJar = project.file("library/build/intermediates/compile_library_classes/debug/classes.jar");
        assertThat(classesJar).isFile();
        try (Zip classesZip = new Zip(classesJar)) {
            assertThat(classesZip).contains("META-INF/lib.kotlin_module");
            assertThat(classesZip).contains("META-INF/post-lib.kotlin_module");
        }

        File resJar = project.file("library/build/intermediates/library_java_res/debug/res.jar");
        assertThat(resJar).isFile();
        try (Zip resZip = new Zip(classesJar)) {
            assertThat(resZip).contains("META-INF/lib.kotlin_module");
            assertThat(resZip).contains("META-INF/post-lib.kotlin_module");

            // verify the compile classpath
            checkDependencies(
                    result,
                    "BytecodeGeneratingTask(:app:generateBytecodeFordebug): ",
                    true,
                    "library/build/intermediates/compile_library_classes/debug/classes.jar",
                    "jar/build/libs/jar.jar");

            // verify source folders
            checkSourceFolders(result, "SourceFoldersApi(:app:debug): ", "app/src/custom/java");
        }
    }

    @Test
    public void buildAppTest() throws Exception {
        GradleBuildResult result = project.executor().run("clean", "app:assembleAndroidTest");

        final GradleTestProject appProject = project.getSubproject("app");

        Apk apk = appProject.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
        assertThat(apk.getFile()).isFile();
        assertThat(apk).contains("META-INF/test.kotlin_module");

        // also verify that the app's jar used by test compilation contains the kotlin module files
        File classesJar =
                appProject.getIntermediateFile("app_classes/debug/bundleDebugClasses/classes.jar");
        assertThat(classesJar).isFile();
        try (Zip classesZip = new Zip(classesJar)) {
            assertThat(classesZip).contains("META-INF/app.kotlin_module");
            assertThat(classesZip).contains("META-INF/post-app.kotlin_module");

            // verify the compile classpath
            checkDependencies(
                    result,
                    "BytecodeGeneratingTask(:app:generateBytecodeFordebugAndroidTest): ",
                    true,
                    "app/build/intermediates/app_classes/debug/bundleDebugClasses/classes.jar",
                    "library/build/intermediates/compile_library_classes/debug/classes.jar",
                    "jar/build/libs/jar.jar");
        }
    }


    @Test
    public void buildAppUnitTest() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "app:testDebugUnitTest");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:app:generateBytecodeFordebugUnitTest): ",
                false,
                "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
                "app/build/generated/preJavacbytecode/debug",
                "app/build/generated/postJavacBytecode/debug",
                "library/build/intermediates/compile_library_classes/debug/classes.jar",
                "jar/build/libs/jar.jar");
    }

    @Test
    public void buildLibrary() throws Exception {
        project.execute("clean", "lib:assembleDebug");

        try (Aar aar = project.getSubproject("library").getAar("debug");
             Zip classes = aar.getEntryAsZip("classes.jar")) {
            assertThat(classes).contains("com/example/bytecode/Lib.class");
            assertThat(classes).contains("com/example/bytecode/PostJavacLib.class");
            assertThat(classes).contains("META-INF/lib.kotlin_module");
            assertThat(classes).contains("META-INF/post-lib.kotlin_module");
        }
    }

    @Test
    public void buildLibTest() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "lib:assembleAndroidTest");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:library:generateBytecodeFordebugAndroidTest): ",
                true,
                "library/build/intermediates/compile_library_classes/debug/classes.jar");
    }

    @Test
    public void buildLibUnitTest() throws IOException, InterruptedException {
        project.execute("clean", "lib:testDebugUnitTest");
    }

    @Test
    public void buildTestApp() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "test:assembleDebug");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:test:generateBytecodeFordebug): ",
                true,
                "app/build/intermediates/app_classes/debug/bundleDebugClasses/classes.jar",
                "jar/build/libs/jar.jar",
                "library/build/intermediates/compile_library_classes/debug/classes.jar");
    }

    @Test
    public void buildFeature() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "bundle:assembleDebug");

        // check that the feature dex file contains the App class.
        Apk apk =
                project.getSubproject("baseFeature").getFeatureApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();
        Optional<Dex> dexOptional = apk.getMainDexFile();
        assertThat(dexOptional).isPresent();
        //noinspection OptionalGetWithoutIsPresent
        final Dex dexFile = dexOptional.get();
        assertThat(dexFile).containsClasses("Lcom/example/bytecode/App;");
        assertThat(dexFile).containsClasses("Lcom/example/bytecode/PostJavacApp;");

        assertThat(apk).contains("META-INF/app.kotlin_module");
        assertThat(apk).contains("META-INF/post-app.kotlin_module");
        assertThat(apk).contains("META-INF/lib.kotlin_module");
        assertThat(apk).contains("META-INF/post-lib.kotlin_module");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:baseFeature:generateBytecodeFordebugFeature): ",
                true,
                "library/build/intermediates/compile_library_classes/debug/classes.jar",
                "jar/build/libs/jar.jar");

        // verify source folders
        checkSourceFolders(
                result, "SourceFoldersApi(:baseFeature:debug): ", "baseFeature/src/custom/java");
    }

    private void checkDependencies(
            GradleBuildResult result, String prefix, boolean exactly, String... dependencies) {
        List<String> lines = result.getStdoutAsLines();
        String projectDir = project.getTestDir().getAbsolutePath();

        lines =
                lines.stream()
                        .filter(s -> s.startsWith(prefix))
                        .map(s -> s.substring(prefix.length()))
                        // Internal deps only. Ignore com.android.support.test:rules/runner/monitor
                        .filter(s -> s.contains(projectDir))
                        .collect(Collectors.toList());

        List<String> deps =
                Arrays.stream(dependencies)
                        .map(
                                s ->
                                        new File(projectDir, FileUtils.toSystemDependentPath(s))
                                                .getAbsolutePath())
                        .collect(Collectors.toList());

        if (exactly) {
            TruthHelper.assertThat(lines).containsExactlyElementsIn(deps);
        } else {
            TruthHelper.assertThat(lines).containsAllIn(deps);
        }
    }

    private void checkSourceFolders(
            GradleBuildResult result, String prefix, String... dependencies) {
        List<String> lines = result.getStdoutAsLines();

        lines =
                lines.stream()
                        .filter(s -> s.startsWith(prefix))
                        .map(s -> s.substring(prefix.length()))
                        .collect(Collectors.toList());

        File projectDir = project.getTestDir();

        List<String> deps =
                Arrays.stream(dependencies)
                        .map(
                                s ->
                                        new File(projectDir, FileUtils.toSystemDependentPath(s))
                                                .getAbsolutePath())
                        .collect(Collectors.toList());

        TruthHelper.assertThat(lines).containsAllIn(deps);
    }
}
