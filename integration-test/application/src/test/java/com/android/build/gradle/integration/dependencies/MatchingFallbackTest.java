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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.build.gradle.integration.common.utils.SyncIssueHelperKt.checkIssuesForSameData;
import static com.android.build.gradle.integration.common.utils.SyncIssueHelperKt.checkIssuesForSameSeverity;
import static com.android.build.gradle.integration.common.utils.SyncIssueHelperKt.checkIssuesForSameType;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.util.Collection;
import org.gradle.tooling.BuildException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MatchingFallbackTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        // add a resources file in the debug build type of the lib.
        File fooTxt =
                FileUtils.join(
                        project.getSubproject("library").getTestDir(),
                        "src",
                        "debug",
                        "resources",
                        "debug_foo.txt");
        FileUtils.mkdirs(fooTxt.getParentFile());
        Files.asCharSink(fooTxt, Charsets.UTF_8).write("foo");
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkBuildType_NoMatching() throws Exception {
        // add a new build type and a dependency on the library module, but no matching strategy
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        foo {}\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        // build expecting an error.
        thrown.expect(BuildException.class);
        project.executor().run("clean", "app:assembleFoo");

        // then query the model
        ModelContainer<AndroidProject> models =
                project.model().ignoreSyncIssues().fetchAndroidProjects();

        //get the app model
        AndroidProject appModel = models.getOnlyModelMap().get(":app");

        final Collection<SyncIssue> syncIssues = appModel.getSyncIssues();
        assertThat(syncIssues).hasSize(4);

        // all the issues should have the same type/severity/data
        checkIssuesForSameSeverity(syncIssues, SyncIssue.SEVERITY_ERROR);
        checkIssuesForSameType(syncIssues, SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        checkIssuesForSameData(syncIssues, null);

        // grab a specific one to test its output
        SyncIssue singleIssue = null;
        String message =
                "Unable to resolve dependency for ':app@foo/runtimeClasspath': Could not resolve project :library.";
        for (SyncIssue issue : syncIssues) {
            if (issue.getMessage().equals(message)) {
                singleIssue = issue;
                break;
            }
        }
        if (singleIssue == null) {
            fail("Failed to find SyncIssue with Message: " + message);
        }

        assertThat(singleIssue.getMultiLineMessage()).isNotNull();
        assertThat(singleIssue.getMultiLineMessage())
                .containsExactly(
                        "Could not resolve project :library.",
                        "Required by:",
                        "    project :app",
                        " > Unable to find a matching configuration of project :library:",
                        "     - Configuration 'debugApiElements':",
                        "         - Required com.android.build.api.attributes.BuildTypeAttr 'foo' and found incompatible value 'debug'.",
                        "         - Required com.android.build.gradle.internal.dependency.AndroidTypeAttr 'Aar' and found compatible value 'Aar'.",
                        "         - Found com.android.build.api.attributes.VariantAttr 'debug' but wasn't required.",
                        "         - Required org.gradle.api.attributes.Usage 'java-runtime' and found incompatible value 'java-api'.",
                        "     - Configuration 'debugRuntimeElements':",
                        "         - Required com.android.build.api.attributes.BuildTypeAttr 'foo' and found incompatible value 'debug'.",
                        "         - Required com.android.build.gradle.internal.dependency.AndroidTypeAttr 'Aar' and found compatible value 'Aar'.",
                        "         - Found com.android.build.api.attributes.VariantAttr 'debug' but wasn't required.",
                        "         - Required org.gradle.api.attributes.Usage 'java-runtime' and found compatible value 'java-runtime'.",
                        "     - Configuration 'releaseApiElements':",
                        "         - Required com.android.build.api.attributes.BuildTypeAttr 'foo' and found incompatible value 'release'.",
                        "         - Required com.android.build.gradle.internal.dependency.AndroidTypeAttr 'Aar' and found compatible value 'Aar'.",
                        "         - Found com.android.build.api.attributes.VariantAttr 'release' but wasn't required.",
                        "         - Required org.gradle.api.attributes.Usage 'java-runtime' and found incompatible value 'java-api'.",
                        "     - Configuration 'releaseRuntimeElements':",
                        "         - Required com.android.build.api.attributes.BuildTypeAttr 'foo' and found incompatible value 'release'.",
                        "         - Required com.android.build.gradle.internal.dependency.AndroidTypeAttr 'Aar' and found compatible value 'Aar'.",
                        "         - Found com.android.build.api.attributes.VariantAttr 'release' but wasn't required.",
                        "         - Required org.gradle.api.attributes.Usage 'java-runtime' and found compatible value 'java-runtime'.")
                .inOrder();
    }

    @Test
    public void checkBuildType_WithMatching() throws Exception {
        // add on the app: a new build type with matching and a dependency on the library module
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        foo {\n"
                        + "          matchingFallbacks = 'debug'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        project.executeAndReturnMultiModel("clean", ":app:assembleFoo");

        final Apk apk = project.getSubproject("app").getApk(ApkType.of("foo", false));
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResource("/debug_foo.txt");
    }

    @Test
    public void checkFlavors_WithMatching() throws Exception {
        // add flavors on the app, as well as a dependency on the library module, and a matching
        // strategy for the flavor
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color'\n"
                        + "    productFlavors {\n"
                        + "        orange {\n"
                        + "            dimension = 'color'\n"
                        + "            matchingFallbacks = 'red'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        // On the library, add a different flavor for the same dimension
        // and a matching flavor in a different dimension (because there's only one value in that
        // dimension, there won't be any issue).
        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color', 'fruit'\n"
                        + "    productFlavors {\n"
                        + "        orange {\n"
                        + "            dimension = 'fruit'\n"
                        + "        }\n"
                        + "        red {\n"
                        + "            dimension = 'color'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // add a resources file in the red flavor of the lib.
        File fooTxt =
                FileUtils.join(
                        project.getSubproject("library").getTestDir(),
                        "src",
                        "red",
                        "resources",
                        "red_foo.txt");
        FileUtils.mkdirs(fooTxt.getParentFile());
        Files.asCharSink(fooTxt, Charsets.UTF_8).write("foo");

        project.executeAndReturnMultiModel("clean", ":app:assembleOrangeDebug");

        final Apk apk = project.getSubproject("app").getApk(ApkType.DEBUG, "orange");
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResource("/debug_foo.txt");
        assertThat(apk).containsJavaResource("/red_foo.txt");
    }
}
