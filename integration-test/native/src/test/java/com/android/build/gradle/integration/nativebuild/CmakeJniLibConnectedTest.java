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

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CmakeJniLibConnectedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("ndkJniLib")
                    .addFile(HelloWorldJniApp.cmakeLists("lib"))
                    .create();

    @Before
    public void setUp() throws IOException, InterruptedException {
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

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
