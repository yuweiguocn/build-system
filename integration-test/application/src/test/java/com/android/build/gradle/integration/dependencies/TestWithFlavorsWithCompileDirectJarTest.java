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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidArtifact;
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
 * test for compile jar in a test app
 */
public class TestWithFlavorsWithCompileDirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> models;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8).write("include 'app', 'jar'");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "      pro { }\n"
                        + "      free { }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation project(\":jar\")\n"
                        + "}\n");
        models = project.executeAndReturnMultiModel("clean", ":app:assembleFreeDebugAndroidTest");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkCompiledJarIsPackaged() throws Exception {
        assertThat(project.getSubproject("app").getTestApk("free"))
                .containsClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkCompiledJarIsInTheTestArtifactModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(models);
        Variant variant =
                AndroidProjectUtils.getVariantByName(
                        models.getOnlyModelMap().get(":app"), "freeDebug");

        AndroidArtifact testArtifact = VariantUtils.getAndroidTestArtifact(variant);

        DependencyGraphs graph = testArtifact.getDependencyGraphs();
        assertThat(helper.on(graph).withType(MODULE).mapTo(GRADLE_PATH)).containsExactly(":jar");
    }
}
