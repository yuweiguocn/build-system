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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.build.gradle.integration.instant.InstantRunTestUtils.getInstantRunModel;
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.android.utils.FileUtils.mkdirs;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.tools.ir.client.InstantRunArtifact;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Hot swap test for sub projects.
 */
public class LibDependencyTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("libDependency")
                    .create();

    @Before
    public void activityClass() throws Exception {
        createLibraryClass("Before");
    }

    @Test
    public void buildIncrementallyWithInstantRun() throws Exception {
        project.execute("clean");
        ModelContainer<AndroidProject> modelContainer = project.model().fetchAndroidProjects();
        InstantRun instantRunModel =
                getInstantRunModel(modelContainer.getOnlyModelMap().get(":app"));

        // Check that original class is included.
        project.executor()
                .withInstantRun(new AndroidVersion(23, null))
                .run("clean", "assembleRelease", "assembleDebug");

        assertThat(InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel))
                .hasClass("Lcom/android/tests/libstest/lib/MainActivity;")
                .that()
                .hasMethod("onCreate");

        checkHotSwapCompatibleChange(instantRunModel);
    }

    @Test
    public void hotSwapChangeInJavaLibrary() throws Exception {
        appendToFile(project.file("settings.gradle"), "\n"
                + "include 'javalib'\n");
        appendToFile(
                project.file("lib/build.gradle"),
                "\n" + "dependencies {\n" + "    api project(':javalib')\n" + "}\n");
        mkdirs(project.file("javalib"));
        Files.asCharSink(project.file("javalib/build.gradle"), Charsets.UTF_8)
                .write(
                        "apply plugin: 'java'\n"
                                + "targetCompatibility = '1.6'\n"
                                + "sourceCompatibility = '1.6'\n");
        createJavaLibraryClass("original");

        ModelContainer<AndroidProject> modelContainer = project.model().fetchAndroidProjects();
        InstantRun instantRunModel =
                getInstantRunModel(modelContainer.getOnlyModelMap().get(":app"));
        project.executor()
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                .run("clean", ":app:assembleDebug");
        createJavaLibraryClass("changed");
        project.executor().withInstantRun(new AndroidVersion(23, null)).run("assembleDebug");
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.COMPATIBLE.toString());
        assertThat(context.getArtifacts()).hasSize(1);
        InstantRunArtifact artifact = Iterables.getOnlyElement(context.getArtifacts());
        assertThat(artifact.type).isEqualTo(InstantRunArtifactType.RELOAD_DEX);
    }

    @Test
    public void checkVerifierFailsIfJavaResourceInLibraryChanged() throws Exception {
        File resource = project.getSubproject(":lib").file("src/main/resources/properties.txt");
        Files.asCharSink(resource, Charsets.UTF_8).write("java resource");

        project.execute("clean");
        ModelContainer<AndroidProject> modelContainer = project.model().fetchAndroidProjects();
        InstantRun instantRunModel =
                getInstantRunModel(modelContainer.getOnlyModelMap().get(":app"));

        // Check that original class is included.
        project.executor()
                .withInstantRun(new AndroidVersion(23, null))
                .run("clean", "assembleDebug");

        assertThat(InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel))
                .hasClass("Lcom/android/tests/libstest/lib/MainActivity;")
                .that()
                .hasMethod("onCreate");

        Files.asCharSink(resource, Charsets.UTF_8).write("changed java resource");

        project.executor().withInstantRun(new AndroidVersion(23, null)).run("assembleDebug");
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED.toString());
        assertThat(context.getArtifacts()).hasSize(1);
        assertThat(context.getArtifacts().get(0).type).isEqualTo(InstantRunArtifactType.SPLIT_MAIN);

        assertThatApk(new Apk(context.getArtifacts().get(0).file))
                .containsFileWithContent("properties.txt", "changed java resource");
    }

    /**
     * Check a hot-swap compatible change works as expected.
     */
    private void checkHotSwapCompatibleChange(@NonNull InstantRun instantRunModel)
            throws Exception {
        createLibraryClass("Hot swap change");

        project.executor().withInstantRun(new AndroidVersion(23, null)).run("assembleDebug");

        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);

        assertThat(context.getArtifacts()).hasSize(1);
        InstantRunArtifact artifact = Iterables.getOnlyElement(context.getArtifacts());
        assertThat(artifact.type).isEqualTo(InstantRunArtifactType.RELOAD_DEX);
        assertThatDex(artifact.file).containsClass("Lcom/android/tests/libstest/lib/Lib$override;");
    }


    private void createLibraryClass(String message) throws Exception {
        String javaCompile = "package com.android.tests.libstest.lib;\n"
            +"public class Lib {\n"
                +"public static String someString() {\n"
                  +"return \"someStringMessage=" + message + "\";\n"
                +"}\n"
            +"}\n";
        Files.asCharSink(
                        project.file("lib/src/main/java/com/android/tests/libstest/lib/Lib.java"),
                        Charsets.UTF_8)
                .write(javaCompile);
    }

    private void createJavaLibraryClass(String message) throws Exception {
        File dir = project.file("javalib/src/main/java/com/example/javalib");
        mkdirs(dir);
        String java = "package com.example.javalib;\n"
                +"public class A {\n"
                +"    public static String someString() {\n"
                +"        return \"someStringMessage=" + message + "\";\n"
                +"    }\n"
                +"}\n";
        Files.asCharSink(new File(dir, "A.java"), Charsets.UTF_8).write(java);
    }
}
