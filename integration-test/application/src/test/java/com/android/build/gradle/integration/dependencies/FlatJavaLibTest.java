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

import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_MIN_SDK;
import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for flattened java lib dependency in the module.
 *
 * This verifies that duplicated external java libraries are de-duped in the model when querying
 * the model with a level < AndroidProject.MODEL_LEVEL_2_DEP_GRAPH
 */
public class FlatJavaLibTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> models;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8).write("include 'app'");

        /*
         * These two dependencies will transitively bring in 9 counts of support-annotations.
         */
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion "
                        + SUPPORT_LIB_MIN_SDK
                        + "\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:appcompat-v7:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    api 'com.android.support:design:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");
        models =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                        .fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkDeDupedExternalJavaLibraries() throws Exception {
        final AndroidProject androidProject = models.getOnlyModelMap().get(":app");
        Variant variant = AndroidProjectUtils.getVariantByName(androidProject, "debug");

        Dependencies deps = variant.getMainArtifact().getDependencies();

        // check we only have one version of support-annotations.
        Collection<JavaLibrary> javaLibraries =
                deps.getJavaLibraries()
                        .stream()
                        .filter(
                                it ->
                                        it.getResolvedCoordinates()
                                                .getArtifactId()
                                                .equals("support-annotations"))
                        .collect(Collectors.toList());
        assertThat(javaLibraries).hasSize(1);
        assertThat(Iterables.getOnlyElement(javaLibraries).getResolvedCoordinates())
                .isEqualTo("com.android.support", "support-annotations", SUPPORT_LIB_VERSION);
    }
}
