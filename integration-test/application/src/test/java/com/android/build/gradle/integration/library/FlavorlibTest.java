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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.VARIANT;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Assemble tests for flavorlib.
 */
public class FlavorlibTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("flavorlib")
            .create();
    public static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        modelContainer = project.executeAndReturnMultiModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void lint() throws Exception {
        project.execute("lint");
    }

    @Test
    public void report() throws Exception {
        project.execute("androidDependencies", "signingReport");
    }

    @Test
    public void checkExplodedAar() throws Exception {
        File intermediates = FileUtils.join(project.getTestDir(), "app", "build", "intermediates");
        assertThat(intermediates).isDirectory();
        assertThat(new File(intermediates, "exploded-aar")).doesNotExist();
    }

    @Test
    public void testModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();

        AndroidProject appModel = models.get(":app");
        assertThat(appModel).named("app model").isNotNull();

        assertThat(appModel.isLibrary()).named("App isLibrary()").isFalse();
        assertThat(appModel.getProjectType()).named("App Project Type").isEqualTo(AndroidProject.PROJECT_TYPE_APP);

        // query for presence check
        AndroidProjectUtils.getProductFlavor(appModel, "flavor1");

        validateVariant(appModel, "flavor1Debug", ":lib1", "debug", helper);
        validateVariant(appModel, "flavor2Debug", ":lib2", "debug", helper);

        // query for presence check
        AndroidProjectUtils.getProductFlavor(appModel, "flavor2");

        validateVariant(appModel, "flavor1Release", ":lib1", "release", helper);
        validateVariant(appModel, "flavor2Release", ":lib2", "release", helper);
    }

    private void validateVariant(
            @NonNull AndroidProject androidProject,
            String variantName,
            String depModuleName,
            String depVariantName,
            LibraryGraphHelper helper) {
        Variant variant = AndroidProjectUtils.getVariantByName(androidProject, variantName);

        DependencyGraphs dependencyGraphs = variant.getMainArtifact().getDependencyGraphs();
        assertThat(dependencyGraphs).named(variantName + " dependency graph").isNotNull();

        LibraryGraphHelper.Items subModules = helper.on(dependencyGraphs).withType(MODULE);

        assertThat(subModules.mapTo(GRADLE_PATH))
                .named(variantName + " sub-modules as gradle-path")
                .containsExactly(depModuleName);
        assertThat(subModules.mapTo(VARIANT))
                .named(variantName + " sub-modules as variant name")
                .containsExactly(depVariantName);
    }
}
