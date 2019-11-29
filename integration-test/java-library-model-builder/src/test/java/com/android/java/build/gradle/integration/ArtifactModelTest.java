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

package com.android.java.build.gradle.integration;


import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleJavaLibs;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.java.model.ArtifactModel;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link ArtifactModel} returned from Java Library Plugin
 */
public class ArtifactModelTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .withDeviceProvider(false)
                    .withSdk(false)
                    .withAndroidGradlePlugin(false)
                    .fromTestApp(MultiModuleJavaLibs.createWithLibs(2))
                    .create();
    private Map<String, ArtifactModel> artifactModelMap;

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath \"com.android.java.tools.build:java-lib-model-builder:$rootProject.buildVersion\"\n"
                        + "    }\n"
                        + "}\n\n"
                        + "subprojects {\n"
                        + "    apply plugin: 'com.android.java'\n"
                        + "}");

        File jarAarBuildFile = project.getSubproject(":lib2").getBuildFile();
        TestFileUtils.appendToFile(jarAarBuildFile,
                "configurations.maybeCreate(\"default\")\n"
                        + "artifacts.add(\"default\", file('auto-value-1.3.jar'))\n"
                        + "artifacts.add(\"default\", file('non-exist-1.0.jar'))\n");
        // Remove java plugin
        TestFileUtils.searchAndReplace(jarAarBuildFile, "apply plugin: 'java'", "");

        FileUtils.createFile(project.getSubproject(":lib2")
                .file("auto-value-1.3.jar"), "dummy");
        artifactModelMap = project.model().fetchMulti(ArtifactModel.class);
    }

    @Test
    public void checkArtifactModel() throws Exception {
        String jarModuleName = ":lib2";
        String defaultConfiguration = "default";

        // Check model exists for Jar/Aar Module.
        assertThat(artifactModelMap).hasSize(1);
        assertThat(artifactModelMap).containsKey(jarModuleName);

        // Check artifactModel returns correct name.
        ArtifactModel artifactModel = artifactModelMap.get(jarModuleName);
        assertThat(artifactModel.getName()).isEqualTo(jarModuleName.substring(1));
        assertThat(artifactModel.getArtifactsByConfiguration()).containsKey(defaultConfiguration);

        // Check artifactsByConfiguration returns full path for both of
        // existing and non-existing artifacts.
        Set<String> files =
                artifactModel.getArtifactsByConfiguration().get(defaultConfiguration)
                        .stream().map(File::getPath).collect(Collectors.toSet());
        assertThat(files).hasSize(2);
        String existingArtifact =
                project.getSubproject(jarModuleName).file("auto-value-1.3.jar").getPath();
        String nonExistingArtifact
                = project.getSubproject(jarModuleName).file("non-exist-1.0.jar").getPath();
        assertThat(files).contains(existingArtifact);
        assertThat(files).contains(nonExistingArtifact);
    }
}
