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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_MIN_SDK;
import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test dependency on a remote library that brings in a transitive
 * dependency that is already present in the main app.
 */
public class TestWithRemoteAndroidLibDepTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        appendToFile(project.getBuildFile(),
"\nsubprojects {\n" +
"    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
"}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion " + SUPPORT_LIB_MIN_SDK + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    androidTestCompile 'com.android.support:appcompat-v7:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkBuild() throws Exception {
        project.execute("app:assembleDebugAndroidTest");
    }
}
