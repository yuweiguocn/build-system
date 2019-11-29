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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.builder.core.BuilderConstants.DEBUG;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for libTestDep. */
public class LibTestDepTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libTestDep").create();

    private static ModelContainer<AndroidProject> model;
    private static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        model = project.executeAndReturnModel("clean", "assembleDebug");
        outputModel = project.model().fetch(ProjectBuildOutput.class);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
        outputModel = null;
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    public void checkTestVariantInheritsDepsFromMainVariant() {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant debugVariant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), DEBUG);

        AndroidArtifact testArtifact = VariantUtils.getAndroidTestArtifact(debugVariant);

        DependencyGraphs testGraph = testArtifact.getDependencyGraphs();

        assertThat(
                        helper.on(testGraph)
                                .withType(JAVA)
                                .asList()
                                .stream()
                                .map(graphItem -> graphItem.getArtifactAddress())
                                .map(dependency -> dependency.substring(0, dependency.indexOf(':')))
                                .collect(Collectors.toList()))
                .containsExactly(
                        "com.google.guava",
                        "junit",
                        "org.hamcrest",
                        "com.android.support",
                        "net.sf.kxml",
                        "com.google.code.findbugs");
    }

    @Test
    public void checkDebugAndReleaseOutputHaveDifferentNames() {
        ProjectBuildOutputUtils.compareDebugAndReleaseOutput(outputModel);
    }
}
