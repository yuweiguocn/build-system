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
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for CMake. */
public class CmakeJniLibTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("ndkJniLib")
                    .addFile(HelloWorldJniApp.cmakeLists("lib"))
                    .setCmakeVersion("3.10.4819442")
                    .setWithCmakeDirInLocalProp(true)
                    .create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        new File(project.getTestDir(), "src/main/jni")
                .renameTo(new File(project.getTestDir(), "src/main/cxx"));
        GradleTestProject lib = project.getSubproject("lib");
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "}\n");

        // Convert externalNativeBuild { ndkbuild { path "Android.mk" } } to
        // externalNativeBuild { cmake { path "CMakeList.txt" } }
        TestFileUtils.searchAndReplace(lib.getBuildFile(), "ndkBuild", "cmake");
        TestFileUtils.searchAndReplace(lib.getBuildFile(), "Android.mk", "CMakeLists.txt");
        project.execute(
                "clean", "assembleDebug", "generateJsonModelDebug", "generateJsonModelRelease");
        assertThat(project.getSubproject("lib").file("build/intermediates/cmake")).exists();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkApkContent() throws IOException {
        GradleTestProject app = project.getSubproject("app");
        Apk gingerbreadUniversal =
                app.getApk("universal", GradleTestProject.ApkType.DEBUG, "gingerbread");
        if (!gingerbreadUniversal.exists()) {
            throw new RuntimeException(String.format("Could not find %s", gingerbreadUniversal));
        }

        TruthHelper.assertThatApk(
                        app.getApk("universal", GradleTestProject.ApkType.DEBUG, "gingerbread"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatApk(
                        app.getApk(
                                "armeabi-v7a", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatApk(
                        app.getApk("x86", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void checkModel() throws IOException {
        // Make sure we can successfully get AndroidProject
        project.model().fetchAndroidProjects().getOnlyModelMap().get(":app");

        NativeAndroidProject model =
                project.model().fetchMulti(NativeAndroidProject.class).get(":lib");
        assertThat(model).isNotNull();
        assertThat(model.getBuildFiles()).hasSize(2);
        assertThat(model.getName()).isEqualTo("lib");
        assertThat(model.getArtifacts())
                .hasSize(NdkHelper.getNdkInfo(project).getDefaultAbis().size() * 2);
        assertThat(model.getFileExtensions()).hasSize(1);

        for (File file : model.getBuildFiles()) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(groupToArtifacts.keySet()).containsExactly("debug", "release");
        assertThat(groupToArtifacts.get("debug")).hasSize(groupToArtifacts.get("release").size());
    }
}
