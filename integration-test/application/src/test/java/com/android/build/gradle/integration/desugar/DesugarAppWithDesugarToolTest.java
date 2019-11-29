/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.builder.core.DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Desugar tool specific tests. */
@RunWith(Parameterized.class)
public class DesugarAppWithDesugarToolTest {
    static final ImmutableList<String> TRY_WITH_RESOURCES_RUNTIME =
            ImmutableList.of(
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$AbstractDesugaringStrategy;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$MimicDesugaringStrategy;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$NullDesugaringStrategy;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$ReuseDesugaringStrategy;");

    @NonNull private final Boolean enableGradleWorkers;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Parameterized.Parameters(name = "enableGradleWorkers={0}")
    public static Boolean[] getParameters() {
        return new Boolean[] {Boolean.TRUE, Boolean.FALSE};
    }

    public DesugarAppWithDesugarToolTest(@NonNull Boolean enableGradleWorkers) {
        this.enableGradleWorkers = enableGradleWorkers;
    }

    @Test
    public void noTaskIfNoJava8Set() throws IOException, InterruptedException {
        GradleBuildResult result = getProjectExecutor().run("assembleDebug");
        Truth.assertThat(result.findTask(":transformClassesWithDesugarForDebug")).isNull();
    }

    @Test
    public void taskRunsIfJava8Set() throws IOException, InterruptedException {
        enableJava8();
        GradleBuildResult result = getProjectExecutor().run("assembleDebug");
        assertThat(result.getTask(":transformClassesWithDesugarForDebug")).didWork();
    }

    @Test
    public void testTryWithResourcesPlatformUnsupported()
            throws IOException, InterruptedException, ProcessException {
        enableJava8();
        writeClassWithTryWithResources();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n" + "android.defaultConfig.minSdkVersion %d\n",
                        MIN_SUPPORTED_API_TRY_WITH_RESOURCES - 1));
        getProjectExecutor().run("assembleDebug", "assembleDebugAndroidTest");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        Apk testApk = project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(apk).containsClass(klass);
            assertThat(testApk).doesNotContainClass(klass);
        }
    }

    @Test
    public void testTryWithResourcesPlatformUnsupportedInstantRun() throws Exception {
        enableJava8();
        writeClassWithTryWithResources();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n" + "android.defaultConfig.minSdkVersion %d\n",
                        MIN_SUPPORTED_API_TRY_WITH_RESOURCES - 1));
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        Iterables.getOnlyElement(
                                project.model().fetchAndroidProjects().getOnlyModelMap().values()));
        getProjectExecutor()
                .withInstantRun(new AndroidVersion(24, null), OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");
        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);

        List<Apk> splits =
                initialContext
                        .getArtifacts()
                        .stream()
                        .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT)
                        .map(
                                a -> {
                                    try {
                                        return new Apk(a.file);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                })
                        .collect(Collectors.toList());
        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(new SplitApks(splits)).hasClass(klass);
        }
    }

    @Test
    public void testTryWithResourcesPlatformSupported()
            throws IOException, InterruptedException, ProcessException {
        enableJava8();
        writeClassWithTryWithResources();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n" + "android.defaultConfig.minSdkVersion %d\n",
                        MIN_SUPPORTED_API_TRY_WITH_RESOURCES));
        getProjectExecutor().run("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(apk).doesNotContainClass(klass);
        }

        // also make sure we do not reference ThrowableExtension classes
        Dex mainDex = apk.getMainDexFile().orElseThrow(AssertionError::new);
        DexBackedClassDef classDef = mainDex.getClasses().get("Lcom/example/helloworld/Data;");
        List<String> allTypesInDex =
                classDef.dexFile
                        .getTypes()
                        .stream()
                        .map(d -> d.getType())
                        .collect(Collectors.toList());

        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(allTypesInDex).doesNotContain(klass);
        }
    }

    @Test
    public void testUpToDateForIncCompileTasks() throws IOException, InterruptedException {
        enableJava8();
        getProjectExecutor().run("assembleDebug");

        Path newSource = project.getMainSrcDir().toPath().resolve("test").resolve("Data.java");
        Files.createDirectories(newSource.getParent());
        Files.write(newSource, ImmutableList.of("package test;", "public class Data {}"));
        GradleBuildResult result = getProjectExecutor().run("assembleDebug");

        assertThat(result.getUpToDateTasks())
                .containsAllIn(ImmutableList.of(":extractTryWithResourcesSupportJarDebug"));
    }

    @Test
    public void testWithBuildCacheDisabled() throws IOException, InterruptedException {
        enableJava8();
        GradleBuildResult result =
                getProjectExecutor()
                        .with(BooleanOption.ENABLE_BUILD_CACHE, false)
                        .run("assembleDebug");
        assertThat(result.getTask(":transformClassesWithDesugarForDebug")).didWork();
    }

    @Test
    public void testWithLegacyJacoco() throws IOException, InterruptedException {
        // java command is not logged when using workers
        Assume.assumeFalse(enableGradleWorkers);
        enableJava8();

        assertThat(
                        getProjectExecutor()
                                .withEnableInfoLogging(true)
                                .run("assembleDebug")
                                .getStdout())
                .doesNotContain("--legacy_jacoco_fix");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.buildTypes.debug.testCoverageEnabled true\n"
                        + "android.jacoco.version '"
                        + JacocoConfigurations.VERSION_FOR_DX
                        + "'");

        // now it should contain it as Jacoco version is lower
        assertThat(
                        getProjectExecutor()
                                .withEnableInfoLogging(true)
                                .run("assembleDebug")
                                .getStdout())
                .contains("--legacy_jacoco_fix");
    }

    @Test
    public void testMinifiedWithProguard()
            throws IOException, InterruptedException, ProcessException {
        enableJava8();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid {\n"
                        + "  defaultConfig.minSdkVersion 18\n"
                        + "  buildTypes {\n"
                        + "    debug {\n"
                        + "      minifyEnabled true\n"
                        + "      testProguardFiles getDefaultProguardFile('proguard-android.txt'), 'test-proguard-rules.pro'\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");

        Files.write(
                project.file("test-proguard-rules.pro").toPath(),
                ImmutableList.of(
                        "# Part of the XML pull API comes with the platform, but ATSL depends on kxml2 which bundles the same classes.",
                        "-dontwarn org.xmlpull.**"));

        // add try with catch in test
        TestFileUtils.addMethod(
                project.file("src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                "void doSomething() throws java.io.IOException {\n"
                        + "    try(java.io.StringWriter sw = new java.io.StringWriter(1)) {}\n"
                        + "}\n");

        getProjectExecutor().with(BooleanOption.ENABLE_R8, false).run("assembleDebugAndroidTest");

        assertThatApk(project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG))
                .containsClass("Lcom/example/helloworld/HelloWorldTest;");
    }

    private void enableJava8() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.compileOptions.sourceCompatibility 1.8\n"
                        + "android.compileOptions.targetCompatibility 1.8");
    }

    private void writeClassWithTryWithResources() throws IOException {
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "import java.io.StringReader;",
                        "public class Data {",
                        "    public void foo() {",
                        "        try(StringReader r = new StringReader(\"\")) {",
                        "        }",
                        "    }",
                        "}"));
    }

    private GradleTaskExecutor getProjectExecutor() {
        return project.executor()
                .with(BooleanOption.ENABLE_D8_DESUGARING, false)
                .with(BooleanOption.ENABLE_R8_DESUGARING, false)
                .with(BooleanOption.ENABLE_GRADLE_WORKERS, enableGradleWorkers);
    }
}
