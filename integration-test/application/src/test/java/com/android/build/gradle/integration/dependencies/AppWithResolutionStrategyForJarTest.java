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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Items;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for flavored dependency on a different package.
 */
public class AppWithResolutionStrategyForJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> models;
    static LibraryGraphHelper helper;


    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        appendToFile(project.getBuildFile(),
                "\n" +
                "subprojects {\n" +
                "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
                "}\n");
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n"
                        + "\n"
                        + "configurations { debugCompileClasspath }\n"
                        + "\n"
                        + "configurations.debugCompileClasspath {\n"
                        + "  resolutionStrategy {\n"
                        + "    eachDependency { DependencyResolveDetails details ->\n"
                        + "      if (details.requested.name == \"guava\") {\n"
                        + "        details.useVersion \"18.0\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "\n");

        TestFileUtils.appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n" + "dependencies {\n" + "    api \"com.google.guava:guava:19.0\"\n" + "}\n");

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
    public void checkModelContainsCorrectDependencies() throws Exception {

        AndroidProject appProject = models.getOnlyModelMap().get(":app");

        Variant debugVariant = AndroidProjectUtils.getVariantByName(appProject, "debug");

        Items items = helper.on(debugVariant.getMainArtifact().getDependencyGraphs());
        checkJarDependency(items, "18.0", "debug");
        checkJarDependency(items.forPackage(), "19.0", "debug");

        Variant releaseVariant = AndroidProjectUtils.getVariantByName(appProject, "release");
        Truth.assertThat(releaseVariant).isNotNull();

        items = helper.on(releaseVariant.getMainArtifact().getDependencyGraphs());
        checkJarDependency(items, "19.0", "release");
        checkJarDependency(items.forPackage(), "19.0", "release");
    }

    private static void checkJarDependency(
            @NonNull Items items,
            @NonNull String jarVersion,
            @NonNull String variantName) {

        assertThat(items.mapTo(COORDINATES))
                .named("Direct modules of " + variantName)
                .containsExactly(
                        project.getTestDir().getAbsolutePath() + "@@:library::" + variantName,
                        "com.google.guava:guava:" + jarVersion + "@jar");
    }
}
