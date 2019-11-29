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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.testutils.truth.FileSubject.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import java.io.File;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for same dependency with and without classifier.
 */
public class AppWithClassifierDepTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithClassifierDep")
            .create();
    public static ModelContainer<AndroidProject> model;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() throws Exception {
        model = project.model().fetchAndroidProjects();
        helper = new LibraryGraphHelper(model);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
        helper = null;
    }

    @Test
    public void checkDebugDepInModel() throws Exception {
        Variant variant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), "debug");

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();

        LibraryGraphHelper.Items javaItems = helper.on(graph).withType(JAVA);
        assertThat(javaItems.mapTo(COORDINATES)).containsExactly("com.foo:sample:1.0@jar");

        Library library = javaItems.asSingleLibrary();
        assertThat(library.getArtifact())
                .named("jar location")
                .isEqualTo(new File(
                        project.getTestDir(), "repo/com/foo/sample/1.0/sample-1.0.jar"));
    }

    @Test
    public void checkAndroidTestDepInModel() throws Exception {
        Variant debugVariant = AndroidProjectUtils.getVariantByName(model.getOnlyModel(), "debug");

        AndroidArtifact androidTestArtifact = VariantUtils.getAndroidTestArtifact(debugVariant);

        DependencyGraphs graph = androidTestArtifact.getDependencyGraphs();

        LibraryGraphHelper.Items javaItems = helper.on(graph).withType(JAVA);
        assertThat(javaItems.mapTo(COORDINATES))
                .containsExactly("com.foo:sample:1.0:testlib@jar", "com.foo:sample:1.0@jar");

        List<Library> libraries = javaItems.asLibraries();

        Library library = getLibraryByCoordinate(libraries, "com.foo:sample:1.0:testlib@jar");
        assertThat(library.getArtifact())
                .named("jar location")
                .isEqualTo(new File(
                        project.getTestDir(),
                        "repo/com/foo/sample/1.0/sample-1.0-testlib.jar"));

        library = getLibraryByCoordinate(libraries, "com.foo:sample:1.0@jar");
        assertThat(library.getArtifact())
                .named("jar location")
                .isEqualTo(
                        new File(project.getTestDir(), "repo/com/foo/sample/1.0/sample-1.0.jar"));
    }

    @NonNull
    private static Library getLibraryByCoordinate(
            @NonNull List<Library> libraries, @NonNull String coordinates) {
        for (Library library : libraries) {
            if (library.getArtifactAddress().equals(coordinates)) {
                return library;
            }
        }

        fail("Failed to find library matching: " + coordinates);
        return null;
    }
}
