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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test various scenarios with AnnotationProcessorOptions.includeCompileClasspath */
public class AnnotationProcessorCompileClasspathTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("butterknife").create();

    @Before
    public void setUp() throws IOException {
        // Remove dependencies block from build file.
        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(), "(?s)dependencies \\{.*\\}", "");
    }

    @Test
    public void failWhenClasspathHasProcessor() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile 'com.jakewharton:butterknife:7.0.1'\n"
                        + "}\n"
                        + "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath null");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertThat(result.getFailureMessage())
                .contains("Annotation processors must be explicitly declared now");
        assertThat(result.getFailureMessage())
                .contains("- butterknife-7.0.1.jar (com.jakewharton:butterknife:7.0.1)");
    }

    @Test
    public void failForAndroidTest() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile 'com.jakewharton:butterknife:7.0.1'\n"
                        + "    annotationProcessor 'com.jakewharton:butterknife:7.0.1'\n"
                        + "}\n"
                        + "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath null");

        GradleBuildResult result = project.executor().run("assembleDebugAndroidTest");
        String message = result.getStdout();
        assertThat(message).contains("Annotation processors must be explicitly declared now");
        assertThat(message).contains("androidTestAnnotationProcessor");
        assertThat(message).contains("- butterknife-7.0.1.jar (com.jakewharton:butterknife:7.0.1)");

        result = project.executor().run("assembleDebugUnitTest");
        message = result.getStdout();
        assertThat(message).contains("Annotation processors must be explicitly declared");
        assertThat(message).contains("testAnnotationProcessor");
        assertThat(message).contains("- butterknife-7.0.1.jar (com.jakewharton:butterknife:7.0.1)");
    }

    @Test
    public void checkSuccessWithIncludeCompileClasspath() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath = true\n"
                        + "dependencies {\n"
                        + "    compile 'com.jakewharton:butterknife:7.0.1'\n"
                        + "}\n");
        project.executor()
                .run("assembleDebug", "assembleDebugAndroidTest", "assembleDebugUnitTest");
    }

    @Test
    public void checkSuccessWhenProcessorIsSpecified() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath null\n"
                        + "dependencies {\n"
                        + "    compile 'com.jakewharton:butterknife:7.0.1'\n"
                        + "    annotationProcessor 'com.jakewharton:butterknife:7.0.1'\n"
                        + "    testAnnotationProcessor 'com.jakewharton:butterknife:7.0.1'\n"
                        + "    androidTestAnnotationProcessor 'com.jakewharton:butterknife:7.0.1'\n"
                        + "}\n");
        project.executor().run("assembleDebug", "assembleDebugAndroidTest", "testDebug");
    }
}
