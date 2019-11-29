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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for uploadAchives with multiple projects. */
public class AarWithLocalJarsTest {

    @ClassRule
    public static GradleTestProject app =
            GradleTestProject.builder().fromTestProject("repo/app").withName("app").create();

    @ClassRule
    public static GradleTestProject baseLibrary =
            GradleTestProject.builder()
                    .fromTestProject("repo/baseLibrary")
                    .withName("baseLibrary")
                    .create();

    @ClassRule
    public static GradleTestProject library =
            GradleTestProject.builder()
                    .fromTestProject("repo/library")
                    .withName("library")
                    .create();

    @ClassRule
    public static GradleTestProject util =
            GradleTestProject.builder().fromTestProject("repo/util").withName("util").create();

    @BeforeClass
    public static void setUp() throws IOException {
        // Clean testRepo
        File testRepo = new File(app.getTestDir(), "../testrepo");
        //FileUtils.deleteIfExists(testRepo);
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
        // build the jar library.
        util.execute("clean", "assemble");

        // copy it inside the baseLibrary
        File from = FileUtils.join(util.getTestDir(), "build", "libs", "util-1.0.jar");
        File to = FileUtils.join(baseLibrary.getTestDir(), "libs", "util-1.0.jar");
        FileUtils.mkdirs(to.getParentFile());
        Files.copy(from, to);

        // and replace the maven dependency by a local jar dependency.
        TestFileUtils.searchAndReplace(
                baseLibrary.getBuildFile(),
                "api 'com.example.android.multiproject:util:1.0'",
                "api files('libs/util-1.0.jar')");

        // build and publish the 2 AArs and then build the app.
        baseLibrary.execute("clean", "uploadArchives");
        library.execute("clean", "uploadArchives");
        app.execute("clean", "assembleDebug");

        Apk apk = app.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();
        // check it contains classes from both the AAR's own code and from the local jar inside the AAR.
        final Optional<Dex> mainDexFile = apk.getMainDexFile();
        assertThat(mainDexFile).isPresent();
        //noinspection OptionalGetWithoutIsPresent
        assertThat(mainDexFile.get())
                .containsClasses(
                        "Lcom/example/android/multiproject/person/Person;",
                        "Lcom/example/android/multiproject/library/PersonView;");
    }
}
