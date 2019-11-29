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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.VARIANT;
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
import com.android.testutils.apk.Apk;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for compile library in app
 */
public class AppWithCompileLibTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "dependencies {\n" + "    api project(\":library\")\n" + "}\n");
        modelContainer = project.executeAndReturnMultiModel("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkCompiledLibraryIsPackaged() throws Exception {
        Apk apk = project.getSubproject("app").getApk("debug");

        assertThat(apk).containsClass("Lcom/example/android/multiproject/library/PersonView;");
    }

    @Test
    public void checkCompiledLibraryIsInTheModel() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        Map<String, AndroidProject> models = modelContainer.getOnlyModelMap();
        Variant variant = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();

        LibraryGraphHelper.Items subModuleItems = helper.on(graph).withType(MODULE);

        assertThat(subModuleItems.mapTo(COORDINATES))
                .named("app compile dependencies sub-modules coordinates")
                .containsExactly(project.getTestDir().getAbsolutePath() + "@@:library::debug");

        assertThat(subModuleItems.mapTo(GRADLE_PATH))
                .named("app compile dependencies sub-modules gradle-paths")
                .containsExactly(":library");

        assertThat(subModuleItems.mapTo(VARIANT))
                .named("app compile dependencies sub-modules variants")
                .containsExactly("debug");

    }
}
