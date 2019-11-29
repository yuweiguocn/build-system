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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.options.StringOption;
import java.io.File;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApkLocationTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void outputToInjectedLocation() throws Exception {
        project.executor()
                .with(StringOption.IDE_APK_LOCATION, mTemporaryFolder.getRoot().getAbsolutePath())
                .run("assembleDebug");

        File debugApkLocation = new File(mTemporaryFolder.getRoot(), "debug");
        File[] files = debugApkLocation.listFiles();
        assertNotNull(files);

        assertThat(
                        Arrays.stream(files)
                                .filter(file -> file.isFile() && file.getName().endsWith(".apk"))
                                .count())
                .isEqualTo(1);

        assertThat(
                        Arrays.stream(files)
                                .filter(
                                        file ->
                                                file.isFile()
                                                        && file.getName().equals("output.json"))
                                .count())
                .isEqualTo(1);
    }
}
