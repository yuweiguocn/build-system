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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for a dependency on a local jar through a module wrapper
 */
public class DepOnLocalJarThroughAModuleTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> models;

    @BeforeClass
    public static void setUp() throws Exception {
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    api project(\":localJarAsModule\")\n"
                        + "}\n");
        models = project.executeAndReturnMultiModel("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkJarIsPackaged() throws Exception {
        assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkJarModuleIsInTheTestArtifactModel() throws Exception {
        final AndroidProject androidProject = models.getOnlyModelMap().get(":app");
        Variant variant = AndroidProjectUtils.getVariantByName(androidProject, "debug");

        LibraryGraphHelper helper = new LibraryGraphHelper(models);

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();

        assertThat(helper.on(graph).withType(JAVA).asList())
                .named("app java dependencies")
                .isEmpty();
        assertThat(helper.on(graph).withType(ANDROID).asList())
                .named("app android dependencies")
                .isEmpty();
        assertThat(helper.on(graph).withType(MODULE).mapTo(GRADLE_PATH))
                .named("app module dependencies")
                .containsExactly(":localJarAsModule");
    }
}
