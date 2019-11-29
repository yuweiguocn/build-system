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
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Assemble tests for multiproject.
 */
public class MultiProjectTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("multiproject")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        modelContainer = project.model().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();

        AndroidProject baseLibModel = models.get(":baseLibrary");
        assertThat(baseLibModel).named("Module app").isNotNull();

        Collection<Variant> variants = baseLibModel.getVariants();
        assertThat(variants).named("variant list").hasSize(2);

        Variant variant = AndroidProjectUtils.getVariantByName(baseLibModel, "release");
        assertThat(variant).named("release variant").isNotNull();

        //noinspection ConstantConditions
        AndroidArtifact mainArtifact = variant.getMainArtifact();
        assertThat(mainArtifact).named("release main artifact").isNotNull();

        DependencyGraphs compileGraph = mainArtifact.getDependencyGraphs();
        assertThat(compileGraph).named("release main artifact graph").isNotNull();

        assertThat(helper.on(compileGraph).withType(MODULE).mapTo(GRADLE_PATH))
                .named("release sub-module dependencies")
                .containsExactly(":util");
    }
}
