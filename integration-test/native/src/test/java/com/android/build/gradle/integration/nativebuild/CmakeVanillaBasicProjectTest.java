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
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for Vanilla-Cmake. */
public class CmakeVanillaBasicProjectTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(
                            HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build())
                    // Set the cmake version and set the local properties with the path to the cmake
                    // binary.
                    .setCmakeVersion("3.10.2")
                    .setWithCmakeDirInLocalProp(false)
                    .create();

    @Before
    public void setUp() throws IOException {
        File cmakeBinFolder =
                new File(GradleTestProject.getCmakeVersionFolder("3.10.4819442"), "bin");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        defaultConfig {\n"
                        + "          externalNativeBuild {\n"
                        + "              cmake {\n"
                        + "                abiFilters.addAll(\"armeabi-v7a\", \"armeabi\", \"x86\");\n"
                        + "                cFlags.addAll(\"-DTEST_C_FLAG\", \"-DTEST_C_FLAG_2\")\n"
                        + "                cppFlags.addAll(\"-DTEST_CPP_FLAG\")\n"
                        + "                targets.addAll(\"hello-jni\")\n"
                        + "                arguments \"-DCMAKE_PROGRAM_PATH="
                        + cmakeBinFolder.getAbsolutePath().replace("\\", "\\\\")
                        + "\"\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "            version \"3.10.2\""
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n");
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        assert !variant.getExternalNativeBuildTasks().isEmpty()\n"
                        + "        for (def task : variant.getExternalNativeBuildTasks()) {\n"
                        + "            assert task.getName() == \"externalNativeBuild\" + variant.getName().capitalize()\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkApkContent() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/armeabi/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkApkContentWithInjectedABI() throws IOException, InterruptedException {
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                .run("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkModel() throws IOException {
        project.model().fetchAndroidProjects(); // Make sure we can successfully get AndroidProject
        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.CMAKE.getName());
        assertThat(model.getBuildFiles()).isNotEmpty();
        assertThat(model.getName()).isEqualTo("project");
        int abiCount = 3;
        assertThat(model.getArtifacts()).hasSize(abiCount * 2);
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
        assertThat(model).hasArtifactGroupsOfSize(abiCount);
    }

    @Test
    public void checkClean() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug", "assembleRelease");
        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
        assertThat(model).hasBuildOutputCountEqualTo(6);
        assertThat(model).allBuildOutputsExist();
        // CMake .o files are kept in -B folder which is under .externalNativeBuild/
        assertThat(model).hasExactObjectFilesInExternalNativeBuildFolder("hello-jni.c.o");
        // CMake .so files are kept in -DCMAKE_LIBRARY_OUTPUT_DIRECTORY folder which is under build/
        assertThat(model).hasExactSharedObjectFilesInBuildFolder("libhello-jni.so");
        project.execute("clean");
        assertThat(model).noBuildOutputsExist();
        assertThat(model).hasExactObjectFilesInBuildFolder();
        assertThat(model).hasExactSharedObjectFilesInBuildFolder();
    }

    @Test
    public void checkCleanAfterAbiSubset() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug", "assembleRelease");
        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
        assertThat(model).hasBuildOutputCountEqualTo(6);

        List<File> allBuildOutputs = Lists.newArrayList();
        for (NativeArtifact artifact : model.getArtifacts()) {
            assertThat(artifact.getOutputFile()).isFile();
            allBuildOutputs.add(artifact.getOutputFile());
        }

        // Change the build file to only have "x86"
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "    android {\n"
                        + "        defaultConfig {\n"
                        + "          externalNativeBuild {\n"
                        + "              cmake {\n"
                        + "                abiFilters.clear();\n"
                        + "                abiFilters.addAll(\"x86\");\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n");
        project.execute("clean");

        // All build outputs should no longer exist, even the non-x86 outputs
        for (File output : allBuildOutputs) {
            assertThat(output).doesNotExist();
        }
    }
}
