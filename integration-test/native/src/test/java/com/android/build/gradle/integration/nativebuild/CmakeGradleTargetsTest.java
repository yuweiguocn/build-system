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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.StringOption;
import com.android.testutils.apk.Apk;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for Cmake. */
@RunWith(Parameterized.class)
public class CmakeGradleTargetsTest {
    private List<Target> targets;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cxx").build())
                    .addFile(HelloWorldJniApp.cmakeListsWithExecutables("."))
                    .addFile(HelloWorldJniApp.executableCpp("src/main/cxx/executable", "main.cpp"))
                    .setCmakeVersion("3.10.4819442")
                    .setWithCmakeDirInLocalProp(true)
                    .create();

    @Parameterized.Parameters(name = "gradle target(s) = {0}")
    public static Collection<Object[]> data() {
        return ImmutableList.of(
                new Object[] {Collections.emptyList()},
                new Object[] {ImmutableList.of(Target.HELLO_JNI, Target.HELLO_EXECUTABLE)},
                new Object[] {ImmutableList.of(Target.HELLO_JNI)},
                new Object[] {ImmutableList.of(Target.HELLO_EXECUTABLE)});
    }

    public CmakeGradleTargetsTest(List<Target> targets) {
        this.targets = targets;
    }

    @Before
    public void setUp() throws IOException {
        StringBuilder buildDotGradle =
                new StringBuilder(
                        "apply plugin: 'com.android.application'\n"
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
                                + "                cppFlags.addAll(\"-DTEST_CPP_FLAG\")\n");

        if (!targets.isEmpty()) {
            buildDotGradle
                    .append("                targets.addAll(")
                    .append(Joiner.on(',').join(targets))
                    .append(")\n");
        }

        buildDotGradle.append(
                "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n");

        TestFileUtils.appendToFile(project.getBuildFile(), buildDotGradle.toString());
    }

    @Test
    public void checkBuildDotGradleTargetsFieldHonored() throws IOException, InterruptedException {
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                .run("clean", "assembleDebug");
        Apk apk = project.getApk("debug");

        Set<Target> shouldNotBeFound = Sets.newHashSet(Target.values());
        if (targets.isEmpty()) {
            shouldNotBeFound.clear();
            targets = Arrays.asList(Target.values());
        } else {
            shouldNotBeFound.removeAll(targets);
        }

        for (Target target : targets) {
            if (target.getLocation() != null) {
                assertThatApk(apk).contains(target.getLocation());
            } else {
                assertThat(project.getTestDir()).containsFile(target.getName());
                assertThatApk(apk).doesNotContainFile(target.getName());
            }
        }

        for (Target target : shouldNotBeFound) {
            if (target.getLocation() != null) {
                assertThatApk(apk).doesNotContain(target.getLocation());
            } else {
                assertThat(project.getTestDir()).doesNotContainFile(target.getName());
                assertThatApk(apk).doesNotContainFile(target.getName());
            }
        }
    }

    private enum Target {
        HELLO_JNI("hello-jni", "lib/x86/libhello-jni.so"),
        HELLO_EXECUTABLE("hello-executable", null);

        private String name;
        private String location;

        Target(String name, String location) {
            this.name = name;
            this.location = location;
        }

        @Override
        public String toString() {
            return "\"" + name + "\"";
        }

        String getName() {
            return name;
        }

        String getLocation() {
            return location;
        }
    }
}
