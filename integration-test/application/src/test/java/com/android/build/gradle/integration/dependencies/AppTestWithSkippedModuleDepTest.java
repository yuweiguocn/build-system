/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Items;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.testutils.apk.Apk;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for app with a sub-module as both a compile and androidTestCompile dependency.
 */
public class AppTestWithSkippedModuleDepTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    private static ModelContainer<AndroidProject> modelContainer;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8).write("include 'app', 'jar'");

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n"
                        + "    api project(':jar')\n"
                        + "    androidTestImplementation project(':jar')\n"
                        + "}\n");

        project.execute("clean", ":app:assembleDebug", ":app:assembleAndroidTest");
        modelContainer = project.model().withFullDependencies().fetchAndroidProjects();
        helper = new LibraryGraphHelper(modelContainer);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
        helper = null;
    }

    @Test
    public void checkAppBuild() throws Exception {
        Apk appApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(appApk).exists();
        assertThat(appApk)
                .containsClass("Lcom/example/android/multiproject/person/Person;");
    }

    @Test
    public void checkTestBuild() throws Exception {
        Apk testApk =
                project.getSubproject(":app").getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
        assertThat(testApk).exists();
        assertThat(testApk)
                .doesNotContainClass("Lcom/example/android/multiproject/person/Person;");
    }

    @Test
    public void checkLevel2AppModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");
        DependencyGraphs dependencyGraphs = appDebug.getMainArtifact().getDependencyGraphs();

        // --- Compile Graph
        Items compileItems = helper.on(dependencyGraphs);

        // check direct dependencies
        assertThat(compileItems.withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("app direct compile module dependencies")
                .containsExactly(":jar");

        assertThat(compileItems.withType(JAVA).asList())
                .named("app direct compile java deps")
                .isEmpty();

        assertThat(compileItems.withType(ANDROID).asList())
                .named("app direct compile android deps")
                .isEmpty();

        // --- package Graph
        // FIXME once we have full graphs
        //Items packageItems = helper.on(dependencyGraphs).forPackage();
        //
        //// check direct dependencies
        //assertThat(packageItems.withType(MODULE).mapTo(Property.GRADLE_PATH))
        //        .named("app direct package module dependencies")
        //        .containsExactly(":jar");
        //
        //assertThat(packageItems.withType(JAVA).asList())
        //        .named("app direct package java deps")
        //        .isEmpty();
        //
        //assertThat(packageItems.withType(ANDROID).asList())
        //        .named("app direct package android deps")
        //        .isEmpty();
        //
        //// --- skipped/provided states
        //assertThat(dependencyGraphs.getSkippedLibraries())
        //        .named("package skipped libraries")
        //        .isEmpty();
        //assertThat(dependencyGraphs.getProvidedLibraries())
        //        .named("compile provided libraries")
        //        .isEmpty();
    }

    @Test
    public void checkLevel2TestModel() throws Exception {

        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");

        AndroidArtifact testArtifact = VariantUtils.getAndroidTestArtifact(appDebug);

        DependencyGraphs dependencyGraphs = testArtifact.getDependencyGraphs();

        // --- compile Graph
        Items compileItems = helper.on(dependencyGraphs);

        // check direct dependencies
        assertThat(compileItems.withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("app direct compile module dependencies")
                .containsExactly(":jar");

        assertThat(compileItems.withType(JAVA).asList())
                .named("app direct compile java deps")
                .isEmpty();

        assertThat(compileItems.withType(ANDROID).asList())
                .named("app direct compile android deps")
                .isEmpty();

        // --- package Graph
        // FIXME once we have full graphs
        //Items packageItems = helper.on(dependencyGraphs).forPackage();
        //
        //// check direct dependencies
        //assertThat(packageItems.withType(MODULE).mapTo(Property.GRADLE_PATH))
        //        .named("app direct package module dependencies")
        //        .containsExactly(":jar");
        //
        //assertThat(packageItems.withType(JAVA).asList())
        //        .named("app direct package java deps")
        //        .isEmpty();
        //
        //assertThat(packageItems.withType(ANDROID).asList())
        //        .named("app direct package android deps")
        //        .isEmpty();
        //
        //// --- provided/skipppd state
        //assertThat(dependencyGraphs.getSkippedLibraries())
        //        .named("package skipped libraries")
        //        .containsExactly(":jar");
        //assertThat(dependencyGraphs.getProvidedLibraries())
        //        .named("compile provided libraries")
        //        .isEmpty();
    }
}
