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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
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
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for package (apk) jar in app
 */
public class AppWithPackageDirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8).write("include 'app', 'jar'");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "dependencies {\n" + "    runtimeOnly project(\":jar\")\n" + "}\n");
        project.execute("clean", ":app:assembleDebug");
        modelContainer = project.model().withFullDependencies().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkPackageJarIsPackaged() throws Exception {
        assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkPackagedJarIsNotInTheCompileModel() throws Exception {
        Variant variant =
                AndroidProjectUtils.getVariantByName(
                        modelContainer.getOnlyModelMap().get(":app"), "debug");
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        DependencyGraphs compileGraph = variant.getMainArtifact().getDependencyGraphs();
        assertThat(helper.on(compileGraph).withType(MODULE).asList())
                .named("app sub-module compile deps")
                .isEmpty();
        assertThat(helper.on(compileGraph).withType(JAVA).asList())
                .named("app java compile deps")
                .isEmpty();
    }

    @Test
    public void checkPackagedJarIsInThePackageModel() throws Exception {
        Variant variant =
                AndroidProjectUtils.getVariantByName(
                        modelContainer.getOnlyModelMap().get(":app"), "debug");
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        DependencyGraphs dependencyGraph = variant.getMainArtifact().getDependencyGraphs();
        LibraryGraphHelper.Items packageItems = helper.on(dependencyGraph).forPackage();

        assertThat(packageItems.withType(MODULE).mapTo(COORDINATES))
                .named("app sub-module package deps")
                .containsExactly(project.getTestDir().getAbsolutePath() + "@@:jar");
        assertThat(packageItems.withType(JAVA).asList())
                .named("app java package deps")
                .isEmpty();
    }

}
