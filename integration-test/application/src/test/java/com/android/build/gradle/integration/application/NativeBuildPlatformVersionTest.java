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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.NativeModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;

/** Tests that platform version selected is correct */
public class NativeBuildPlatformVersionTest {

    private static String cmakeLists =
            "cmake_minimum_required(VERSION 3.4.1)\n"
                    + "file(GLOB SRC src/main/cpp/hello-jni.cpp)\n"
                    + "set(CMAKE_VERBOSE_MAKEFILE ON)\n"
                    + "add_library(hello-jni SHARED \\${SRC})\n"
                    + "target_link_libraries(hello-jni log)\"";

    private static String androidMk =
            "LOCAL_PATH := \\$(call my-dir)\n"
                    + "include \\$(CLEAR_VARS)\n"
                    + "LOCAL_MODULE    := hello-jni\n"
                    + "LOCAL_SRC_FILES := hello-jni.cpp\n"
                    + "include \\$(BUILD_SHARED_LIBRARY)";

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder()
                            .withNativeDir("cpp")
                            .useCppSource(true)
                            .build())
                        .create();

    @Before
    public void setup() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "apply plugin: 'com.android.application'\n");
    }
    

    private static Set<String> getMatchingPlatformArchitectureFoldersFromSystem(List<String> flags) {
        Set<String> platformArchitectures = Sets.newHashSet();
        boolean lastFlagWasSystem = true;
        for (String flag : flags) {

            if (flag.contains("platform") && flag.contains("arch") && lastFlagWasSystem) {
                File folder = new File(flag);
                // Basic sanity check that we got an include folder.
                assertThat(folder).isDirectory();
                assertThat(folder.getName()).isEqualTo("include");
                folder = folder.getParentFile();
                assertThat(folder.getName()).isEqualTo("usr");
                folder = folder.getParentFile();
                String architecture = folder.getName();
                assertThat(architecture).startsWith("arch-");
                folder = folder.getParentFile();
                String platform = folder.getName();
                assertThat(platform).startsWith("android-");
                platformArchitectures.add(platform + "." + architecture);
            } else {
                lastFlagWasSystem = flag.equals("-isystem");
            }
        }
        return platformArchitectures;
    }

    private static Set<String> getMatchingPlatformArchitectureFoldersFromPrefix(
            String prefix,
            List<String> flags) {
        Set<String> platformArchitectures = Sets.newHashSet();
        for (String flag : flags) {
            if (flag.contains("platform") && flag.contains("arch")
                    && flag.startsWith(prefix)) {
                String includePath = flag.substring(prefix.length());

                File folder = new File(includePath);
                if (folder.getName().equals("include")) {
                    folder = folder.getParentFile();
                }
                if (folder.getName().equals("usr")) {
                    folder = folder.getParentFile();
                }
                String architecture = folder.getName();
                assertThat(architecture).startsWith("arch-");
                folder = folder.getParentFile();
                String platform = folder.getName();
                assertThat(platform).startsWith("android-");
                platformArchitectures.add(platform + "." + architecture);
            }
        }
        return platformArchitectures;
    }

    private static Set<String> getDistinctPlatformArchitectureCombinations(NativeAndroidProject model) {
        Set<String> platformArchitectures = Sets.newHashSet();
        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> flags = NativeModelHelper.getFlatCppFlags(model, artifact);
            platformArchitectures.addAll(getMatchingPlatformArchitectureFoldersFromSystem(flags));
            platformArchitectures.addAll(getMatchingPlatformArchitectureFoldersFromPrefix(
                    "--isysroot=", flags));
            platformArchitectures.addAll(getMatchingPlatformArchitectureFoldersFromPrefix("-I",
                    flags));
        }
        assertThat(platformArchitectures).isNotEmpty();
        System.err.printf("MAP %s\n", platformArchitectures);
        return platformArchitectures;
    }

    private void checkBuildSucceeds(String... expectedPlatformAbiCombinations) throws Exception {
        project.model().fetch(AndroidProject.class);
        NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);

        assertThat(model).hasArtifactGroupsOfSize(4);
        Set<String> platformArchitectures = getDistinctPlatformArchitectureCombinations(model);
        assertThat(platformArchitectures)
                .containsExactly((Object[]) expectedPlatformAbiCombinations);
        project.executor().run("assembleDebug");
    }
}
