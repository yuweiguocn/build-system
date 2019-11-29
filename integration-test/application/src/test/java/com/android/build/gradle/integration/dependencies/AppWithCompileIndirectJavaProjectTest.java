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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Items;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.testutils.apk.Apk;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for compile jar in app through an aar dependency
 */
public class AppWithCompileIndirectJavaProjectTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library', 'jar'");

        appendToFile(
                project.getBuildFile(),
                "\nsubprojects {\n"
                        + "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n"
                        + "    implementation project(':library')\n"
                        + "    runtimeOnly 'com.google.guava:guava:19.0'\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\ndependencies { api project(':jar') }");

        appendToFile(
                project.getSubproject("jar").getBuildFile(),
                "\ndependencies { compile 'com.google.guava:guava:19.0' }");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkPackagedJar() throws Exception {
        project.execute("clean", ":app:assembleDebug");

        Apk apk = project.getSubproject("app").getApk("debug");

        assertThat(apk).containsClass("Lcom/example/android/multiproject/person/People;");
        assertThat(apk).containsClass("Lcom/example/android/multiproject/library/PersonView;");
    }

    @Test
    public void checkLevel1Model() throws Exception {
        ModelContainer<AndroidProject> modelContainer =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                        .fetchAndroidProjects();

        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();

        // ---
        // test the dependencies on the :app module.

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");
        Truth.assertThat(appDebug).isNotNull();

        Dependencies appDeps = appDebug.getMainArtifact().getDependencies();

        Collection<AndroidLibrary> appLibDeps = appDeps.getLibraries();
        assertThat(appLibDeps).named("app(androidlibs) count").hasSize(1);
        AndroidLibrary appAndroidLibrary = Iterables.getOnlyElement(appLibDeps);
        assertThat(appAndroidLibrary.getProject()).named("app(androidlibs[0]) project").isEqualTo(":library");

        Collection<Dependencies.ProjectIdentifier> appProjectDeps = appDeps.getJavaModules();
        assertThat(
                        appProjectDeps
                                .stream()
                                .map(Dependencies.ProjectIdentifier::getProjectPath)
                                .collect(Collectors.toList()))
                .named("app(modules) count")
                .containsExactly(":jar");

        Collection<JavaLibrary> appJavaLibDeps = appDeps.getJavaLibraries();
        assertThat(appJavaLibDeps).named("app(javalibs) count").hasSize(1);
        JavaLibrary javaLibrary = Iterables.getOnlyElement(appJavaLibDeps);
        assertThat(javaLibrary.getResolvedCoordinates())
                .isEqualTo("com.google.guava", "guava", "19.0", "jar", null);

        // ---
        // test the dependencies on the :library module.

        Variant libDebug = AndroidProjectUtils.getVariantByName(models.get(":library"), "debug");
        Truth.assertThat(libDebug).isNotNull();

        Dependencies libDeps = libDebug.getMainArtifact().getDependencies();

        assertThat(libDeps.getLibraries()).named("lib(androidlibs) count").isEmpty();

        Collection<Dependencies.ProjectIdentifier> libProjectDeps = libDeps.getJavaModules();
        assertThat(libProjectDeps).named("lib(modules) count").hasSize(1);
        String libProjectDep = Iterables.getOnlyElement(libProjectDeps).getProjectPath();
        assertThat(libProjectDep).named("lib->:jar project").isEqualTo(":jar");

        Collection<JavaLibrary> libJavaLibDeps = appDeps.getJavaLibraries();
        assertThat(libJavaLibDeps).named("lib(javalibs) count").hasSize(1);
        javaLibrary = Iterables.getOnlyElement(libJavaLibDeps);
        assertThat(javaLibrary.getResolvedCoordinates())
                .isEqualTo("com.google.guava", "guava", "19.0", "jar", null);
    }

    @Test
    public void checkLevel4Model() throws Exception {
        String rootProjectId = project.getTestDir().getAbsolutePath() + "@@";

        ModelContainer<AndroidProject> modelContainer =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_LATEST)
                        .withFullDependencies()
                        .fetchAndroidProjects();

        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        // ---
        // test full transitive dependencies from the :app module.

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");
        Truth.assertThat(appDebug).isNotNull();

        DependencyGraphs dependencyGraph = appDebug.getMainArtifact().getDependencyGraphs();

        {

            // no direct android library
            assertThat(helper.on(dependencyGraph).withType(ANDROID).asList())
                    .named(":app compile Android")
                    .isEmpty();

            // no direct java library
            assertThat(helper.on(dependencyGraph).withType(JAVA).mapTo(COORDINATES))
                    .named(":app compile Java")
                    .containsExactly("com.google.guava:guava:19.0@jar");

            // look at direct modules
            Items moduleItems = helper.on(dependencyGraph).withType(MODULE);

            // should depend on :library
            // For now it contains all the transitive in a single list, so also :jar
            assertThat(moduleItems.mapTo(Property.COORDINATES))
                    .named(":app compile modules")
                    .containsExactly(rootProjectId + ":library::debug", rootProjectId + ":jar");

            // once there is a true graph, change this to false
            if (true) {
                // check there's not transitive info in the elements.
                List<GraphItem> libs = moduleItems.asList();
                for (GraphItem lib : libs) {
                    assertThat(lib.getDependencies()).isEmpty();
                }
            } else {
                Items libraryItems = moduleItems.getTransitiveFromSingleItem();

                // now look at the transitive dependencies of this item
                assertThat(libraryItems.withType(ANDROID).asList())
                        .named(":app->:lib compile Android")
                        .isEmpty();

                // no direct java library
                assertThat(libraryItems.withType(JAVA).asList())
                        .named(":app->:lib compile Java")
                        .isEmpty();

                // look at direct module
                Items librarySubModuleItems = libraryItems.withType(MODULE);

                // should depend on :jar
                assertThat(librarySubModuleItems.mapTo(Property.GRADLE_PATH))
                        .named(":app compile modules")
                        .containsExactly(":jar");

                // follow the transitive dependencies again
                Items libraryToJarItems = librarySubModuleItems.getTransitiveFromSingleItem();

                // no direct android library
                assertThat(libraryToJarItems.withType(ANDROID).asList())
                        .named(":app->:lib->:jar compile Android")
                        .isEmpty();

                // no direct module dep
                assertThat(libraryToJarItems.withType(MODULE).asList())
                        .named(":app->:lib->:jar compile module")
                        .isEmpty();

                assertThat(libraryToJarItems.withType(JAVA).mapTo(COORDINATES))
                        .named(":app->:lib->:jar compile java")
                        .containsExactly("com.google.guava:guava:19.0@jar");
            }
        }

        // same thing with the package deps. Main difference is guava available as direct
        // dependencies and transitive one is promoted
        {
            Items packageItems = helper.on(dependencyGraph).forPackage();

            // no direct android library
            assertThat(packageItems.withType(ANDROID).asList())
                    .named(":app package Android")
                    .isEmpty();

            // dependency on guava.
            assertThat(packageItems.withType(JAVA).mapTo(COORDINATES))
                    .named(":app package Java")
                    .containsExactly("com.google.guava:guava:19.0@jar");

            // look at direct module
            Items moduleItems = packageItems.withType(MODULE);

            // should depend on :library
            // For now it contains all the transitive in a single list, so also :jar
            assertThat(moduleItems.mapTo(Property.COORDINATES))
                    .named(":app package modules")
                    .containsExactly(rootProjectId + ":library::debug", rootProjectId + ":jar");

            // once there is a true graph, change this to false
            if (true) {
                // check there's not transitive info in the elements.
                List<GraphItem> libs = moduleItems.asList();
                for (GraphItem lib : libs) {
                    assertThat(lib.getDependencies()).isEmpty();
                }
            } else {
                // now look at the transitive dependencies of this item
                Items libraryItems = moduleItems.getTransitiveFromSingleItem();

                // no direct android lib
                assertThat(libraryItems.withType(ANDROID).asList())
                        .named(":app->:lib package Android")
                        .isEmpty();

                // no direct java library
                assertThat(libraryItems.withType(JAVA).asList())
                        .named(":app->:lib package Java")
                        .isEmpty();

                // look at direct module
                Items librarySubModuleItems = libraryItems.withType(MODULE);

                // should depend on :jar
                assertThat(librarySubModuleItems.mapTo(Property.GRADLE_PATH))
                        .named(":app->:lib package modules")
                        .containsExactly(":jar");

                // follow the transitive dependencies again
                Items libraryToJarItems = librarySubModuleItems.getTransitiveFromSingleItem();

                // no direct android library
                assertThat(libraryToJarItems.withType(ANDROID).asList())
                        .named(":app->:lib->:jar package Android")
                        .isEmpty();

                // no direct module dep
                assertThat(libraryToJarItems.withType(MODULE).asList())
                        .named(":app->:lib->:jar package module")
                        .isEmpty();

                assertThat(libraryToJarItems.withType(JAVA).mapTo(COORDINATES))
                        .named(":app->:lib->:jar package java")
                        .containsExactly("com.google.guava:guava:19.0@jar");
            }
        }

        // ---
        // test full transitive dependencies from the :library module.
        {
            Variant libDebug =
                    AndroidProjectUtils.getVariantByName(models.get(":library"), "debug");
            Truth.assertThat(libDebug).isNotNull();

            DependencyGraphs compileGraph = libDebug.getMainArtifact().getDependencyGraphs();

            // no direct android library
            assertThat(helper.on(compileGraph).withType(ANDROID).asList())
                    .named(":lib compile Android")
                    .isEmpty();

            // no direct java library
            // Right now guava shows up directly due to lack for graph
            assertThat(helper.on(compileGraph).withType(JAVA).mapTo(COORDINATES))
                    .named(":lib compile Java")
                    .containsExactly("com.google.guava:guava:19.0@jar");

            // look at direct module
            Items moduleItems = helper.on(compileGraph).withType(MODULE);

            // should depend on :jar
            assertThat(moduleItems.mapTo(Property.GRADLE_PATH))
                    .named(":lib compile modules")
                    .containsExactly(":jar");

            // follow the transitive dependencies again
            Items jarItems = moduleItems.getTransitiveFromSingleItem();


            // once there is a true graph, change this to false
            if (true) {
                assertThat(jarItems.asLibraries()).isEmpty();
            } else {
                // no direct android library
                assertThat(jarItems.withType(ANDROID).asList())
                        .named(":lib->:jar compile Android")
                        .isEmpty();

                // no direct module dep
                assertThat(jarItems.withType(MODULE).asList())
                        .named(":lib->:jar compile module")
                        .isEmpty();

                assertThat(jarItems.withType(JAVA).mapTo(COORDINATES))
                        .named(":lib->:jar compile java")
                        .containsExactly("com.google.guava:guava:19.0@jar");
            }
        }
    }
}
