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

import static com.android.testutils.truth.FileSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ClassField;
import com.android.builder.model.Variant;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for Res Values declared in build type, flavors, and variant and how they override each other
 */
public class ResValueTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    private static AndroidProject model;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "            apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "                defaultConfig {\n"
                        + "                    resValue \"string\", \"VALUE_DEFAULT\", \"1\"\n"
                        + "                    resValue \"string\", \"VALUE_DEBUG\",   \"1\"\n"
                        + "                    resValue \"string\", \"VALUE_FLAVOR\",  \"1\"\n"
                        + "                    resValue \"string\", \"VALUE_VARIANT\", \"1\"\n"
                        + "                }\n"
                        + "\n"
                        + "                buildTypes {\n"
                        + "                    debug {\n"
                        + "                        resValue \"string\", \"VALUE_DEBUG\",   \"100\"\n"
                        + "                        resValue \"string\", \"VALUE_VARIANT\", \"100\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "\n"
                        + "                flavorDimensions 'foo'\n"
                        + "                productFlavors {\n"
                        + "                    flavor1 {\n"
                        + "                        resValue \"string\", \"VALUE_DEBUG\",   \"10\"\n"
                        + "                        resValue \"string\", \"VALUE_FLAVOR\",  \"10\"\n"
                        + "                        resValue \"string\", \"VALUE_VARIANT\", \"10\"\n"
                        + "                    }\n"
                        + "                    flavor2 {\n"
                        + "                        resValue \"string\", \"VALUE_DEBUG\",   \"20\"\n"
                        + "                        resValue \"string\", \"VALUE_FLAVOR\",  \"20\"\n"
                        + "                        resValue \"string\", \"VALUE_VARIANT\", \"20\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "\n"
                        + "                applicationVariants.all { variant ->\n"
                        + "                    if (variant.buildType.name == \"debug\") {\n"
                        + "                        variant.resValue \"string\", \"VALUE_VARIANT\", \"1000\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "            ");

        model =
                project.executeAndReturnModel(
                                "clean",
                                "generateFlavor1DebugResValue",
                                "generateFlavor1ReleaseResValue",
                                "generateFlavor2DebugResValue",
                                "generateFlavor2ReleaseResValue")
                        .getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void builFlavor1Debug() throws IOException {
        String expected =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Values from the variant -->\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">1000</string>\n"
                        + "    <!-- Values from build type: debug -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">100</string>\n"
                        + "    <!-- Values from product flavor: flavor1 -->\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">10</string>\n"
                        + "    <!-- Values from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor1/debug");
    }

    @Test
    public void modelFlavor1Debug() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "10");
        map.put("VALUE_DEBUG", "100");
        map.put("VALUE_VARIANT", "1000");
        checkVariant(model, "flavor1Debug", map);
    }

    @Test
    public void buildFlavor2Debug() throws IOException {
        String expected =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Values from the variant -->\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">1000</string>\n"
                        + "    <!-- Values from build type: debug -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">100</string>\n"
                        + "    <!-- Values from product flavor: flavor2 -->\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">20</string>\n"
                        + "    <!-- Values from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor2/debug");
    }

    @Test
    public void modelFlavor2Debug() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "20");
        map.put("VALUE_DEBUG", "100");
        map.put("VALUE_VARIANT", "1000");
        checkVariant(model, "flavor2Debug", map);
    }

    @Test
    public void buildFlavor1Release() throws IOException {
        String expected =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Values from product flavor: flavor1 -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">10</string>\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">10</string>\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">10</string>\n"
                        + "    <!-- Values from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor1/release");
    }

    @Test
    public void modelFlavor1Release() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "10");
        map.put("VALUE_DEBUG", "10");
        map.put("VALUE_VARIANT", "10");
        checkVariant(model, "flavor1Release", map);
    }

    @Test
    public void buildFlavor2Release() throws IOException {
        String expected =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Values from product flavor: flavor2 -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">20</string>\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">20</string>\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">20</string>\n"
                        + "    <!-- Values from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor2/release");
    }

    @Test
    public void modelFlavor2Release() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "20");
        map.put("VALUE_DEBUG", "20");
        map.put("VALUE_VARIANT", "20");
        checkVariant(model, "flavor2Release", map);
    }

    private static void checkBuildConfig(@NonNull String expected, @NonNull String variantDir)
            throws IOException {
        File outputFile =
                new File(
                        project.getTestDir(),
                        "build/generated/res/resValues/" + variantDir + "/values/generated.xml");
        assertThat(outputFile).isFile();
        assertThat(outputFile).contentWithUnixLineSeparatorsIsExactly(expected);
    }

    private static void checkVariant(
            @NonNull AndroidProject androidProject,
            @NonNull String variantName,
            @Nullable Map<String, String> valueMap) {
        Variant variant = AndroidProjectUtils.findVariantByName(androidProject, variantName);
        assertNotNull("${variantName} variant null-check", variant);

        AndroidArtifact artifact = variant.getMainArtifact();
        assertNotNull("${variantName} main artifact null-check", artifact);

        Map<String, ClassField> value = artifact.getResValues();
        assertNotNull(value);

        // check the map against the expected one.
        assertEquals(valueMap.keySet(), value.keySet());
        for (String key : valueMap.keySet()) {
            ClassField field = value.get(key);
            assertNotNull(variantName + ": expected field " + key, field);
            assertEquals(
                    variantName + ": check Value of " + key, valueMap.get(key), field.getValue());
        }
    }
}
