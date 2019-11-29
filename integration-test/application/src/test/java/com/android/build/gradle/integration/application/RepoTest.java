/*
 * Copyright (C) 2014 The Android Open Source Project
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
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for uploadAchives with multiple projects. */
public class RepoTest {

    @ClassRule
    public static GradleTestProject app =
            GradleTestProject.builder().withName("app").fromTestProject("repo/app").create();

    @ClassRule
    public static GradleTestProject baseLibrary =
            GradleTestProject.builder()
                    .withName("baseLibrary")
                    .fromTestProject("repo/baseLibrary")
                    .create();

    @ClassRule
    public static GradleTestProject library =
            GradleTestProject.builder()
                    .withName("library")
                    .fromTestProject("repo/library")
                    .create();

    @ClassRule
    public static GradleTestProject util =
            GradleTestProject.builder().withName("util").fromTestProject("repo/util").create();

    @BeforeClass
    public static void setUp() {
        // Clean testRepo
        File testRepo = new File(app.getTestDir(), "../testrepo");
        testRepo.delete();
    }

    @AfterClass
    public static void cleanUp() {
        app = null;
        baseLibrary = null;
        library = null;
        util = null;
    }

    @Test
    public void repo() throws IOException, InterruptedException {
        util.execute("clean", "uploadArchives");
        baseLibrary.execute("clean", "uploadArchives");
        library.execute("clean", "uploadArchives");
        app.execute("clean", "assembleDebug");
    }
}
