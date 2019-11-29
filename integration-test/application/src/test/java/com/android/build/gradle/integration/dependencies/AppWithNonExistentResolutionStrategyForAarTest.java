/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

/** test for flavored dependency on a different package. */
public class AppWithNonExistentResolutionStrategyForAarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        TestFileUtils.appendToFile(project.getBuildFile(),
                "\n" +
                "subprojects {\n" +
                "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
                "}\n");
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "\n" +
                "dependencies {\n" +
                "    debugCompile project(\":library\")\n" +
                "    releaseCompile project(\":library\")\n" +
                "}\n" +
                "\n" +
                "configurations {\n" +
                "  debugCompileClasspath\n" +
                "  debugRuntimeClasspath\n" +
                "}\n" +
                "\n" +
                "configurations.debugCompileClasspath {\n" +
                "  resolutionStrategy {\n" +
                "    eachDependency { DependencyResolveDetails details ->\n" +
                "      if (details.requested.name == \"jdeferred-android-aar\") {\n" +
                "        details.useVersion \"-1.-1.-1\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "configurations.debugRuntimeClasspath {\n" +
                "  resolutionStrategy {\n" +
                "    eachDependency { DependencyResolveDetails details ->\n" +
                "      if (details.requested.name == \"jdeferred-android-aar\") {\n" +
                "        details.useVersion \"-1.-1.-1\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "\n");

        TestFileUtils.appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile \"org.jdeferred:jdeferred-android-aar:1.2.3\"\n" +
                "}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Ignore
    @Test
    public void checkWeReceivedASyncIssue() throws Exception {
        modelContainer = project.model().ignoreSyncIssues().fetchAndroidProjects();
        SyncIssue issue =
                assertThat(modelContainer.getOnlyModelMap().get(":app"))
                        .hasSingleIssue(
                                SyncIssue.SEVERITY_ERROR, SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        assertThat(issue.getMessage()).contains("org.jdeferred:jdeferred-android-aar:-1.-1.-1");
    }
}
