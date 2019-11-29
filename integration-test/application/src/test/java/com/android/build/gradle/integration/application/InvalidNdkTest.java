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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Check projects can be configured properly with an invalid NDK.
 */
public class InvalidNdkTest {
    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .withoutNdk()
            .create();


    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static File ndkDir;

    @BeforeClass
    public static void setUp() throws Exception {
        ndkDir = temporaryFolder.newFolder();
        TestFileUtils.appendToFile(
                sProject.file("local.properties"),
                "ndk.dir=" + ndkDir.getAbsolutePath().replace("\\", "\\\\"));
    }

    @Test
    public void buildSuccessFullyWithEmptyNdk() throws Exception {
        FileUtils.cleanOutputDir(ndkDir);
        sProject.executor().run("help");
    }

    @Test
    public void buildSuccessFullyWithPlatformDir() throws Exception {
        FileUtils.cleanOutputDir(ndkDir);
        FileUtils.mkdirs(new File(ndkDir, "platforms"));
        sProject.executor().run("help");
    }

}
