/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for multiDex. */
@RunWith(FilterableParameterized.class)
public class MultiDexTest {

    enum MainDexListTool {
        D8,
        R8,
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("multiDex").withHeap("2048M").create();

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "mainDexListTool = {0}")
    public static Object[] data() {
        return MainDexListTool.values();
    }

    @Parameterized.Parameter public MainDexListTool tool;

    @Test
    public void checkBuildWithoutKeepRuntimeAnnotatedClasses() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.dexOptions.keepRuntimeAnnotatedClasses false");

        executor()
                .run("assembleDebug", "makeApkFromBundleForIcsDebug", "assembleAndroidTest");

        List<String> mandatoryClasses =
                Lists.newArrayList("Lcom/android/tests/basic/MyAnnotation;");

        assertMainDexContains("debug", mandatoryClasses);

        try (Apk bundleBase = getStandaloneBundleApk()) {
            assertMainDexContains(bundleBase, mandatoryClasses);
        }

        // manually inspect the apk to ensure that the classes.dex that was created is the same
        // one in the apk. This tests that the packaging didn't rename the multiple dex files
        // around when we packaged them.
        List<File> allClassesDex =
                FileUtils.find(
                        project.getIntermediateFile("dex"),
                        Pattern.compile("icsDebug/mergeDexIcsDebug/out/classes\\.dex"));
        assertThat(allClassesDex).hasSize(1);
        File classesDex = allClassesDex.get(0);

        assertThat(project.getApk("ics", "debug"))
                .containsFileWithContent("classes.dex", Files.readAllBytes(classesDex.toPath()));

        File classes2Dex = FileUtils.join(classesDex.getParentFile(), "classes2.dex");

        assertThat(project.getApk("ics", "debug"))
                .containsFileWithContent("classes2.dex", Files.readAllBytes(classes2Dex.toPath()));

        commonApkChecks("debug");

        assertThat(project.getTestApk("ics"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");
        assertThat(project.getTestApk("lollipop"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");

        // Both test APKs should contain a class from Junit.
        assertThat(project.getTestApk("ics")).containsClass("Lorg/junit/Assert;");
        assertThat(project.getTestApk("lollipop")).containsClass("Lorg/junit/Assert;");

        assertThat(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/NotUsed;");
        assertThat(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/DeadCode;");
    }

    @Test
    public void checkApplicationNameAdded() throws IOException, InterruptedException {
        // noinspection ResultOfMethodCallIgnored
        FileUtils.join(project.getTestDir(), "src/ics/AndroidManifest.xml").delete();
        executor().run("processIcsDebugManifest");
        assertThat(
                        FileUtils.join(
                                project.getTestDir(),
                                "build/intermediates/merged_manifests/icsDebug/AndroidManifest.xml"))
                .contains("android:name=\"android.support.multidex.MultiDexApplication\"");
    }

    private Apk getStandaloneBundleApk() throws IOException {
        Path extracted = temporaryFolder.newFile("standalone-hdpi.apk").toPath();

        try (FileSystem apks =
                        FileUtils.createZipFilesystem(
                                project.getIntermediateFile(
                                                "apks_from_bundle",
                                                "icsDebug",
                                                "makeApkFromBundleForIcsDebug",
                                                "bundle.apks")
                                        .toPath());
                BufferedOutputStream out =
                        new BufferedOutputStream(Files.newOutputStream(extracted))) {
            Files.copy(apks.getPath("standalones/standalone-hdpi.apk"), out);
        }
        return new Apk(extracted);
    }

    @Test
    public void checkProguard() throws Exception {
        checkMinifiedBuild("proguard");
    }

    @Test
    public void checkShrinker() throws Exception {
        checkMinifiedBuild("r8");
    }

    public void checkMinifiedBuild(String buildType) throws Exception {
        executor().run(StringHelper.appendCapitalized("assemble", buildType));

        assertMainDexContains(buildType, ImmutableList.of());

        commonApkChecks(buildType);

        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;");
        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .doesNotContainClass("Lcom/android/tests/basic/DeadCode;");
    }

    @Test
    public void checkNativeMultidexAndroidTest() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    androidTestCompile 'com.android.support:appcompat-v7:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}");
        executor().run("assembleLollipopDebugAndroidTest");
        // it should contain 2 dex files, one for sources, one for the external lib
        assertThat(project.getTestApk("lollipop")).contains("classes.dex");
        assertThat(project.getTestApk("lollipop")).contains("classes2.dex");
    }

    @Test
    public void checkLegacyMultiDexAndroidTest()
            throws IOException, InterruptedException, ProcessException {
        executor().run("assembleIcsDebugAndroidTest");

        Apk testApk = project.getTestApk("ics");
        assertThat(testApk).contains("classes.dex");
        assertThat(testApk).contains("classes2.dex");
        assertThat(testApk).containsClass("Lcom/android/tests/basic/OtherActivityTest;");
    }

    @Test
    public void checkLegacyMultiInstrumentationForAndroidTest()
            throws IOException, InterruptedException, ProcessException {
        String someClass =
                "package example;\n"
                        + "public class SomeClass {\n"
                        + "  Object o = new OtherClass();\n"
                        + "}\n"
                        + "class OtherClass {}\n";
        Path someClassPath =
                project.getTestDir()
                        .toPath()
                        .resolve("src/androidTest/java/example/SomeClass.java");
        Files.createDirectories(someClassPath.getParent());
        Files.write(someClassPath, someClass.getBytes());

        String instrumentation =
                "package example;\n"
                        + "public class MyRunner extends android.app.Instrumentation {\n"
                        + "  public void callApplicationOnCreate(android.app.Application app) {\n"
                        + "    new SomeClass();\n"
                        + "  }\n"
                        + "}\n";
        Path instrumentationPath =
                project.getTestDir().toPath().resolve("src/androidTest/java/example/MyRunner.java");
        Files.createDirectories(instrumentationPath.getParent());
        Files.write(instrumentationPath, instrumentation.getBytes());

        executor().run("assembleIcsDebugAndroidTest");

        Apk testApk = project.getTestApk("ics");
        assertThat(testApk).containsMainClass("Lexample/OtherClass;");
    }

    private void commonApkChecks(String buildType) throws Exception {
        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .containsClass("Landroid/support/multidex/MultiDexApplication;");
        assertThat(project.getApk(ApkType.of(buildType, true), "lollipop"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");

        for (String flavor : ImmutableList.of("ics", "lollipop")) {
            assertThat(project.getApk(ApkType.of(buildType, true), flavor))
                    .containsClass("Lcom/android/tests/basic/Main;");
            assertThat(project.getApk(ApkType.of(buildType, true), flavor))
                    .containsClass("Lcom/android/tests/basic/Used;");
            assertThat(project.getApk(ApkType.of(buildType, true), flavor))
                    .containsClass("Lcom/android/tests/basic/Kept;");
        }
    }

    private void assertMainDexContains(
            @NonNull String buildType, @NonNull List<String> mandatoryClasses) throws Exception {
        Apk apk = project.getApk(ApkType.of(buildType, true), "ics");
        assertMainDexContains(apk, mandatoryClasses);
    }

    private static void assertMainDexContains(
            @NonNull Apk apk, @NonNull List<String> mandatoryClasses) throws IOException {
        Dex mainDex = apk.getMainDexFile().orElseThrow(AssertionError::new);

        ImmutableSet<String> mainDexClasses = mainDex.getClasses().keySet();
        assertThat(mainDexClasses).contains("Landroid/support/multidex/MultiDexApplication;");

        Set<String> nonMultidexSupportClasses =
                mainDexClasses
                        .stream()
                        .filter(c -> !c.startsWith("Landroid/support/multidex"))
                        .collect(Collectors.toSet());
        assertThat(nonMultidexSupportClasses).containsExactlyElementsIn(mandatoryClasses);
    }

    @NonNull
    private GradleTaskExecutor executor() {
        return project.executor().with(BooleanOption.ENABLE_R8, tool == MainDexListTool.R8);
    }
}
