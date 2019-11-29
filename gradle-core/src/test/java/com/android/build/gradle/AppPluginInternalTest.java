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
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.tasks.AndroidJavaCompile;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultBuildType;
import com.android.builder.model.SigningConfig;
import com.google.common.collect.ImmutableMap;
import groovy.util.Eval;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import junit.framework.TestCase;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the internal workings of the app plugin ("android") */
public class AppPluginInternalTest {

    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();
    private Project project;

    @Before
    public void setUp() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(TestProjects.Plugin.APP)
                        .build();
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
    }

    @Test
    public void testBasic() {
        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(2, plugin.getVariantManager().getBuildTypes().size());
        TestCase.assertNotNull(
                plugin.getVariantManager().getBuildTypes().get(BuilderConstants.DEBUG));
        TestCase.assertNotNull(
                plugin.getVariantManager().getBuildTypes().get(BuilderConstants.RELEASE));
        TestCase.assertEquals(0, plugin.getVariantManager().getProductFlavors().size());

        List<VariantScope> variants = plugin.getVariantManager().getVariantScopes();
        VariantCheckers.checkDefaultVariants(variants);

        VariantCheckers.findVariantData(variants, "debug");
        VariantCheckers.findVariantData(variants, "release");
        VariantCheckers.findVariantData(variants, "debugAndroidTest");
    }

    @Test
    public void testDefaultConfig() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        fakeConfig {\n"
                        + "            storeFile project.file('aa')\n"
                        + "            storePassword 'bb'\n"
                        + "            keyAlias 'cc'\n"
                        + "            keyPassword 'dd'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 1\n"
                        + "        versionName '2.0'\n"
                        + "        minSdkVersion 2\n"
                        + "        targetSdkVersion 3\n"
                        + "\n"
                        + "        signingConfig signingConfigs.fakeConfig\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(
                Integer.valueOf(1), plugin.getExtension().getDefaultConfig().getVersionCode());
        TestCase.assertNotNull(plugin.getExtension().getDefaultConfig().getMinSdkVersion());
        TestCase.assertEquals(
                2, plugin.getExtension().getDefaultConfig().getMinSdkVersion().getApiLevel());
        TestCase.assertNotNull(plugin.getExtension().getDefaultConfig().getTargetSdkVersion());
        TestCase.assertEquals(
                3, plugin.getExtension().getDefaultConfig().getTargetSdkVersion().getApiLevel());
        TestCase.assertEquals("2.0", plugin.getExtension().getDefaultConfig().getVersionName());

        TestCase.assertEquals(
                new File(project.getProjectDir(), "aa"),
                plugin.getExtension().getDefaultConfig().getSigningConfig().getStoreFile());
        TestCase.assertEquals(
                "bb",
                plugin.getExtension().getDefaultConfig().getSigningConfig().getStorePassword());
        TestCase.assertEquals(
                "cc", plugin.getExtension().getDefaultConfig().getSigningConfig().getKeyAlias());
        TestCase.assertEquals(
                "dd", plugin.getExtension().getDefaultConfig().getSigningConfig().getKeyPassword());
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

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(3, plugin.getVariantManager().getBuildTypes().size());

        List<VariantScope> variants = plugin.getVariantManager().getVariantScopes();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 3);
        map.put("unitTests", 3);
        map.put("androidTests", 1);
        TestCase.assertEquals(VariantCheckers.countVariants(map), variants.size());

        String[] variantNames = new String[] {"debug", "release", "staging"};

        for (String variantName : variantNames) {
            VariantCheckers.findVariantData(variants, variantName);
        }

        BaseVariantData testVariant =
                VariantCheckers.findVariantData(variants, "stagingAndroidTest");
        TestCase.assertEquals(
                "staging", testVariant.getVariantConfiguration().getBuildType().getName());
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

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(2, plugin.getVariantManager().getProductFlavors().size());

        List<VariantScope> variants = plugin.getVariantManager().getVariantScopes();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 4);
        map.put("unitTests", 4);
        map.put("androidTests", 2);
        TestCase.assertEquals(VariantCheckers.countVariants(map), variants.size());

        String[] variantNames =
                new String[] {
                    "flavor1Debug",
                    "flavor1Release",
                    "flavor1DebugAndroidTest",
                    "flavor2Debug",
                    "flavor2Release",
                    "flavor2DebugAndroidTest"
                };

        for (String variantName : variantNames) {
            VariantCheckers.findVariantData(variants, variantName);
        }
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
                        + "            dimension 'dimension1'\n"
                        + "        }\n"
                        + "        f2 {\n"
                        + "            dimension 'dimension1'\n"
                        + "        }\n"
                        + "\n"
                        + "        fa {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "        fb {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "        fc {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        TestCase.assertEquals(5, plugin.getVariantManager().getProductFlavors().size());

        List<VariantScope> variants = plugin.getVariantManager().getVariantScopes();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 12);
        map.put("unitTests", 12);
        map.put("androidTests", 6);
        TestCase.assertEquals(VariantCheckers.countVariants(map), variants.size());

        String[] variantNames =
                new String[] {
                    "f1FaDebug",
                    "f1FbDebug",
                    "f1FcDebug",
                    "f2FaDebug",
                    "f2FbDebug",
                    "f2FcDebug",
                    "f1FaRelease",
                    "f1FbRelease",
                    "f1FcRelease",
                    "f2FaRelease",
                    "f2FbRelease",
                    "f2FcRelease",
                    "f1FaDebugAndroidTest",
                    "f1FbDebugAndroidTest",
                    "f1FcDebugAndroidTest",
                    "f2FaDebugAndroidTest",
                    "f2FbDebugAndroidTest",
                    "f2FcDebugAndroidTest"
                };

        for (String variantName : variantNames) {
            VariantCheckers.findVariantData(variants, variantName);
        }
    }
    @Test
    public void testSigningConfigs() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    signingConfigs {\n"
                        + "        one {\n"
                        + "            storeFile project.file('a1')\n"
                        + "            storePassword 'b1'\n"
                        + "            keyAlias 'c1'\n"
                        + "            keyPassword 'd1'\n"
                        + "        }\n"
                        + "        two {\n"
                        + "            storeFile project.file('a2')\n"
                        + "            storePassword 'b2'\n"
                        + "            keyAlias 'c2'\n"
                        + "            keyPassword 'd2'\n"
                        + "        }\n"
                        + "        three {\n"
                        + "            storeFile project.file('a3')\n"
                        + "            storePassword 'b3'\n"
                        + "            keyAlias 'c3'\n"
                        + "            keyPassword 'd3'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 1\n"
                        + "        versionName '2.0'\n"
                        + "        minSdkVersion 2\n"
                        + "        targetSdkVersion 3\n"
                        + "    }\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "        }\n"
                        + "        staging {\n"
                        + "        }\n"
                        + "        release {\n"
                        + "            signingConfig owner.signingConfigs.three\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "        }\n"
                        + "        flavor2 {\n"
                        + "            signingConfig owner.signingConfigs.one\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        List<VariantScope> variants = plugin.getVariantManager().getVariantScopes();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(3);
        map.put("appVariants", 6);
        map.put("unitTests", 6);
        map.put("androidTests", 2);
        TestCase.assertEquals(VariantCheckers.countVariants(map), variants.size());

        BaseVariantData variant;
        SigningConfig signingConfig;

        variant = VariantCheckers.findVariantData(variants, "flavor1Debug");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        final File file = signingConfig.getStoreFile();
        assertNotNull(file);
        assertThat(file.getAbsolutePath())
                .isEqualTo(
                        GradleKeystoreHelper.getDefaultDebugKeystoreLocation().getAbsolutePath());

        variant = VariantCheckers.findVariantData(variants, "flavor1Staging");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNull(signingConfig);

        variant = VariantCheckers.findVariantData(variants, "flavor1Release");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a3"), signingConfig.getStoreFile());

        variant = VariantCheckers.findVariantData(variants, "flavor2Debug");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        final File file1 = signingConfig.getStoreFile();
        assertNotNull(file1);
        assertThat(file1.getAbsolutePath())
                .isEqualTo(
                        GradleKeystoreHelper.getDefaultDebugKeystoreLocation().getAbsolutePath());

        variant = VariantCheckers.findVariantData(variants, "flavor2Staging");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a1"), signingConfig.getStoreFile());

        variant = VariantCheckers.findVariantData(variants, "flavor2Release");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a3"), signingConfig.getStoreFile());
    }

    /**
     * test that debug build type maps to the SigningConfig object as the signingConfig container
     */
    @Test
    public void testDebugSigningConfig() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        debug {\n"
                        + "            storePassword = 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);

        // check that the debug buildType has the updated debug signing config.
        DefaultBuildType buildType =
                (DefaultBuildType)
                        plugin.getVariantManager()
                                .getBuildTypes()
                                .get(BuilderConstants.DEBUG)
                                .getBuildType();
        SigningConfig signingConfig = buildType.getSigningConfig();
        TestCase.assertEquals(
                plugin.getVariantManager().getSigningConfigs().get(BuilderConstants.DEBUG),
                signingConfig);
        TestCase.assertEquals("foo", signingConfig.getStorePassword());
    }

    @Test
    public void testSigningConfigInitWith() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        foo.initWith(owner.signingConfigs.debug)\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);

        SigningConfig debugSC =
                plugin.getVariantManager().getSigningConfigs().get(BuilderConstants.DEBUG);
        SigningConfig fooSC = plugin.getVariantManager().getSigningConfigs().get("foo");

        TestCase.assertNotNull(fooSC);

        TestCase.assertEquals(debugSC.getStoreFile(), fooSC.getStoreFile());
        TestCase.assertEquals(debugSC.getStorePassword(), fooSC.getStorePassword());
        TestCase.assertEquals(debugSC.getKeyAlias(), fooSC.getKeyAlias());
        TestCase.assertEquals(debugSC.getKeyPassword(), fooSC.getKeyPassword());
    }

    @Test
    public void testPluginDetection() {
        project.apply(ImmutableMap.of("plugin", "java"));

        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        Exception recordedException = null;
        try {
            plugin.createAndroidTasks();
        } catch (Exception e) {
            recordedException = e;
        }

        TestCase.assertNotNull(recordedException);
        TestCase.assertEquals(BadPluginException.class, recordedException.getClass());
    }

    @Test
    public void testJava8CompileBootclasspath() {
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
        android.getCompileOptions().setSourceCompatibility(JavaVersion.VERSION_1_8);
        android.getCompileOptions().setTargetCompatibility(JavaVersion.VERSION_1_8);

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks();

        AndroidJavaCompile compileDebugJavaWithJavac =
                (AndroidJavaCompile) project.getTasks().getByName("compileDebugJavaWithJavac");
        Set<File> bootclasspath =
                compileDebugJavaWithJavac.getOptions().getBootstrapClasspath().getFiles();
        assertThat(bootclasspath.stream().map(File::getName).collect(Collectors.toSet()))
                .containsExactly("android.jar", "core-lambda-stubs.jar");
        assertThat(bootclasspath).containsExactlyElementsIn(android.getBootClasspath());
    }
}
