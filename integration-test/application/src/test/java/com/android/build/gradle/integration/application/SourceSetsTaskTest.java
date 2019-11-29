/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.utils.FileUtils;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the sourceSets task.
 */
public class SourceSetsTaskTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void runsSuccessfully() throws Exception {
        project.execute("sourceSets");

        String expected =
                "debug"
                        + System.lineSeparator()
                        + "-----"
                        + System.lineSeparator()
                        + "Compile configuration: debugCompile"
                        + System.lineSeparator()
                        + "build.gradle name: android.sourceSets.debug"
                        + System.lineSeparator()
                        + "Java sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/java")
                        + "]"
                        + System.lineSeparator()
                        + "Manifest file: "
                        + FileUtils.toSystemDependentPath("src/debug/AndroidManifest.xml")
                        + System.lineSeparator()
                        + "Android resources: ["
                        + FileUtils.toSystemDependentPath("src/debug/res")
                        + "]"
                        + System.lineSeparator()
                        + "Assets: ["
                        + FileUtils.toSystemDependentPath("src/debug/assets")
                        + "]"
                        + System.lineSeparator()
                        + "AIDL sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/aidl")
                        + "]"
                        + System.lineSeparator()
                        + "RenderScript sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/rs")
                        + "]"
                        + System.lineSeparator()
                        + "JNI sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/jni")
                        + "]"
                        + System.lineSeparator()
                        + "JNI libraries: ["
                        + FileUtils.toSystemDependentPath("src/debug/jniLibs")
                        + "]"
                        + System.lineSeparator()
                        + "Java-style resources: ["
                        + FileUtils.toSystemDependentPath("src/debug/resources")
                        + "]"
                        + System.lineSeparator()
                        + ""
                        + System.lineSeparator();

        assertThat(project.getBuildResult().getStdout()).contains(expected);
    }
}
