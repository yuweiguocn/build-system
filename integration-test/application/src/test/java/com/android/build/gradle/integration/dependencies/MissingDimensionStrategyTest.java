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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MissingDimensionStrategyTest {

    private static final String FILE_TXT = "file.txt";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        // add some files to verify variant selection post build.
        final GradleTestProject library = project.getSubproject("library");
        addFile(library, "foo", "foo");
        addFile(library, "bar", "bar");

        appendToFile(
                library.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'libdim'\n"
                        + "    productFlavors {\n"
                        + "        foo {\n"
                        + "            dimension 'libdim'\n"
                        + "        }\n"
                        + "        bar {\n"
                        + "            dimension 'libdim'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkDefaultConfig_Selection() throws Exception {
        final GradleTestProject appProject = project.getSubproject("app");
        appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        missingDimensionStrategy 'libdim', 'foo'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        project.executeAndReturnMultiModel("clean", ":app:assembleDebug");

        final Apk apk = appProject.getApk(ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResourceWithContent("/" + FILE_TXT, "foo");
    }

    @Test
    public void checkDefaultConfig_Fallback() throws Exception {
        final GradleTestProject appProject = project.getSubproject("app");
        appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        missingDimensionStrategy 'libdim', 'wrong_value', 'foo'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        project.executeAndReturnMultiModel("clean", ":app:assembleDebug");

        final Apk apk = appProject.getApk(ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResourceWithContent("/" + FILE_TXT, "foo");
    }

    @Test
    public void checkFlavors_Selection() throws Exception {
        final GradleTestProject appProject = project.getSubproject("app");
        appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color'\n"
                        + "    productFlavors {\n"
                        + "        blue {\n"
                        + "            dimension = 'color'\n"
                        + "            missingDimensionStrategy 'libdim', 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        project.executeAndReturnMultiModel("clean", ":app:assembleBlueDebug");

        final Apk apk = appProject.getApk(ApkType.DEBUG, "blue");
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResourceWithContent("/" + FILE_TXT, "foo");
    }

    @Test
    public void checkFlavors_Fallback() throws Exception {
        final GradleTestProject appProject = project.getSubproject("app");
        appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color'\n"
                        + "    productFlavors {\n"
                        + "        blue {\n"
                        + "            dimension = 'color'\n"
                        + "            missingDimensionStrategy 'libdim', 'wrong_value', 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        project.executeAndReturnMultiModel("clean", ":app:assembleBlueDebug");

        final Apk apk = appProject.getApk(ApkType.DEBUG, "blue");
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResourceWithContent("/" + FILE_TXT, "foo");
    }

    @Test
    public void checkFlavors_Override() throws Exception {
        final GradleTestProject appProject = project.getSubproject("app");
        appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        missingDimensionStrategy 'libdim', 'foo'\n"
                        + "    }\n"
                        + "    flavorDimensions 'color'\n"
                        + "    productFlavors {\n"
                        + "        blue {\n"
                        + "            dimension = 'color'\n"
                        + "            missingDimensionStrategy 'libdim', 'bar'\n"
                        + "        }\n"
                        + "        red {\n"
                        + "            dimension = 'color'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        project.executeAndReturnMultiModel(
                "clean", ":app:assembleBlueDebug", ":app:assembleRedDebug");

        final Apk blueApk = appProject.getApk(ApkType.DEBUG, "blue");
        assertThat(blueApk.getFile()).isFile();
        assertThat(blueApk).containsJavaResourceWithContent("/" + FILE_TXT, "bar");

        final Apk redApk = appProject.getApk(ApkType.DEBUG, "red");
        assertThat(redApk.getFile()).isFile();
        assertThat(redApk).containsJavaResourceWithContent("/" + FILE_TXT, "foo");
    }

    @Test
    public void checkFlavors_nodefault() throws Exception {
        final GradleTestProject appProject = project.getSubproject("app");
        appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color'\n"
                        + "    productFlavors {\n"
                        + "        blue {\n"
                        + "            dimension = 'color'\n"
                        + "            missingDimensionStrategy 'libdim', 'bar'\n"
                        + "        }\n"
                        + "        red {\n"
                        + "            dimension = 'color'\n"
                        + "            missingDimensionStrategy 'libdim', 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        project.executeAndReturnMultiModel(
                "clean", ":app:assembleBlueDebug", ":app:assembleRedDebug");

        final Apk blueApk = appProject.getApk(ApkType.DEBUG, "blue");
        assertThat(blueApk.getFile()).isFile();
        assertThat(blueApk).containsJavaResourceWithContent("/" + FILE_TXT, "bar");

        final Apk redApk = appProject.getApk(ApkType.DEBUG, "red");
        assertThat(redApk.getFile()).isFile();
        assertThat(redApk).containsJavaResourceWithContent("/" + FILE_TXT, "foo");
    }

    private static void addFile(
            @NonNull GradleTestProject project,
            @NonNull String dimension,
            @NonNull String fileContent)
            throws IOException {
        File file = FileUtils.join(project.getTestDir(), "src", dimension, "resources", FILE_TXT);
        FileUtils.mkdirs(file.getParentFile());
        Files.asCharSink(file, Charsets.UTF_8).write(fileContent);
    }
}
