/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.testutils.apk.Aar;
import com.android.testutils.apk.Apk;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for optional aar (using the provided scope)
 */
public class OptionalAarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> models;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library', 'library2'");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "\n" + "dependencies {\n" + "    api project(\":library\")\n" + "}\n");
        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compileOnly project(\":library2\")\n"
                        + "}\n");

        // build the app and need to build the aar separately since building the app
        // doesn't build the aar anymore.

        project.execute("clean", ":app:assembleDebug", "library:assembleDebug");
        models = project.model().withFullDependencies().fetchAndroidProjects();
        helper = new LibraryGraphHelper(models);

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
        helper = null;
    }

    @Test
    public void checkAppDoesNotContainProvidedLibsLayout() throws Exception {
        Apk apk = project.getSubproject("app").getApk("debug");

        assertThatApk(apk).doesNotContainResource("layout/lib2layout.xml");
    }

    @Test
    public void checkAppDoesNotContainProvidedLibsCode() throws Exception {
        Apk apk = project.getSubproject("app").getApk("debug");

        assertThatApk(apk).doesNotContainClass("Lcom/example/android/multiproject/library2/PersonView2;");
    }

    @Test
    public void checkLibDoesNotContainProvidedLibsLayout() throws Exception {
        Aar aar = project.getSubproject("library").getAar("debug");

        assertThatAar(aar).doesNotContainResource("layout/lib2layout.xml");
        assertThatAar(aar).textSymbolFile().contains("int layout liblayout");
        assertThatAar(aar).textSymbolFile().doesNotContain("int layout lib2layout");
    }

    @Test
    public void checkAppModelDoesNotIncludeOptionalLibrary() throws Exception {
        final AndroidProject androidProject = models.getOnlyModelMap().get(":app");

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = AndroidProjectUtils.getVariantByName(androidProject, "debug");

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();

        LibraryGraphHelper.Items moduleItems = helper.on(graph).withType(MODULE);
        assertThat(moduleItems.mapTo(GRADLE_PATH)).containsExactly(":library");
        // nothing is marked as provided
        assertThat(graph.getProvidedLibraries()).isEmpty();

        GraphItem libraryItem = moduleItems.asSingleGraphItem();
        assertThat(libraryItem.getDependencies()).isEmpty();

    }

    @Test
    public void checkLibraryModelIncludesOptionalLibrary() throws Exception {
        final AndroidProject androidProject = models.getOnlyModelMap().get(":library");

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = AndroidProjectUtils.getVariantByName(androidProject, "debug");
        AndroidArtifact mainArtifact = variant.getMainArtifact();

        DependencyGraphs dependencyGraph = mainArtifact.getDependencyGraphs();
        LibraryGraphHelper.Items moduleItems = helper.on(dependencyGraph).withType(MODULE);
        assertThat(moduleItems.mapTo(GRADLE_PATH)).containsExactly(":library2");
        assertThat(dependencyGraph.getProvidedLibraries())
                .containsExactly(moduleItems.asSingleGraphItem().getArtifactAddress());

        assertThat(dependencyGraph.getPackageDependencies()).isEmpty();
    }
}
