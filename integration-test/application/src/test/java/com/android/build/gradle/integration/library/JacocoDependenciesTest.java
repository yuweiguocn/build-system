/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.Java8OptionalSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilder;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.SplitApks;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import java.util.Optional;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test for jacoco agent runtime dependencies. */
public class JacocoDependenciesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Before
    public void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        appendToFile(
                project.getBuildFile(),
                "\nsubprojects {\n"
                        + "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    api project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\nandroid.buildTypes.debug.testCoverageEnabled = true");
    }

    @Test
    public void checkJacocoInApp() throws IOException, InterruptedException {
        project.executor().run("clean", "app:assembleDebug");

        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        assertThat(dexOptional).isPresent();

        // noinspection ConstantConditions
        assertThat(dexOptional.get()).containsClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkDefaultVersion() throws IOException {
        assertAgentMavenCoordinates(
                "org.jacoco:org.jacoco.agent:" + JacocoOptions.DEFAULT_VERSION + ":runtime@jar");
    }

    @Test
    public void checkVersionForced() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("library").getBuildFile(),
                "apply plugin: 'com.android.library'",
                "\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "dependencies {\n"
                        + "  implementation "
                        + "'org.jacoco:org.jacoco.agent:0.7.4.201502262128:runtime'\n"
                        + "}\n");
        assertAgentMavenCoordinates(
                "org.jacoco:org.jacoco.agent:" + JacocoOptions.DEFAULT_VERSION + ":runtime@jar");
    }

    @Test
    public void checkAgentRuntimeVersionWhenOverridden() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n" + "android.jacoco.version '0.7.4.201502262128'\n");
        assertAgentMavenCoordinates("org.jacoco:org.jacoco.agent:0.7.4.201502262128:runtime@jar");
    }

    @Test
    public void checkJacocoDisabledForInstantRun() throws Exception {
        project.executor().withInstantRun(new AndroidVersion(21)).run("app:assembleDebug");
        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModelMap().get(":app");

        SplitApks apks =
                InstantRunTestUtils.getCompiledColdSwapChange(
                        InstantRunTestUtils.getInstantRunModel(model));

        DexBackedClassDef libClass =
                apks.getAllClasses().get("Lcom/example/android/multiproject/library/PersonView;");
        libClass.getFields()
                .forEach(f -> assertThat(f.getName().toLowerCase()).doesNotContain("jacocodata"));
    }

    @Test
    public void checkVersionWithDx() throws IOException {
        assertAgentMavenCoordinates(
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .with(BooleanOption.ENABLE_D8, false),
                "org.jacoco:org.jacoco.agent:"
                        + JacocoConfigurations.VERSION_FOR_DX
                        + ":runtime@jar");
    }

    private void assertAgentMavenCoordinates(@NonNull String expected) throws IOException {
        assertAgentMavenCoordinates(project.model(), expected);
    }

    private void assertAgentMavenCoordinates(
            @NonNull ModelBuilder modelBuilder, @NonNull String expected) throws IOException {
        ModelContainer<AndroidProject> container =
                modelBuilder
                        .level(AndroidProject.MODEL_LEVEL_LATEST)
                        .withFullDependencies()
                        .fetchAndroidProjects();
        LibraryGraphHelper helper = new LibraryGraphHelper(container);
        Variant appDebug =
                AndroidProjectUtils.getVariantByName(
                        container.getOnlyModelMap().get(":library"), "debug");

        DependencyGraphs dependencyGraphs = appDebug.getMainArtifact().getDependencyGraphs();
        assertThat(
                        helper.on(dependencyGraphs)
                                .forPackage()
                                .withType(JAVA)
                                .mapTo(LibraryGraphHelper.Property.COORDINATES))
                .named("jacoco agent runtime jar")
                .containsExactly(expected);
    }
}
