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

package com.android.build.gradle.integration.packaging;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.Rule;
import org.junit.Test;
/**
 * test for incremental code change.
 *
 * It's a simple two project setup, with an app and a library. Only the library
 * gets changed and after the compilation of the app, we check code from both app and library
 * is present in the dex file of the app.
 *
 * 3 cases:
 * - no multi-dex
 * - native multi-dex
 * - legacy multi-dex
 */
public class IncrementalCodeChangeTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @Test
    public void checkNonMultiDex() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "\n"
                + "dependencies {\n"
                + "    compile project(':library')\n"
                + "}");

        project.executor().run("clean", ":app:assembleDebug");

        TestFileUtils.replaceLine(
                project.file("library/src/main/java/com/example/android/multiproject/library/PersonView.java"),
                9,
                "        setTextSize(30);");

        project.executor().run(":app:assembleDebug");

        // class from :library
        TruthHelper.assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;");

        // class from :app
        TruthHelper.assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/MainActivity;");
    }

    @Test
    public void checkLegacyMultiDex() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "\n"
                + "android {\n"
                + "    defaultConfig {\n"
                + "        multiDexEnabled = true\n"
                + "    }\n"
                + "}\n"
                + "dependencies {\n"
                + "    compile project(':library')\n"
                + "}");

        project.executor().run("clean", ":app:assembleDebug");

        TestFileUtils.replaceLine(
                project.file("library/src/main/java/com/example/android/multiproject/library/PersonView.java"),
                9,
                "        setTextSize(30);");

        project.executor().run(":app:assembleDebug");

        // class from :library
        TruthHelper.assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;");

        // class from :app
        TruthHelper.assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/MainActivity;");

        // class from legacy multi-dex lib
        TruthHelper.assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Landroid/support/multidex/MultiDex;");
    }

    @Test
    public void checkNativeMultiDex() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "\n"
                + "android {\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 21\n"
                + "        multiDexEnabled = true\n"
                + "    }\n"
                + "}\n"
                + "dependencies {\n"
                + "    compile project(':library')\n"
                + "}\n");

        project.executor().run("clean", ":app:assembleDebug");

        TestFileUtils.replaceLine(
                project.file("library/src/main/java/com/example/android/multiproject/library/PersonView.java"),
                9,
                "        setTextSize(30);");

        project.executor().run(":app:assembleDebug");

        // class from :library
        TruthHelper.assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;");

        // class from :app
        TruthHelper.assertThat(project.getSubproject("app").getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/MainActivity;");
    }
}
