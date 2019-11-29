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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.desugar.DesugaringProjectConfigurator.configureR8Desugaring;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.desugar.resources.TestClass;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests use of Java 8 language in the application module, for D8 and Desugar tool. */
@RunWith(Parameterized.class)
public class DesugarAppTest {

    @NonNull private final Boolean enableGradleWorkers;
    @NonNull private final VariantScope.Java8LangSupport java8LangSupport;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Parameterized.Parameters(name = "enableGradleWorkers={0}, tool={1}")
    public static Collection<Object[]> getParameters() {
        // noinspection unchecked
        Set<Object[]> setups =
                Sets.cartesianProduct(
                                Sets.newHashSet(Boolean.TRUE, Boolean.FALSE),
                                Sets.newHashSet(
                                        VariantScope.Java8LangSupport.D8,
                                        VariantScope.Java8LangSupport.DESUGAR))
                        .stream()
                        .map(List::toArray)
                        .collect(Collectors.toSet());
        setups.add(new Object[] {Boolean.FALSE, VariantScope.Java8LangSupport.R8});
        return setups;
    }

    public DesugarAppTest(
            @NonNull Boolean enableGradleWorkers,
            @NonNull VariantScope.Java8LangSupport java8LangSupport) {
        this.enableGradleWorkers = enableGradleWorkers;
        this.java8LangSupport = java8LangSupport;
    }

    @Before
    public void setUp() {
        if (java8LangSupport == VariantScope.Java8LangSupport.R8) {
            configureR8Desugaring(project);
        }
    }

    @Test
    public void syncIssueIfJava8AndDesugaringDisabled() throws IOException {
        enableJava8();
        AndroidProject result =
                project.model()
                        .with(BooleanOption.ENABLE_D8_DESUGARING, false)
                        .with(BooleanOption.ENABLE_R8_DESUGARING, false)
                        .with(BooleanOption.ENABLE_DESUGAR, false)
                        .ignoreSyncIssues()
                        .fetchAndroidProjects()
                        .getOnlyModel();
        String expectedMsg = "to your gradle.properties file to enable Java 8 language support.";
        boolean found =
                result.getSyncIssues()
                        .stream()
                        .filter(i -> i.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .anyMatch(i -> i.getMessage().contains(expectedMsg));
        assertThat(found).named("Sync issue to enable desugaring found").isTrue();
    }

    @Test
    public void supportsJava8() throws IOException, InterruptedException, ProcessException {
        enableJava8();
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "class Data {",
                        "    public static void doLambda() {",
                        "        Runnable r = () -> { };",
                        "    }",
                        "}"));

        getProjectExecutor().run("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).containsClass("Lcom/example/helloworld/Data;");
        assertThat(apk).hasDexVersion(35);
    }

    @Test
    public void desugarsLibraryDependency()
            throws IOException, InterruptedException, ProcessException {
        enableJava8();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");
        List<String> classes = createLibToDesugarAndGetClasses();
        getProjectExecutor().run("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).hasDexVersion(35);

        if (java8LangSupport == VariantScope.Java8LangSupport.DESUGAR) {
            for (String klass :
                    Iterables.concat(
                            classes, DesugarAppWithDesugarToolTest.TRY_WITH_RESOURCES_RUNTIME)) {
                assertThat(apk).containsClass(klass);
            }
        }
    }

    @Test
    public void testNonDesugaredLibraryDependency() throws IOException, InterruptedException {
        // see b/65543679 for details
        Assume.assumeFalse(enableGradleWorkers);
        // see b/72994228
        Assume.assumeTrue(java8LangSupport != VariantScope.Java8LangSupport.R8);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");
        createLibToDesugarAndGetClasses();
        GradleBuildResult result = getProjectExecutor().expectFailure().run("assembleDebug");
        assertThat(result.getStderr())
                .contains(
                        "The dependency contains Java 8 bytecode. Please enable desugaring by adding the following to build.gradle\n");
    }

    @Test
    public void runsAfterJacoco() throws IOException, InterruptedException {
        enableJava8();
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.testCoverageEnabled true");
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "Runnable r = () -> { };");

        getProjectExecutor().run("assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).hasDexVersion(35);
    }

    @Test
    public void testBuildCacheIntegration()
            throws IOException, InterruptedException, ProcessException {
        // regression test for - http://b.android.com/292762
        enableJava8();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n"
                                + "android.buildTypes.debug.testCoverageEnabled true\n"
                                + "android.defaultConfig.minSdkVersion %d\n"
                                + "dependencies {\n"
                                + "    compile 'com.android.support:support-v4:%s'\n"
                                + "}",
                        TestVersions.SUPPORT_LIB_MIN_SDK, TestVersions.SUPPORT_LIB_VERSION));

        getProjectExecutor().run("assembleDebug");
        getProjectExecutor().run("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Landroid/support/v4/app/ActivityCompat;");
    }

    @Test
    public void testAndroidMethodInvocationsNotRewritten()
            throws IOException, InterruptedException, ProcessException {
        enableJava8();
        // using at least android-27 as ServiceConnection has a default method
        TestFileUtils.appendToFile(project.getBuildFile(), "\nandroid.compileSdkVersion " + DEFAULT_COMPILE_SDK_VERSION);

        Path newSource = project.getMainSrcDir().toPath().resolve("test").resolve("MyService.java");
        Files.createDirectories(newSource.getParent());
        Files.write(
                newSource,
                ImmutableList.of(
                        "package test;",
                        "import android.content.ServiceConnection;",
                        "import android.content.ComponentName;",
                        "import android.os.IBinder;",
                        "public class MyService implements ServiceConnection {",
                        "    public void onServiceConnected(ComponentName var1, IBinder var2) {}",
                        "    public void onServiceDisconnected(ComponentName var1) {}",
                        "}"));
        getProjectExecutor().run("assembleDebug");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .hasClass("Ltest/MyService;")
                .that()
                .doesNotHaveMethod("onBindingDied");
    }

    @Test
    public void testLegacyMultidexForDesugaredTypes() throws IOException, InterruptedException {
        enableJava8();
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "Runnable r = () -> { };");
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.buildTypes.debug.multiDexKeepProguard file('rules')\n"
                        + "android.defaultConfig.multiDexEnabled true\n"
                        + "android.defaultConfig.minSdkVersion 20");
        // just keep the HelloWorld, the lambda one should be inferred
        Files.write(
                project.getBuildFile().toPath().resolveSibling("rules"),
                "-keep class **HelloWorld".getBytes());

        getProjectExecutor().run("assembleDebug");

        ImmutableMap<String, DexBackedClassDef> classes =
                project.getApk(GradleTestProject.ApkType.DEBUG)
                        .getMainDexFile()
                        .orElseThrow(AssertionError::new)
                        .getClasses();

        long helloWorldClasses =
                classes.keySet().stream().filter(t -> t.contains("HelloWorld")).count();
        assertThat(helloWorldClasses)
                .named("original and synthesized classes count in the main dex ")
                .isEqualTo(2);
    }

    @Test
    public void testLegacyMultidexForDesugaredExternalLib()
            throws IOException, InterruptedException {
        createLibToDesugarAndGetClasses();
        enableJava8();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");

        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "Class<?> c = " + TestClass.class.getName() + ".class;");
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.buildTypes.debug.multiDexKeepProguard file('rules')\n"
                        + "android.defaultConfig.multiDexEnabled true\n"
                        + "android.defaultConfig.minSdkVersion 20");
        Files.write(
                project.getBuildFile().toPath().resolveSibling("rules"),
                "-keep class **HelloWorld".getBytes());

        getProjectExecutor().run("assembleDebug");

        ImmutableMap<String, DexBackedClassDef> classes =
                project.getApk(GradleTestProject.ApkType.DEBUG)
                        .getMainDexFile()
                        .orElseThrow(AssertionError::new)
                        .getClasses();

        long helloWorldClasses =
                classes.keySet().stream().filter(t -> t.contains("TestClass")).count();
        assertThat(helloWorldClasses)
                .named("original and synthesized classes count in the main dex ")
                .isEqualTo(2);
    }

    private void enableJava8() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.compileOptions.sourceCompatibility 1.8\n"
                        + "android.compileOptions.targetCompatibility 1.8");
    }

    @NonNull
    private List<String> createLibToDesugarAndGetClasses() throws IOException {
        Path lib = project.getTestDir().toPath().resolve("libs/my-lib.jar");
        Files.createDirectories(lib.getParent());
        TestInputsGenerator.pathWithClasses(lib, Lists.newArrayList(TestClass.class));

        return ImmutableList.of("L" + TestClass.class.getName().replaceAll("\\.", "/") + ";");
    }

    private GradleTaskExecutor getProjectExecutor() {
        return project.executor()
                .with(
                        BooleanOption.ENABLE_D8_DESUGARING,
                        java8LangSupport == VariantScope.Java8LangSupport.D8)
                .with(BooleanOption.ENABLE_GRADLE_WORKERS, enableGradleWorkers)
                .with(
                        BooleanOption.ENABLE_R8_DESUGARING,
                        java8LangSupport == VariantScope.Java8LangSupport.R8)
                .with(
                        BooleanOption.ENABLE_R8,
                        java8LangSupport == VariantScope.Java8LangSupport.R8);
    }
}
