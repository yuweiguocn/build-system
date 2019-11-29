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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for renamedApk. */
public class RenamedApkTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("renamedApk").create();

    private static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        outputModel = project.executeAndReturnOutputModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        outputModel = null;
    }

    @Test
    public void checkModelReflectsRenamedApk() throws Exception {
        File projectDir = project.getTestDir();
        VariantBuildOutput variantBuildOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);
        Collection<OutputFile> outputFiles = variantBuildOutput.getOutputs();

        File buildDir = new File(projectDir, "build/outputs/apk/debug");

        assertEquals(1, outputFiles.size());
        OutputFile output = outputFiles.iterator().next();

        String variantName = BuilderConstants.DEBUG;
        assertEquals(
                "Output file for " + variantName,
                new File(buildDir, variantName + ".apk"),
                output.getOutputFile());
    }

    @Test
    public void checkRenamedApk() {
        File debugApk = project.file("build/outputs/apk/debug/debug.apk");
        assertTrue("Check output file: " + debugApk, debugApk.isFile());
    }
}
