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
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import com.google.common.truth.Truth;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for package (apk) local aar in app
 */
public class AppWithCompileLocalAarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
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
                        + "    implementation files(\"libs/baseLib-1.0.aar\")\n"
                        + "}\n");

        modelContainer = project.model().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkModelContainsLocalAar() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        AndroidProject model = modelContainer.getOnlyModel();

        Variant debug = AndroidProjectUtils.getVariantByName(model, "debug");
        Truth.assertThat(debug).isNotNull();

        DependencyGraphs dependencyGraph = debug.getMainArtifact().getDependencyGraphs();

        final LibraryGraphHelper.Items androidItems = helper.on(dependencyGraph).withType(ANDROID);
        assertThat(androidItems.asList())
                .named("compileClasspath with Android filter")
                .hasSize(1);

        Library library = androidItems.asSingleLibrary();
        assertThat(library.getArtifactAddress()).startsWith("__local_aars__:");
    }
}
