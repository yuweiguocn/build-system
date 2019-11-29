/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test for unresolved placeholders in libraries. */
public class PlaceholderInLibsTest {
    private static Map<String, ProjectBuildOutput> outputModels;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("placeholderInLibsTest").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        outputModels =
                project.executeAndReturnOutputMultiModel(
                        "clean",
                        ":examplelibrary:generateDebugAndroidTestSources",
                        "app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        outputModels = null;
    }

    @Test
    public void testLibraryPlaceholderSubstitutionInFinalApk() throws Exception {

        // Load the custom model for the project
        ProjectBuildOutput projectBuildOutput = outputModels.get(":app");
        Collection<VariantBuildOutput> variantBuildOutputs =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantBuildOutputs).named("Variant Count").hasSize(2);

        // get the main artifact of the debug artifact
        VariantBuildOutput debugOutput =
                ProjectBuildOutputUtils.getVariantBuildOutput(projectBuildOutput, "flavorDebug");

        // get the outputs.
        Collection<OutputFile> debugOutputs = debugOutput.getOutputs();
        assertNotNull(debugOutputs);

        assertEquals(1, debugOutputs.size());
        OutputFile output = debugOutputs.iterator().next();
        assertEquals(1, output.getOutputs().size());

        List<String> apkBadging =
                ApkHelper.getApkBadging(output.getOutputs().iterator().next().getOutputFile());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" +
                    "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'")) {
                return;
            }
        }
        Assert.fail("failed to find the permission with the right placeholder substitution.");
    }
}
