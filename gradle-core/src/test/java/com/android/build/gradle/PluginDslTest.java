/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.errors.SyncIssueHandler;
import com.android.build.gradle.internal.fixture.BaseTestedVariant;
import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantChecker;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.AndroidJavaCompile;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestOptions.Execution;
import com.android.builder.model.Version;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import groovy.util.Eval;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for checking the "application" and "atom" DSLs. */
@RunWith(Parameterized.class)
public class PluginDslTest {

    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();
    private BasePlugin plugin;
    private BaseExtension android;
    private Project project;
    private VariantChecker checker;
    private final TestProjects.Plugin pluginType;

    @Parameterized.Parameters(name = "{0}")
    public static TestProjects.Plugin[] generateStates() {
        return new TestProjects.Plugin[] {TestProjects.Plugin.APP};
    }

    public PluginDslTest(TestProjects.Plugin pluginType) {
        this.pluginType = pluginType;
    }


    @Before
    public void setUp() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(pluginType)
                        .withProperty(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED, true)
                        .build();

        android = project.getExtensions().getByType(pluginType.getExtensionClass());
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);

        if (pluginType == TestProjects.Plugin.APP) {
            plugin = (AppPlugin) project.getPlugins().getPlugin(pluginType.getPluginClass());
            checker = VariantCheckers.createAppChecker((AppExtension) android);
        } else {
            throw new AssertionError("Unsupported plugin type");
        }
    }

    @Test
    public void testBasic() {
        plugin.createAndroidTasks();
        VariantCheckers.checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<BaseTestedVariant> variants = checker.getVariants();
        Truth.assertThat(variants).named("variant list").hasSize(2);

        Set<TestVariant> testVariants = checker.getTestVariants();
        Truth.assertThat(testVariants).named("test variant list").hasSize(1);

        checker.checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
    }

    @Test
    public void testBasicWithStringTarget() {
        Eval.me(
                "project",
                project,
                "\n        project.android {\n            compileSdkVersion 'android-"
                        + String.valueOf(TestConstants.COMPILE_SDK_VERSION)
                        + "'\n        }\n");

        plugin.createAndroidTasks();
        VariantCheckers.checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created
        Set<BaseTestedVariant> variants = checker.getVariants();
        Truth.assertThat(variants).named("variant list").hasSize(2);

        Set<TestVariant> testVariants = checker.getTestVariants();
        Truth.assertThat(testVariants).named("test variant list").hasSize(1);

        checker.checkTestedVariant("debug", "debugAndroidTest", variants, testVariants);
        checker.checkNonTestedVariant("release", variants);
    }

    @Test
    public void testMultiRes() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    sourceSets.main.res.srcDirs 'src/main/res1', 'src/main/res2'\n"
                        + "}\n");

        // nothing to be done here. If the DSL fails, it'll throw an exception
    }

    @Test
    public void testBuildTypes() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    testBuildType 'staging'\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        staging {\n"
                        + "            signingConfig signingConfigs.debug\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 3);
        map.put("unitTest", 3);
        map.put("androidTests", 1);
        assertThat(VariantCheckers.countVariants(map))
                .isEqualTo(plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        Truth.assertThat(variants).named("variant list").hasSize(3);

        Set<TestVariant> testVariants = checker.getTestVariants();
        Truth.assertThat(testVariants).named("test variant list").hasSize(1);

        checker.checkTestedVariant("staging", "stagingAndroidTest", variants, testVariants);

        checker.checkNonTestedVariant("debug", variants);
        checker.checkNonTestedVariant("release", variants);
    }

    @Test
    public void testFlavors() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "\n"
                        + "        }\n"
                        + "        flavor2 {\n"
                        + "\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 4);
        map.put("unitTest", 4);
        map.put("androidTests", 2);
        assertThat(VariantCheckers.countVariants(map))
                .isEqualTo(plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        Truth.assertThat(variants).named("variant list").hasSize(4);

        Set<TestVariant> testVariants = checker.getTestVariants();
        Truth.assertThat(testVariants).named("test variant list").hasSize(2);

        checker.checkTestedVariant(
                "flavor1Debug", "flavor1DebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant(
                "flavor2Debug", "flavor2DebugAndroidTest", variants, testVariants);

        checker.checkNonTestedVariant("flavor1Release", variants);
        checker.checkNonTestedVariant("flavor2Release", variants);
    }

    @Test
    public void testMultiFlavors() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    flavorDimensions   'dimension1', 'dimension2'\n"
                        + "\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            dimension   'dimension1'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'f1'\n"
                        + "        }\n"
                        + "        f2 {\n"
                        + "            dimension   'dimension1'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'f2'\n"
                        + "        }\n"
                        + "\n"
                        + "        fa {\n"
                        + "            dimension   'dimension2'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'fa'\n"
                        + "        }\n"
                        + "        fb {\n"
                        + "            dimension   'dimension2'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'fb'\n"
                        + "        }\n"
                        + "        fc {\n"
                        + "            dimension   'dimension2'\n"
                        + "            javaCompileOptions.annotationProcessorOptions.className 'fc'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks();
        ImmutableMap<String, Integer> map =
                ImmutableMap.of("appVariants", 12, "unitTests", 12, "androidTests", 6);
        assertThat(VariantCheckers.countVariants(map))
                .isEqualTo(plugin.getVariantManager().getVariantScopes().size());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        Truth.assertThat(variants).named("variant list").hasSize(12);

        Set<TestVariant> testVariants = checker.getTestVariants();
        Truth.assertThat(testVariants).named("test variant list").hasSize(6);

        checker.checkTestedVariant("f1FaDebug", "f1FaDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f1FbDebug", "f1FbDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f1FcDebug", "f1FcDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f2FaDebug", "f2FaDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f2FbDebug", "f2FbDebugAndroidTest", variants, testVariants);
        checker.checkTestedVariant("f2FcDebug", "f2FcDebugAndroidTest", variants, testVariants);

        Map<String, VariantScope> variantMap = getVariantMap();

        for (String dim1 : ImmutableList.of("f1", "f2")) {
            for (String dim2 : ImmutableList.of("fa", "fb", "fc")) {
                String variantName =
                        StringHelper.combineAsCamelCase(ImmutableList.of(dim1, dim2, "debug"));
                GradleVariantConfiguration variant =
                        variantMap.get(variantName).getVariantConfiguration();
                assertThat(
                                variant.getJavaCompileOptions()
                                        .getAnnotationProcessorOptions()
                                        .getClassNames())
                        .containsExactly(dim2, dim1)
                        .inOrder();
            }
        }

        checker.checkNonTestedVariant("f1FaRelease", variants);
        checker.checkNonTestedVariant("f1FbRelease", variants);
        checker.checkNonTestedVariant("f1FcRelease", variants);
        checker.checkNonTestedVariant("f2FaRelease", variants);
        checker.checkNonTestedVariant("f2FbRelease", variants);
        checker.checkNonTestedVariant("f2FcRelease", variants);
    }

    @Test
    public void testSourceSetsApi() {
        // query the sourceSets, will throw if missing
        Eval.me(
                "project",
                project,
                "    println project.android.sourceSets.main.java.srcDirs\n"
                        + "println project.android.sourceSets.main.resources.srcDirs\n"
                        + "println project.android.sourceSets.main.manifest.srcFile\n"
                        + "println project.android.sourceSets.main.res.srcDirs\n"
                        + "println project.android.sourceSets.main.assets.srcDirs");
    }

    @Test
    public void testObfuscationMappingFile() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            minifyEnabled true\n"
                        + "            proguardFile getDefaultProguardFile('proguard-android.txt')\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        plugin.createAndroidTasks();
        VariantCheckers.checkDefaultVariants(plugin.getVariantManager().getVariantScopes());

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<BaseTestedVariant> variants = checker.getVariants();
        Truth.assertThat(variants).named("variant list").hasSize(2);

        for (BaseTestedVariant variant : variants) {
            if ("release".equals(variant.getBuildType().getName())) {
                assertThat(variant.getMappingFile()).named("release mapping file").isNotNull();
            } else {
                Assert.assertNull(variant.getMappingFile());
            }
        }
    }

    @Test
    public void testProguardFiles_oldDsl() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            proguardFile 'file1.1'\n"
                        + "            proguardFiles 'file1.2', 'file1.3'\n"
                        + "        }\n"
                        + "\n"
                        + "        custom {\n"
                        + "            proguardFile 'file3.1'\n"
                        + "            proguardFiles 'file3.2', 'file3.3'\n"
                        + "            proguardFiles = ['file3.1']\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            proguardFile 'file2.1'\n"
                        + "            proguardFiles 'file2.2', 'file2.3'\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "\n"
                        + "        }\n"
                        + "\n"
                        + "        f3 {\n"
                        + "            proguardFile 'file4.1'\n"
                        + "            proguardFiles 'file4.2', 'file4.3'\n"
                        + "            proguardFiles = ['file4.1']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();

        Map<String, List<String>> expected = new TreeMap<>();
        expected.put(
                "f1Release",
                ImmutableList.of("file1.1", "file1.2", "file1.3", "file2.1", "file2.2", "file2.3"));
        expected.put("f1Debug", ImmutableList.of("file2.1", "file2.2", "file2.3"));
        expected.put("f2Release", ImmutableList.of("file1.1", "file1.2", "file1.3"));
        expected.put("f2Custom", ImmutableList.of("file3.1"));
        expected.put("f3Custom", ImmutableList.of("file3.1", "file4.1"));

        checkProguardFiles(expected);
    }

    @Test
    public void testProguardFiles_newDsl() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            postprocessing{\n"
                        + "                proguardFile 'file1.1'\n"
                        + "                proguardFiles 'file1.2', 'file1.3'\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        custom {\n"
                        + "            postprocessing {\n"
                        + "                proguardFile 'file3.1'\n"
                        + "                proguardFiles 'file3.2', 'file3.3'\n"
                        + "                proguardFiles = ['file3.1']\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            proguardFile 'file2.1'\n"
                        + "            proguardFiles 'file2.2', 'file2.3'\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "\n"
                        + "        }\n"
                        + "\n"
                        + "        f3 {\n"
                        + "            proguardFile 'file4.1'\n"
                        + "            proguardFiles 'file4.2', 'file4.3'\n"
                        + "            proguardFiles = ['file4.1']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();

        String defaultFile =
                new File(
                                project.getBuildDir(),
                                "intermediates/proguard-files/proguard-defaults.txt-"
                                        + Version.ANDROID_GRADLE_PLUGIN_VERSION)
                        .getAbsolutePath();

        Map<String, List<String>> expected = new TreeMap<>();
        expected.put(
                "f1Release",
                ImmutableList.of(
                        defaultFile,
                        "file1.1",
                        "file1.2",
                        "file1.3",
                        "file2.1",
                        "file2.2",
                        "file2.3"));
        expected.put("f2Release", ImmutableList.of(defaultFile, "file1.1", "file1.2", "file1.3"));

        // The custom build type uses setProguardFiles, so the default file will not be there.
        expected.put("f2Custom", ImmutableList.of("file3.1"));
        expected.put("f3Custom", ImmutableList.of("file3.1", "file4.1"));

        checkProguardFiles(expected);
    }

    @Test
    public void testSettingLanguageLevelFromCompileSdk_doNotOverride() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    compileOptions {\n"
                        + "        sourceCompatibility '1.6'\n"
                        + "        targetCompatibility '1.6'\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();

        AndroidJavaCompile compileReleaseJavaWithJavac =
                (AndroidJavaCompile)
                        project.getTasks().getByName(checker.getReleaseJavacTaskName());

        assertThat(compileReleaseJavaWithJavac.getTargetCompatibility())
                .named("target compat")
                .isEqualTo(JavaVersion.VERSION_1_6.toString());
        assertThat(compileReleaseJavaWithJavac.getSourceCompatibility())
                .named("source compat")
                .isEqualTo(JavaVersion.VERSION_1_6.toString());
    }

    @Test
    public void testSettingLanguageLevelFromCompileSdkWithJavaVersion_doNotOverride() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "import org.gradle.api.JavaVersion\n"
                        + "project.android {\n"
                        + "    compileOptions {\n"
                        + "        sourceCompatibility = JavaVersion.VERSION_1_8\n"
                        + "        targetCompatibility = JavaVersion.VERSION_1_8\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();

        AndroidJavaCompile compileReleaseJavaWithJavac =
                (AndroidJavaCompile)
                        project.getTasks().getByName(checker.getReleaseJavacTaskName());

        assertThat(compileReleaseJavaWithJavac.getTargetCompatibility())
                .named("target compat")
                .isEqualTo(JavaVersion.VERSION_1_8.toString());
        assertThat(compileReleaseJavaWithJavac.getSourceCompatibility())
                .named("source compat")
                .isEqualTo(JavaVersion.VERSION_1_8.toString());
    }

    @Test
    public void testMockableJarName() {
        android.setCompileSdkVersion(
                "Google Inc.:Google APIs:" + TestConstants.COMPILE_SDK_VERSION_WITH_GOOGLE_APIS);
        plugin.createAndroidTasks();
        Map<String, VariantScope> variantMap = getVariantMap();
        Map.Entry<String, VariantScope> vsentry = variantMap.entrySet().iterator().next();
        File mockableJarFile =
                vsentry.getValue().getGlobalScope().getMockableJarArtifact().getSingleFile();
        assertThat(mockableJarFile).isNotNull();

        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
            // Windows paths contain : to identify drives.
            assertThat(mockableJarFile.getAbsolutePath())
                    .named("Mockable jar Path")
                    .doesNotContain(":");
        }

        assertThat(mockableJarFile.getName()).named("Mockable jar name").isEqualTo("android.jar");
    }

    @Test
    public void testEncoding() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    compileOptions {\n"
                        + "       encoding 'foo'\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();

        AndroidJavaCompile compileReleaseJavaWithJavac =
                (AndroidJavaCompile)
                        project.getTasks().getByName(checker.getReleaseJavacTaskName());

        assertThat(compileReleaseJavaWithJavac.getOptions().getEncoding())
                .named("source encoding")
                .isEqualTo("foo");
    }

    @Test
    public void testInstrumentationRunnerArguments_merging() throws Exception {

        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    defaultConfig {\n"
                        + "        testInstrumentationRunnerArguments(value: 'default', size: 'small')\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "            testInstrumentationRunnerArgument 'value', 'f2'\n"
                        + "        }\n"
                        + "\n"
                        + "        f3  {\n"
                        + "            testInstrumentationRunnerArguments['otherValue'] = 'f3'\n"
                        + "        }\n"
                        + "\n"
                        + "        f4  {\n"
                        + "            testInstrumentationRunnerArguments(otherValue: 'f4.1')\n"
                        + "            testInstrumentationRunnerArguments = [otherValue: 'f4.2']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks();

        Map<String, VariantScope> variantMap = getVariantMap();

        Map<String, Map<String, String>> expected =
                ImmutableMap.of(
                        "f1Debug",
                        ImmutableMap.of("value", "default", "size", "small"),
                        "f2Debug",
                        ImmutableMap.of("value", "f2", "size", "small"),
                        "f3Debug",
                        ImmutableMap.of("value", "default", "size", "small", "otherValue", "f3"),
                        "f4Debug",
                        ImmutableMap.of("value", "default", "size", "small", "otherValue", "f4.2"));

        expected.forEach(
                (variant, args) ->
                        assertThat(
                                        variantMap
                                                .get(variant)
                                                .getVariantConfiguration()
                                                .getInstrumentationRunnerArguments())
                                .containsExactlyEntriesIn(args));
    }

    /** Make sure DSL objects don't need "=" everywhere. */
    @Test
    public void testSetters() throws Exception {

        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            useProguard false\n"
                        + "            shrinkResources true\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
        BuildType debug = android.getBuildTypes().getByName("debug");
        assertThat(debug.isUseProguard()).isFalse();
        assertThat(debug.isShrinkResources()).isTrue();
    }

    @Test
    public void testTestOptionsExecution() throws Exception {
        android.getTestOptions().setExecution("android_test_orchestrator");
        assertThat(android.getTestOptions().getExecutionEnum())
                .isEqualTo(Execution.ANDROID_TEST_ORCHESTRATOR);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAdbExe() throws Exception {
        assertThat(android.getAdbExe()).named("adb exe").isNotNull();
        assertThat(android.getAdbExecutable()).named("adb executable").isNotNull();

        assertThat(android.getAdbExe()).named("adb exe").isEqualTo(android.getAdbExecutable());
    }

    @Test
    public void testSetOlderBuildToolsVersion() {
        android.setBuildToolsVersion("19.0.0");
        plugin.createAndroidTasks();
        assertThat(plugin.getAndroidBuilder().getBuildToolInfo().getRevision())
                .isEqualTo(AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION);
        // FIXME once we get rid of the component model, we can make this better.
        Collection<SyncIssue> syncIssues =
                ((SyncIssueHandler) plugin.getAndroidBuilder().getIssueReporter()).getSyncIssues();
        assertThat(syncIssues).hasSize(1);
        SyncIssue issue = Iterables.getOnlyElement(syncIssues);
        assertThat(issue.getType()).isEqualTo(SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW);
        assertThat(issue.getSeverity()).isEqualTo(SyncIssue.SEVERITY_WARNING);
        assertThat(issue.getMessage())
                .isEqualTo(
                        "The specified Android SDK Build Tools version (19.0.0) is "
                                + "ignored, as it is below the minimum supported version ("
                                + AndroidBuilder.MIN_BUILD_TOOLS_REV
                                + ") for Android Gradle Plugin "
                                + Version.ANDROID_GRADLE_PLUGIN_VERSION
                                + ".\n"
                                + "Android SDK Build Tools "
                                + AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION
                                + " will be used.\n"
                                + "To suppress this warning, remove \"buildToolsVersion '19.0.0'\" from your build.gradle file, "
                                + "as each version of the Android Gradle Plugin now has a default version of the build tools.");
    }

    public void checkProguardFiles(Map<String, List<String>> expected) {
        Map<String, VariantScope> variantMap = getVariantMap();
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            String variantName = entry.getKey();
            Set<File> proguardFiles =
                    variantMap
                            .get(variantName)
                            .getProguardFiles()
                            .stream()
                            .map(File::getAbsoluteFile)
                            .collect(Collectors.toSet());
            Set<File> expectedFiles =
                    entry.getValue().stream().map(project::file).collect(Collectors.toSet());
            assertThat(proguardFiles)
                    .named("Proguard files for " + variantName)
                    .containsExactlyElementsIn(expectedFiles);
        }
    }

    public Map<String, VariantScope> getVariantMap() {
        Map<String, VariantScope> result = new HashMap<>();
        for (VariantScope variantScope : plugin.getVariantManager().getVariantScopes()) {
            result.put(variantScope.getFullVariantName(), variantScope);
        }
        return result;
    }
}
