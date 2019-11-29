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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for provided java submodule in app
 */
public class AppWithProvidedProjectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8).write("include 'app', 'jar'");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "dependencies {\n" + "    compileOnly project(\":jar\")\n" + "}\n");

        project.execute("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkProvidedJarIsNotPackaged() throws Exception {
        assertThat(project.getSubproject("app").getApk("debug"))
                .doesNotContainClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkProvidedJarIsInTheMainArtifactDependency() throws Exception {
        ModelContainer<AndroidProject> modelContainer =
                project.model().withFullDependencies().fetchAndroidProjects();

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Variant variant =
                AndroidProjectUtils.getVariantByName(
                        modelContainer.getOnlyModelMap().get(":app"), "debug");

        DependencyGraphs dependencyGraph = variant.getMainArtifact().getDependencyGraphs();

        // assert that there is one sub-module dependency
        assertThat(helper.on(dependencyGraph).withType(MODULE).asList())
                .named("Module dependencies")
                .hasSize(1);
        // and that it's provided
        GraphItem javaItem = helper.on(dependencyGraph).withType(MODULE).asSingleGraphItem();
        assertThat(dependencyGraph.getProvidedLibraries())
                .named("compile provided list")
                .containsExactly(javaItem.getArtifactAddress());

        // check that the package graph does not contain the item (or anything else)
        assertThat(dependencyGraph.getPackageDependencies())
                .named("package dependencies")
                .isEmpty();
    }
}
