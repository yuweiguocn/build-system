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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.nio.charset.StandardCharsets;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * App with androidTestCompile dependency on a library that share the same dependencies on a second
 * library.
 */
public class AppWithAndroidTestDependencyOnLibTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), StandardCharsets.UTF_8)
                .write("include 'app', 'library', 'library2'");

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    implementation project(':library2')\n"
                        + "    implementation 'com.android.support.constraint:constraint-layout:"
                        + TestVersions.TEST_CONSTRAINT_LAYOUT_VERSION
                        + "'\n"
                        + "    androidTestImplementation project(':library')\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    implementation 'com.android.support.constraint:constraint-layout:"
                        + TestVersions.TEST_CONSTRAINT_LAYOUT_VERSION
                        + "'\n"
                        + "}\n");

        // Use the constraint layout dependency to check it is included in the app android test
        // resource compilation.
        Files.asCharSink(
                        FileUtils.join(
                                project.getSubproject("library").getMainSrcDir().getParentFile(),
                                "res",
                                "layout",
                                "liblayout.xml"),
                        Charsets.UTF_8)
                .write(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<android.support.constraint.ConstraintLayout "
                                + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                                + "    android:orientation=\"horizontal\"\n"
                                + "    android:layout_width=\"fill_parent\"\n"
                                + "    android:layout_height=\"fill_parent\">\n"
                                + "\n"
                                + "    <TextView\n"
                                + "    android:layout_width=\"wrap_content\"\n"
                                + "    android:layout_height=\"wrap_content\"\n"
                                + "    android:text=\"Hello World!\"\n"
                                + "    app:layout_constraintBottom_toBottomOf=\"parent\"\n"
                                + "    app:layout_constraintLeft_toLeftOf=\"parent\"\n"
                                + "    app:layout_constraintRight_toRightOf=\"parent\"\n"
                                + "    app:layout_constraintTop_toTopOf=\"parent\" />\n"
                                + "</android.support.constraint.ConstraintLayout>");
    }

    @Test
    public void checkResourcesLinking() throws Exception {
        // This test asserts the following:
        //
        // |-------------------|-------------------------------------------|
        // | Resources from    | Will be included in                       |
        // |-------------------|-------------------------------------------|
        // | :app main         | Main (by definition)                      |
        // | :app test         | Test (by definition)                      |
        // | :library main     | Test (directly)                           |
        // | :library2 main    | Main (directly) **                        |
        // | contraint layout  | Main (directly) and test * (via :library) |
        // ----------------------------------------------------------------|
        // *  In 3.0-alphas, each androidTest configuration now extended the main
        //    configuration, and all the resource also present in the application
        //    were filtered out for tests.  This was the right thing to do for
        //    classes, but not for resources, and lead to
        //    https://issuetracker.google.com/65175674.
        //
        // ** The initial fix for the issue in
        //    ag/I971a3894b3272a189fa0f54e27d6d3995db88378 was to include all
        //    resources in the test, which bloats the test APK significantly and
        //    broke the corner case of https://issuetracker.google.com/68275433
        project.executor().run(":app:assembleDebug", ":app:assembleDebugAndroidTest");

        Apk debugApk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(debugApk).containsResource("layout/lib2layout.xml");
        // Sanity check - liblayout comes from library, which is test only
        assertThat(debugApk).doesNotContainResource("layout/liblayout.xml");

        Apk debugAndroidTestApk = project.getSubproject("app").getTestApk();
        assertThat(debugAndroidTestApk).containsResource("layout/liblayout.xml");
        // As tests do not directly depend on library2, this layout should not be included.
        assertThat(debugAndroidTestApk).doesNotContainResource("layout/lib2layout.xml");
    }
}
