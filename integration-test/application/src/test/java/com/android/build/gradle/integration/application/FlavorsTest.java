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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.SourceSetContainerUtils;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.ProductFlavorHelper;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.SourceProviderHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantHelper;
import com.android.builder.core.VariantType;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for flavors. */
public class FlavorsTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("flavors").create();

    @Test
    public void checkFlavorsInModel() throws Exception {
        AndroidProject model =
                project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();

        File projectDir = project.getTestDir();

        assertFalse("Library Project", model.isLibrary());
        assertEquals("Project Type", AndroidProject.PROJECT_TYPE_APP, model.getProjectType());

        assertThat(model.getFlavorDimensions()).containsExactly("group1", "group2");

        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        new SourceProviderHelper(
                        model.getName(), projectDir, "main", defaultConfig.getSourceProvider())
                .test();

        SourceProviderContainer testSourceProviderContainer =
                SourceSetContainerUtils.getExtraSourceProviderContainer(
                        defaultConfig, ARTIFACT_ANDROID_TEST);

        new SourceProviderHelper(
                        model.getName(),
                        projectDir,
                        VariantType.ANDROID_TEST_PREFIX,
                        testSourceProviderContainer.getSourceProvider())
                .test();

        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertThat(
                        buildTypes
                                .stream()
                                .map(type -> type.getBuildType().getName())
                                .collect(Collectors.toList()))
                .containsExactly("debug", "release");

        Map<String, String> expected =
                ImmutableMap.of("f1", "group1", "f2", "group1", "fa", "group2", "fb", "group2");

        Collection<ProductFlavorContainer> flavorContainers = model.getProductFlavors();
        assertThat(
                        flavorContainers
                                .stream()
                                .map(flavor -> flavor.getProductFlavor().getName())
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(expected.keySet());

        for (ProductFlavorContainer flavorContainer: flavorContainers) {
            ProductFlavor flavor = flavorContainer.getProductFlavor();
            assertThat(flavor.getDimension())
                    .named("Flavor " + flavor.getName())
                    .isEqualTo(expected.get(flavor.getName()));
        }

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 8, variants.size());
        Variant f1faDebugVariant = AndroidProjectUtils.getVariantByName(model, "f1FaDebug");
        assertThat(f1faDebugVariant.getProductFlavors()).containsExactly("f1", "fa");
        new ProductFlavorHelper(f1faDebugVariant.getMergedFlavor(), "F1faDebug Merged Flavor")
                .setMinSdkVersion(14)
                .setTestInstrumentationRunner("android.support.test.runner.AndroidJUnitRunner")
                .test();

        // Verify the build outputs
        ProjectBuildOutput projectBuildOutput = project.model().fetch(ProjectBuildOutput.class);
        Collection<VariantBuildOutput> variantBuildOutputs =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantBuildOutputs).named("Variant Output Count").hasSize(8);
        VariantBuildOutput f1f1VariantOutput =
                ProjectBuildOutputUtils.getVariantBuildOutput(projectBuildOutput, "f1FaDebug");
        new VariantHelper(
                        f1faDebugVariant,
                        f1f1VariantOutput,
                        projectDir,
                        "/f1Fa/debug/flavors-f1-fa-debug.apk")
                .test();
    }

    @Test
    public void compoundSourceSetsAreInModel() throws Exception {
        AndroidProject model =
                project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();

        for (Variant variant : model.getVariants()) {
            for (JavaArtifact javaArtifact : variant.getExtraJavaArtifacts()) {
                assertThat(javaArtifact.getMultiFlavorSourceProvider())
                        .named("MultiFlavor SourceProvider for " + javaArtifact.getName())
                        .isNotNull();
                assertThat(javaArtifact.getVariantSourceProvider())
                        .named("Variant SourceProvider for " + javaArtifact.getName())
                        .isNotNull();
            }

            for (AndroidArtifact androidArtifact : variant.getExtraAndroidArtifacts()) {
                assertThat(androidArtifact.getMultiFlavorSourceProvider())
                        .named("MultiFlavor SourceProvider for " + androidArtifact.getName())
                        .isNotNull();
                assertThat(androidArtifact.getVariantSourceProvider())
                        .named("Variant SourceProvider for " + androidArtifact.getName())
                        .isNotNull();
            }
        }
    }

    private void addProductFlavorsVersionNameSuffixes() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android{\n"
                        + "    defaultConfig {\n"
                        + "        versionName '1.0'\n"
                        + "    }\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            versionNameSuffix 'f1'\n"
                        + "        }\n"
                        + "        f2 {\n"
                        + "            versionNameSuffix '-f2'\n"
                        + "        }\n"
                        + "        fa {\n"
                        + "            versionNameSuffix '-fa'\n"
                        + "        }\n"
                        + "        fb {\n"
                        + "            versionNameSuffix 'fb'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    private void addBuildTypesVersionNameSuffixes() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        versionName '1.0'\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            versionNameSuffix 'release'\n"
                        + "        }\n"
                        + "        debug {\n"
                        + "            versionNameSuffix '-debug'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkVersionNameWithBuildTypeOnlySuffix() throws Exception {
        addBuildTypesVersionNameSuffixes();
        project.execute("clean", "assembleDebug", "assembleRelease");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1", "fa"))
                .hasVersionName("1.0-debug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1", "fb"))
                .hasVersionName("1.0-debug");

        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1", "fa"))
                .hasVersionName("1.0release");
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1", "fb"))
                .hasVersionName("1.0release");
    }

    @Test
    public void checkVersionNameWithProduceFlavorOnlySuffix() throws Exception {
        addProductFlavorsVersionNameSuffixes();
        project.execute("clean", "assembleDebug", "assembleRelease");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1", "fb"))
                .hasVersionName("1.0f1fb");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1", "fa"))
                .hasVersionName("1.0f1-fa");

        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1", "fb"))
                .hasVersionName("1.0f1fb");
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1", "fa"))
                .hasVersionName("1.0f1-fa");
    }

    @Test
    public void checVersionNameforFlavorAndBuildTypeSuffix() throws Exception {
        addBuildTypesVersionNameSuffixes();
        addProductFlavorsVersionNameSuffixes();

        project.execute("clean", "assembleDebug", "assembleRelease");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1", "fb"))
                .hasVersionName("1.0f1fb-debug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f2", "fb"))
                .hasVersionName("1.0-f2fb-debug");

        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f2", "fa"))
                .hasVersionName("1.0-f2-farelease");
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1", "fa"))
                .hasVersionName("1.0f1-farelease");
    }
}
