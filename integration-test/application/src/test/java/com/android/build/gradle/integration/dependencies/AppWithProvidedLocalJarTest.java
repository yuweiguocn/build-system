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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for provided local jar in app
 */
public class AppWithProvidedLocalJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compileOnly files(\"libs/util-1.0.jar\")\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkProvidedLocalJarIsNotPackaged() throws Exception {
        assertThat(project.getApk("debug"))
                .doesNotContainClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkBasicModel() throws Exception {
        ModelContainer<AndroidProject> model = project.model().fetchAndroidProjects();
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant variant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), "debug");

        DependencyGraphs dependencyGraph = variant.getMainArtifact().getDependencyGraphs();

        checkCompileDeps(helper, dependencyGraph);
    }

    @Test
    public void checkFullModel() throws Exception {
        ModelContainer<AndroidProject> model =
                project.model().withFullDependencies().fetchAndroidProjects();
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant variant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), "debug");

        DependencyGraphs dependencyGraph = variant.getMainArtifact().getDependencyGraphs();
        checkCompileDeps(helper, dependencyGraph);

        // check that the package graph does not contain the item (or anything else)
        assertThat(dependencyGraph.getPackageDependencies())
                .named("package dependencies")
                .isEmpty();
    }

    private void checkCompileDeps(LibraryGraphHelper helper, DependencyGraphs dependencyGraph) {
        // assert that there is one java library dependency
        assertThat(helper.on(dependencyGraph).withType(JAVA).asList())
                .named("Java Library dependencies")
                .hasSize(1);
        // and that it's provided
        GraphItem javaItem = helper.on(dependencyGraph).withType(JAVA).asSingleGraphItem();
        assertThat(dependencyGraph.getProvidedLibraries())
                .named("compile provided list")
                .containsExactly(javaItem.getArtifactAddress());
    }

}
