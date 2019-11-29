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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.GraphItem;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Test;

public class SimpleCompositeBuildTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("simpleCompositeBuild")
                    .withoutNdk()
                    .withDependencyChecker(false)
                    .create();

    @Test
    public void testBuild() throws Exception {
        project.execute("clean", "assembleDebug");
        ModelContainer<AndroidProject> modelContainer = project.model().fetchAndroidProjects();

        AndroidProject appProject = modelContainer.getRootBuildModelMap().get(":app");

        Variant debugVariant = AndroidProjectUtils.getVariantByName(appProject, "debug");

        List<GraphItem> dependencies =
                debugVariant.getMainArtifact().getDependencyGraphs().getCompileDependencies();

        Truth.assertThat(dependencies).hasSize(1);

        String libAddress = Iterables.getOnlyElement(dependencies).getArtifactAddress();
        Truth.assertThat(libAddress).endsWith("string-utils@@:");
    }
}
