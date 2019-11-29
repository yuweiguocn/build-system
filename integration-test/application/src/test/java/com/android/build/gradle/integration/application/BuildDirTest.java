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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Checks that we obey project.buildDir in the DSL.
 *
 * <p>This means we don't read it too early, before the user had a chance to change it.
 */
public class BuildDirTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void buildDirIsObeyed() throws Exception {
        File buildDir = changeBuildDir();

        project.execute("assembleDebug");

        assertThat(buildDir).isDirectory();
        assertThat(project.file("build")).doesNotExist();
    }

    @NonNull
    private File changeBuildDir() throws IOException {
        File buildDir = temporaryFolder.newFolder();
        FileUtils.deletePath(buildDir);
        assertThat(buildDir).doesNotExist();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "project.buildDir = '%s'",
                        buildDir.getAbsolutePath().replace(File.separatorChar, '/')));
        return buildDir;
    }
}
