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

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Iterators;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.JavaVersion;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Assemble tests for basic.
 */
@Category(SmokeTests.class)
public class BasicTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .withoutNdk()
            .create();

    public static AndroidProject model;
    public static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void getModel() throws Exception {
        outputModel =
                project.executeAndReturnOutputModel("clean", "assembleDebug", "assembleRelease");
        // basic project overwrites buildConfigField which emits a sync warning
        model = project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel();
        model.getSyncIssues()
                .forEach(
                        issue -> {
                            assertThat(issue.getSeverity()).isEqualTo(SyncIssue.SEVERITY_WARNING);
                            assertThat(issue.getMessage())
                                    .containsMatch(Pattern.compile(".*value is being replaced.*"));
                        });
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void report() throws Exception {
        project.execute("androidDependencies");
    }

    @Test
    public void basicModel() {
        assertNotEquals("Library Project", model.getProjectType(), PROJECT_TYPE_LIBRARY);
        assertEquals("Project Type", AndroidProject.PROJECT_TYPE_APP, model.getProjectType());
        assertEquals(
                "Compile Target", GradleTestProject.getCompileSdkHash(), model.getCompileTarget());
        assertFalse("Non empty bootclasspath", model.getBootClasspath().isEmpty());

        assertNotNull("aaptOptions not null", model.getAaptOptions());
        assertEquals("aaptOptions noCompress", 1, model.getAaptOptions().getNoCompress().size());
        assertTrue("aaptOptions noCompress",
                model.getAaptOptions().getNoCompress().contains("txt"));
        assertEquals(
                "aaptOptions ignoreAssetsPattern",
                "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~",
                model.getAaptOptions().getIgnoreAssets());
        assertFalse(
                "aaptOptions getFailOnMissingConfigEntry",
                model.getAaptOptions().getFailOnMissingConfigEntry());

        // Since source and target compatibility are not explicitly set in the build.gradle,
        // the default value depends on the JDK used.
        JavaVersion expected;
        if (JavaVersion.current().isJava7Compatible()) {
            expected = JavaVersion.VERSION_1_7;
        } else {
            expected = JavaVersion.VERSION_1_6;
        }

        JavaCompileOptions javaCompileOptions = model.getJavaCompileOptions();
        assertEquals(
                expected.toString(),
                javaCompileOptions.getSourceCompatibility());
        assertEquals(
                expected.toString(),
                javaCompileOptions.getTargetCompatibility());
        assertEquals("UTF-8", javaCompileOptions.getEncoding());
    }

    @Test
    public void sourceProvidersModel() {
        AndroidProjectUtils.testDefaultSourceSets(model, project.getTestDir());

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact();
            assertNull(artifact.getVariantSourceProvider());
            assertNull(artifact.getMultiFlavorSourceProvider());
        }
    }

    @Test
    public void checkDebugAndReleaseOutputHaveDifferentNames() {
        ProjectBuildOutputUtils.compareDebugAndReleaseOutput(outputModel);
    }

    @Test
    public void weDontFailOnLicenceDotTxtWhenPackagingDependencies() throws Exception {
        project.execute("assembleAndroidTest");
    }

    @Test
    public void generationInModel() {
        assertThat(model.getPluginGeneration())
                .named("Plugin Generation")
                .isEqualTo(AndroidProject.GENERATION_ORIGINAL);
    }

    @Test
    public void checkDensityAndResourceConfigs() throws Exception {
        project.executor()
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                .with(StringOption.IDE_BUILD_TARGET_DENSITY, "xxhdpi")
                .run("assembleDebug");
    }

    @Test
    public void testBuildOutputModel() throws Exception {
        // Execute build and get the initial minimalistic model.
        Map<String, ProjectBuildOutput> multi =
                project.executeAndReturnOutputMultiModel(
                        "assemble", "assembleDebugAndroidTest", "testDebugUnitTest");

        ProjectBuildOutput mainModule = multi.get(":");
        assertThat(mainModule.getVariantsBuildOutput()).hasSize(2);
        assertThat(
                        mainModule
                                .getVariantsBuildOutput()
                                .stream()
                                .map(VariantBuildOutput::getName)
                                .collect(Collectors.toList()))
                .containsExactly("debug", "release");

        for (VariantBuildOutput variantBuildOutput : mainModule.getVariantsBuildOutput()) {
            assertThat(variantBuildOutput.getOutputs()).hasSize(1);
            OutputFile output = variantBuildOutput.getOutputs().iterator().next();
            assertThat(output.getOutputFile().exists()).isTrue();
            assertThat(output.getFilters()).isEmpty();
            assertThat(output.getOutputType()).isEqualTo("MAIN");

            int expectedTestedVariants = variantBuildOutput.getName().equals("debug") ? 2 : 1;
            assertThat(variantBuildOutput.getTestingVariants()).hasSize(expectedTestedVariants);
            List<String> testVariantTypes =
                    variantBuildOutput
                            .getTestingVariants()
                            .stream()
                            .map(TestVariantBuildOutput::getType)
                            .collect(Collectors.toList());
            if (expectedTestedVariants == 1) {
                assertThat(testVariantTypes).containsExactly("UNIT");
            } else {
                assertThat(testVariantTypes).containsExactly("UNIT", "ANDROID_TEST");
            }

            for (TestVariantBuildOutput testVariantBuildOutput :
                    variantBuildOutput.getTestingVariants()) {
                assertThat(testVariantBuildOutput.getTestedVariantName())
                        .isEqualTo(variantBuildOutput.getName());
                assertThat(testVariantBuildOutput.getOutputs()).hasSize(1);
                output = Iterators.getOnlyElement(testVariantBuildOutput.getOutputs().iterator());
                assertThat(output.getOutputType()).isEqualTo("MAIN");
                assertThat(output.getFilters()).isEmpty();
                if (variantBuildOutput.getName().equals("debug")) {
                    assertThat(output.getOutputFile().exists()).isTrue();
                }
            }
        }
    }

    @Test
    public void testRenderscriptDidNotRun() throws Exception {
        // Execute renderscript task and check if it was skipped
        Map<String, ProjectBuildOutput> multi =
                project.executeAndReturnOutputMultiModel("compileDebugRenderscript");
        assertThat(
                        project.getBuildResult()
                                .getTask(":compileDebugRenderscript")
                                .getExecutionState()
                                .toString())
                .isEqualTo("SKIPPED");
    }
}
