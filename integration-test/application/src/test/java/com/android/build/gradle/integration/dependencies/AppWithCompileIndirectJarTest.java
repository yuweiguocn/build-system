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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for compile jar in app through an aar dependency
 */
public class AppWithCompileIndirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        appendToFile(project.getBuildFile(),
"\nsubprojects {\n" +
"    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
"}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    implementation project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\ndependencies {\n" + "    api 'com.google.guava:guava:18.0'\n" + "}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkLevel1Model() throws Exception {
        Map<String, AndroidProject> models =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                        .fetchAndroidProjects()
                        .getOnlyModelMap();

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");

        Dependencies deps = appDebug.getMainArtifact().getDependencies();

        Collection<AndroidLibrary> libs = deps.getLibraries();
        assertThat(libs).named("app androidlibrary deps count").hasSize(1);
        AndroidLibrary androidLibrary = Iterables.getOnlyElement(libs);
        assertThat(androidLibrary.getProject()).named("app androidlib deps path").isEqualTo(":library");
        assertThat(androidLibrary.getJavaDependencies()).named("app androidlib java libs count").isEmpty();

        assertThat(deps.getJavaModules()).named("app module dependency count").isEmpty();

        Collection<JavaLibrary> javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).named("app java dependency count").hasSize(1);
        //assertThat(javaLibraries).named("app java dependency count").isEmpty();

        // ---

        Variant libDebug = AndroidProjectUtils.getVariantByName(models.get(":library"), "debug");
        Truth.assertThat(libDebug).isNotNull();

        deps = libDebug.getMainArtifact().getDependencies();

        assertThat(deps.getLibraries()).named("lib androidlibrary deps count").isEmpty();

        javaLibraries = deps.getJavaLibraries();
        assertThat(javaLibraries).named("lib java dependency count").hasSize(1);
        JavaLibrary javaLib = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLib.getResolvedCoordinates())
                .named("lib java lib resolved coordinates")
                .isEqualTo("com.google.guava", "guava", "18.0");
    }

    @Test
    public void checkLevel4Model() throws Exception {
        final ModelContainer<AndroidProject> modelContainer =
                project.model().level(AndroidProject.MODEL_LEVEL_LATEST).fetchAndroidProjects();
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");

        DependencyGraphs compileGraph = appDebug.getMainArtifact().getDependencyGraphs();
        System.out.println(compileGraph.getCompileDependencies());

        // check direct dependencies
        assertThat(helper.on(compileGraph).withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("app direct module dependencies")
                .containsExactly(":library");

        //assertThat(helper.on(compileGraph).withType(JAVA).asList())
        //        .named("app direct java deps")
        //        .isEmpty();
        assertThat(helper.on(compileGraph).withType(JAVA).mapTo(Property.COORDINATES))
                .named("app direct java deps")
                .containsExactly("com.google.guava:guava:18.0@jar");

        assertThat(helper.on(compileGraph).withType(ANDROID).asList())
                .named("app direct android deps")
                .isEmpty();

        //Items libraryItems = helper.on(compileGraph).withType(MODULE).getTransitiveFromSingleItem();
        //
        //assertThat(libraryItems.withType(JAVA).mapTo(Property.COORDINATES))
        //        .named("sub-module java dependencies")
        //        .containsExactly("com.google.guava:guava:18.0@jar");

        // ---

        Variant libDebug = AndroidProjectUtils.getVariantByName(models.get(":library"), "debug");
        Truth.assertThat(libDebug).isNotNull();

        compileGraph = libDebug.getMainArtifact().getDependencyGraphs();

        assertThat(helper.on(compileGraph).withType(ANDROID).asList())
                .named(":library android dependencies")
                .isEmpty();

        assertThat(helper.on(compileGraph).withType(JAVA).mapTo(Property.COORDINATES))
                .named(":library java dependencies")
                .containsExactly("com.google.guava:guava:18.0@jar");
    }
}
