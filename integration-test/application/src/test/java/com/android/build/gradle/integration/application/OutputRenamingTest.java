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
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantBuildOutputUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for class densitySplitInL */
public class OutputRenamingTest {

    private static AndroidProject model;
    private static ProjectBuildOutput outputModel;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplitInL").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "applicationVariants.all { variant ->\n"
                        + "    // Custom APK names (do not do this for 'dev' build type)\n"
                        + "    println variant.buildType.name\n"
                        + "    def baseFileName = \"project-${variant.flavorName}-${variant.versionCode}-${variant.buildType.name}\"\n"
                        + "    variant.outputs.all { output ->\n"
                        + "      output.outputFileName = \"${baseFileName}-signed.apk\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
        outputModel = project.executeAndReturnOutputModel("clean", "assemble");
        model =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        assertThat(model.getSyncIssues()).hasSize(1);
        assertThat(Iterables.getOnlyElement(model.getSyncIssues()).getMessage())
                .contains(
                        "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false.");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkSplitOutputs() throws Exception {
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        assertFileRenaming(DEBUG);
        assertFileRenaming(RELEASE);
    }

    private static void assertFileRenaming(String buildType) throws IOException {
        Collection<VariantBuildOutput> variantBuildOutputs = outputModel.getVariantsBuildOutput();
        assertThat(variantBuildOutputs).hasSize(2);
        VariantBuildOutput buildOutput =
                ProjectBuildOutputUtils.getVariantBuildOutput(outputModel, buildType);

        // get the outputs.
        Collection<OutputFile> outputs = buildOutput.getOutputs();
        assertNotNull(outputs);
        assertThat(outputs).hasSize(5);

        String expectedFileName = "project--12-" + buildType.toLowerCase() + "-signed.apk";
        File mainOutputFile =
                VariantBuildOutputUtils.getMainOutputFile(buildOutput).getOutputFile();
        assertEquals(expectedFileName, mainOutputFile.getName());
        assertTrue(mainOutputFile.exists());
    }
}
