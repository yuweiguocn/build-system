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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for package (publish) local jar in libs
 */
public class LibWithPackageLocalJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();
    static ModelContainer<AndroidProject> modelContainer;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.library\"\n"
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
                        + "    runtimeOnly files(\"libs/util-1.0.jar\")\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");
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
    public void checkPackagedLocalJarIsPackaged() throws Exception {
        // search in secondary jars only.
        assertThat(project.getAar("debug"))
                .containsSecondaryClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkPackagedLocalJarIsNotInTheCompileModel() throws Exception {

        Variant variant =
                AndroidProjectUtils.getVariantByName(modelContainer.getOnlyModel(), "debug");

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();
        assertThat(helper.on(graph).withType(JAVA).asList()).isEmpty();
    }

    @Test
    public void checkPackagedLocalJarIsNotInThePackageModel() throws Exception {
        Variant variant =
                AndroidProjectUtils.getVariantByName(modelContainer.getOnlyModel(), "debug");

        DependencyGraphs dependencyGraphs = variant.getMainArtifact().getDependencyGraphs();
        assertThat(helper.on(dependencyGraphs).forPackage().withType(JAVA).asList()).hasSize(1);
    }
}
