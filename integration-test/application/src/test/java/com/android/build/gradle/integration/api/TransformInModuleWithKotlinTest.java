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

package com.android.build.gradle.integration.api;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;

public class TransformInModuleWithKotlinTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("transformInModuleWithKotlin").create();

    @Test
    public void testTransform() throws IOException, InterruptedException, ProcessException {
        // enable coverage in library as regression test for b/65345148
        Files.append(
                "\nandroid.buildTypes.debug.testCoverageEnabled true\n",
                project.getSubproject("lib").getBuildFile(),
                Charsets.UTF_8);

        Map<String, ProjectBuildOutput> multi =
                project.executeAndReturnOutputMultiModel("clean", "assembleDebug");
        ProjectBuildOutput appBuildOutput = multi.get(":app");
        assertThat(appBuildOutput).isNotNull();
        Collection<VariantBuildOutput> buildOutputs = appBuildOutput.getVariantsBuildOutput();
        assertThat(buildOutputs).hasSize(2);
        Optional<VariantBuildOutput> debug =
                buildOutputs
                        .stream()
                        .filter(variantBuildOutput -> variantBuildOutput.getName().equals("debug"))
                        .findFirst();
        assertThat(debug.isPresent()).isTrue();
        Collection<OutputFile> debugOutputs = debug.get().getOutputs();
        assertThat(debugOutputs).hasSize(1);
        assertThatApk(new Apk(Iterables.getOnlyElement(debugOutputs).getOutputFile()))
                .hasClass("LHello;");
    }
}
