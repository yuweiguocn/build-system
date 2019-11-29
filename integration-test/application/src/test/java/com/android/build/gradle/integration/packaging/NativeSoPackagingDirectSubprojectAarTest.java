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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test native .so packaging from a subproject that directly publish an AAR. */
public class NativeSoPackagingDirectSubprojectAarTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create();

    @Before
    public void setUp() throws IOException, InterruptedException {
        // Create an .so file in the library
        GradleTestProject lib = project.getSubproject(":lib");
        File soLib = lib.file("src/main/jniLibs/x86/libfoo.so");
        FileUtils.mkdirs(soLib.getParentFile());
        Files.write(soLib.toPath(), "foo".getBytes(StandardCharsets.UTF_8));

        // Create a new subproject that directly publish the aar built by :lib
        File buildFile = project.file("directLib/build.gradle");
        FileUtils.mkdirs(buildFile.getParentFile());
        Files.write(
                buildFile.toPath(),
                ("configurations.create('default')\n"
                                + "artifacts.add('default', file('"
                                + FileUtils.toSystemIndependentPath(
                                        lib.getAar("debug").getFile().toString())
                                + "'))")
                        .getBytes(StandardCharsets.UTF_8));
        TestFileUtils.appendToFile(project.file("settings.gradle"), "include 'directLib'\n");

        // Rewrite app project to depend on directLib.
        Files.write(
                project.getSubproject(":app").getBuildFile().toPath(),
                ("apply plugin: 'com.android.application'\n"
                                + "\n"
                                + "dependencies {\n"
                                + "    compile project(':directLib')\n"
                                + "}\n"
                                + "\n"
                                + "android {\n"
                                + "     compileSdkVersion "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "     buildToolsVersion '"
                                + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                                + "'\n"
                                + "}\n")
                        .getBytes(StandardCharsets.UTF_8));

        // Build library to create the aar with .so file.
        project.executor().run(":lib:assembleDebug");
    }

    @Test
    public void checkApkContainsNativeSo() throws IOException, InterruptedException {
        project.executor().run(":app:assembleDebug");
        assertThat(project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("lib/x86/libfoo.so", "foo");
    }
}
