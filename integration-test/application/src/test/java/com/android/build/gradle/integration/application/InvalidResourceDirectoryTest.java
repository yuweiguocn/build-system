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
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Throwables;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class InvalidResourceDirectoryTest {

    public static final String INVALID_LAYOUT_FOLDER = "src/main/res/layout-hdpi-land";

    public static AndroidTestModule app = HelloWorldApp.noBuildFile();

    static {
        app.addFile(
                new TestSourceFile(
                        INVALID_LAYOUT_FOLDER,
                        "main.xml",
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    android:orientation=\"vertical\"\n"
                                + "    android:layout_width=\"fill_parent\"\n"
                                + "    android:layout_height=\"fill_parent\"\n"
                                + "    >\n"
                                + "<TextView\n"
                                + "    android:layout_width=\"fill_parent\"\n"
                                + "    android:layout_height=\"wrap_content\"\n"
                                + "    android:text=\"hello invalid layout world!\"\n"
                                + "    android:id=\"@+id/text\"\n"
                                + "    />\n"
                                + "</LinearLayout>"));
    }

    @Rule public GradleTestProject project = GradleTestProject.builder().fromTestApp(app).create();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion  "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "}\n");
    }

    @Test
    public void checkBuildFailureOnInvalidResourceDirectory() throws Exception {
        GradleBuildResult result = project.executor().expectFailure().run("assembleRelease");

        assertThat(result.getException()).isNotNull();
        Throwable rootCause = Throwables.getRootCause(result.getException());
        assertThat(rootCause.getMessage())
                .contains(new File(project.getTestDir(), INVALID_LAYOUT_FOLDER).getAbsolutePath());
    }
}
