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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for CMake with targets clause. */
public class CmakeTargetsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cpp").build())
                    .addFile(HelloWorldJniApp.cmakeListsMultiModule("."))
                    .addFile(HelloWorldJniApp.libraryCpp("src/main/cpp/library1", "library1.cpp"))
                    .addFile(HelloWorldJniApp.libraryCpp("src/main/cpp/library2", "library2.cpp"))
                    .setCmakeVersion("3.10.4819442")
                    .setWithCmakeDirInLocalProp(true)
                    .create();

    @Before
    public void setUp() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "    externalNativeBuild {\n"
                        + "      cmake {\n"
                        + "        path \"CMakeLists.txt\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
    }

    @Test
    public void checkMultiTargets() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary1.so");
        assertThatApk(apk).contains("lib/x86/liblibrary1.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary2.so");

        project.model().fetchAndroidProjects(); // Make sure we can successfully get AndroidProject
        assertModel(project, project.model().fetch(NativeAndroidProject.class));
    }

    @Test
    public void checkSingleTarget() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "      externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            targets.addAll(\"library2\")\n"
                        + "          }\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/liblibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary2.so");

        project.model().fetchAndroidProjects(); // Make sure we can successfully get AndroidProject
        assertModel(project, project.model().fetch(NativeAndroidProject.class));
    }

    private static void assertModel(GradleTestProject project, NativeAndroidProject model)
            throws IOException {
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.CMAKE.getName());
        assertThat(model.getBuildFiles()).hasSize(2);
        assertThat(model.getName()).isEqualTo("project");
        assertThat(model.getArtifacts())
                .hasSize(NdkHelper.getNdkInfo(project).getDefaultAbis().size() * 4);
        assertThat(model.getFileExtensions()).hasSize(1);

        for (File file : model.getBuildFiles()) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            assertThat(pathElements).doesNotContain("lib");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(model).hasArtifactGroupsNamed("debug", "release");
        assertThat(model)
                .hasArtifactGroupsOfSize(NdkHelper.getNdkInfo(project).getDefaultAbis().size() * 2);
    }
}
