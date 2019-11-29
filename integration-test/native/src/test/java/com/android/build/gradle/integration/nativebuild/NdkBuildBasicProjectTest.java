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

import static com.android.build.gradle.integration.common.truth.NativeAndroidProjectSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
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

/** Assemble tests for ndk-build. */
public class NdkBuildBasicProjectTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().build())
                    .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
                    .create();

    @Before
    public void setUp() throws IOException {
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
                        + "              ndkBuild {\n"
                        + "                abiFilters.addAll(\"armeabi-v7a\", \"armeabi\", \"x86\")\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "        externalNativeBuild {\n"
                        + "          ndkBuild {\n"
                        + "            path \"src/main/jni/Android.mk\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    android.packagingOptions {\n"
                        + "        doNotStrip \"*/armeabi-v7a/libhello-jni.so\"\n"
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
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        project.execute("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isNotStripped();

        lib = ZipHelper.extractFile(apk, "lib/armeabi/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkApkContentWithInjectedAbi() throws IOException, InterruptedException {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        // Pass invalid-abi, x86 and armeabi. The first (invalid-abi) should be ignored because
        // it is not valid for the build . The second (x86) should be the one chosen to build.
        // Finally, armeabi is valid but it will be ignored because x86 is "preferred".
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "invalid-abi,x86,armeabi")
                .run("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        assertThat(project.file("build/intermediates/merged_manifests/debug/AndroidManifest.xml"))
                .contains("android:testOnly=\"true\"");
    }

    @Test
    public void checkModel() throws IOException {
        project.model().fetchAndroidProjects(); // Make sure we can successfully get AndroidProject
        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.NDK_BUILD.getName());
        assertThat(model.getBuildFiles()).hasSize(1);
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
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        project.execute("clean", "assembleDebug", "assembleRelease");
        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
        assertThat(model).hasBuildOutputCountEqualTo(6);
        assertThat(model).allBuildOutputsExist();
        assertThat(model).hasExactObjectFilesInBuildFolder("hello-jni.o");
        assertThat(model).hasExactSharedObjectFilesInBuildFolder("libhello-jni.so");
        project.execute("clean");
        assertThat(model).noBuildOutputsExist();
        assertThat(model).hasExactObjectFilesInBuildFolder();
        assertThat(model).hasExactSharedObjectFilesInBuildFolder();
    }

    @Test
    public void checkCleanAfterAbiSubset() throws IOException, InterruptedException {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
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
                        + "              ndkBuild {\n"
                        + "                abiFilters.clear();\n"
                        + "                abiFilters.addAll(\"x86\")\n"
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
