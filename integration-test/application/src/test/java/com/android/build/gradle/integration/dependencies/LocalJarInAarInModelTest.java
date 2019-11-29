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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * test for the path of the local jars in aars before and after exploding them.
 */
public class LocalJarInAarInModelTest {

    private static final String JARS_LIBS_INTERNAL_IMPL_24_0_0_JAR =
            "jars" + File.separatorChar + "libs" + File.separatorChar + "internal_impl-24.0.0.jar";
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create();

    @Before
    public void setUp() throws Exception {
        appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
                        + "\n"
                        + "android {\n"
                        + "  compileSdkVersion "
                        + DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "  buildToolsVersion \""
                        + DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "  defaultConfig {\n"
                        + "    minSdkVersion 4\n"
                        + "  }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "  api \"com.android.support:support-v4:24.0.0" // we need to use this version as later versions don't use internal jars under libs/ anymore
                        + "\"\n"
                        + "}\n");
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkAarsExplodedAfterSync_Level4() throws Exception {
        ModelContainer<AndroidProject> model = project.model().fetchAndroidProjects();
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant variant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), "debug");

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();
        LibraryGraphHelper.Items androidItems = helper.on(graph).withType(ANDROID);

        // check the model validity: making sure the folder are extracted and the local
        // jar is present.
        List<Library> libraries = androidItems.asLibraries();
        assertThat(libraries).hasSize(1);

        Library singleLibrary = Iterables.getOnlyElement(libraries);

        File rootFolder = singleLibrary.getFolder();
        assertThat(rootFolder).isDirectory();
        assertThat(new File(rootFolder, singleLibrary.getJarFile())).isFile();

        // check the local jars
        final Collection<String> localJars = singleLibrary.getLocalJars();
        assertThat(localJars).containsExactly(JARS_LIBS_INTERNAL_IMPL_24_0_0_JAR);
        assertThat(new File(rootFolder, JARS_LIBS_INTERNAL_IMPL_24_0_0_JAR)).isFile();
    }

    @Test
    public void checkAarsExplodedAfterSync_Level1() throws Exception {
        // need to test level 1 until Studio 3.0 move to level 4
        ModelContainer<AndroidProject> model =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                        .fetchAndroidProjects();

        Variant variant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), "debug");

        Dependencies deps = variant.getMainArtifact().getDependencies();

        // check the model validity: making sure the folder are extracted and the local
        // jar is present.
        Collection<AndroidLibrary> libraries = deps.getLibraries();
        assertThat(libraries).hasSize(1);

        AndroidLibrary singleLibrary = Iterables.getOnlyElement(libraries);

        File rootFolder = singleLibrary.getFolder();
        assertThat(rootFolder).isDirectory();
        assertThat(singleLibrary.getJarFile()).isFile();

        // check the local jars
        Collection<File> localJars = singleLibrary.getLocalJars();
        final File expectedLocalJarFile = new File(rootFolder, JARS_LIBS_INTERNAL_IMPL_24_0_0_JAR);
        assertThat(localJars).containsExactly(expectedLocalJarFile);
        assertThat(expectedLocalJarFile).isFile();
    }
}
