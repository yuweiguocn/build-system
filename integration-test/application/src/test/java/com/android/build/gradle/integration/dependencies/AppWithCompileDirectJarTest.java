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
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
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
 * test for compile jar in app
 */
public class AppWithCompileDirectJarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> models;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8).write("include 'app', 'jar'");

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "dependencies {\n" + "    implementation project(':jar')\n" + "}\n");
        models = project.executeAndReturnMultiModel("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkCompiledJarIsPackaged() throws Exception {
        assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/person/People;");
    }

    @Test
    public void checkCompiledJarIsInTheModel() throws Exception {
        Variant variant =
                AndroidProjectUtils.getVariantByName(models.getOnlyModelMap().get(":app"), "debug");

        DependencyGraphs compileGraph = variant.getMainArtifact().getDependencyGraphs();
        LibraryGraphHelper helper = new LibraryGraphHelper(models);

        assertThat(helper.on(compileGraph)
                .withType(LibraryGraphHelper.Type.MODULE)
                .mapTo(Property.GRADLE_PATH))
                .named("java sub-modules")
                .containsExactly(":jar");
    }
}
