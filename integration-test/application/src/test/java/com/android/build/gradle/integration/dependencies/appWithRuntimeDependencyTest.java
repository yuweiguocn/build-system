/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for runtime only dependencies. Test project structure: app -> library (implementation) ----
 * library -> library2 (implementation) ---- library -> guava (implementation) The test verifies
 * that the dependency model of app module contains library2 and guava as runtime only dependencies.
 */
public class appWithRuntimeDependencyTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @BeforeClass
    public static void setUp() throws Exception {
        //noinspection deprecation
        Files.write(
                "include 'app', 'library', 'library2'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    implementation project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\ndependencies {\n"
                        + "    implementation project(':library2')\n"
                        + "    implementation 'com.google.guava:guava:19.0'\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkRuntimeClasspathWithLevel1Model() throws Exception {
        Map<String, AndroidProject> models =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                        .fetchAndroidProjects()
                        .getOnlyModelMap();

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");

        Dependencies deps = appDebug.getMainArtifact().getDependencies();

        // Verify that app has one AndroidLibrary dependency, :library.
        Collection<AndroidLibrary> libs = deps.getLibraries();
        assertThat(libs).named("app android library deps count").hasSize(1);
        assertThat(Iterables.getOnlyElement(libs).getProject())
                .named("app android library deps path")
                .isEqualTo(":library");

        // Verify that app doesn't have module dependency.
        assertThat(deps.getJavaModules()).named("app module dependency count").isEmpty();

        // Verify that app doesn't have JavaLibrary dependency.
        assertThat(deps.getJavaLibraries()).named("app java dependency count").isEmpty();

        // Verify that app has runtime only dependencies on :library2 and guava.
        Collection<String> runtimeOnlyClasses =
                deps.getRuntimeOnlyClasses()
                        .stream()
                        .map(File::getPath)
                        .collect(Collectors.toList());
        assertThat(runtimeOnlyClasses).hasSize(2);

        File outputJar =
                project.getSubproject(":library2")
                        .file("build/intermediates/full_jar/debug/createFullJarDebug/full.jar");
        assertThat(runtimeOnlyClasses).contains(outputJar.getPath());
        assertThat(runtimeOnlyClasses.stream().anyMatch(it -> it.endsWith("guava-19.0.jar")))
                .isTrue();
    }
}
