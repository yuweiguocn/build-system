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
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for localJars. */
public class LocalJarsTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("localJars").create();

    private static ModelContainer<AndroidProject> models;
    private static GradleBuildResult result = null;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        result = project.executor().run("clean", "assembleDebug");
        models = project.model().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    public void checkBuildResult() {
        assertThat(result.getTask(":baseLibrary:noop"))
                .ranBefore(":baseLibrary:transformNativeLibsWithSyncJniLibsForDebug");
    }

    @Test
    public void testModel() throws Exception {
        AndroidProject libModel = models.getOnlyModelMap().get(":baseLibrary");
        assertNotNull("Module app null-check", libModel);

        Variant releaseVariant = AndroidProjectUtils.getVariantByName(libModel, "release");

        DependencyGraphs graph = releaseVariant.getMainArtifact().getDependencyGraphs();
        assertNotNull(graph);

        LibraryGraphHelper helper = new LibraryGraphHelper(models);
        assertThat(helper.on(graph).withType(JAVA).asList()).hasSize(2);
    }
}
