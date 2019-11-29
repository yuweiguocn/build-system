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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
/** Assemble tests for tictactoe. */
public class TictactoeTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("tictactoe").create();

    private static ModelContainer<AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        models = project.executeAndReturnMultiModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void testModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(models);

        AndroidProject libModel = models.getOnlyModelMap().get(":lib");
        assertNotNull("lib module model null-check", libModel);
        assertEquals("lib module library flag", libModel.getProjectType(), PROJECT_TYPE_LIBRARY);
        assertEquals("Project Type", PROJECT_TYPE_LIBRARY, libModel.getProjectType());

        AndroidProject appModel = models.getOnlyModelMap().get(":app");
        assertNotNull("app module model null-check", appModel);

        Variant debugVariant = AndroidProjectUtils.getVariantByName(appModel, DEBUG);

        DependencyGraphs graph = debugVariant.getMainArtifact().getDependencyGraphs();
        assertThat(helper.on(graph).withType(MODULE).mapTo(GRADLE_PATH)).containsExactly(":lib");
    }
}
