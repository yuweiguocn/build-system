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

package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for the cruncherEnabled settings. */
public class NoCruncherTest {

    @ClassRule
    public static GradleTestProject noPngCrunch =
            GradleTestProject.builder()
                    .withName("noPngCrunch")
                    .fromTestProject("noPngCrunch")
                    .create();

    @Test
    public void checkNotCrunched() throws Exception {
        noPngCrunch.executor().run("assembleDebug", "assembleRelease");
        // Check "crunchable" PNG is not crunched
        checkResource(ApkType.DEBUG, "drawable/icon.png", false);
        checkResource(ApkType.DEBUG, "drawable/lib_bg.9.png", true);
        checkResource(ApkType.RELEASE, "drawable/icon.png", false);
        checkResource(ApkType.RELEASE, "drawable/lib_bg.9.png", true);
    }

    @Test
    public void checkBuildTypeDefaultsEnable() throws Exception {
        TemporaryProjectModification.doTest(
                noPngCrunch,
                projectModification -> {
                    projectModification.replaceInFile(
                            "build.gradle", "cruncherEnabled = false", "//cruncherEnabled = false");
                    noPngCrunch.executor().run("assembleDebug", "assembleRelease", "assembleQa");
                    checkResource(ApkType.DEBUG, "drawable/icon.png", false);
                    checkResource(ApkType.RELEASE, "drawable/icon.png", true);
                    // QA is debuggable, but inits from release, so the cruncher is default enabled.
                    checkResource(ApkType.of("qa", false), "drawable/icon.png", true);
                });
    }

    @Test
    public void checkBuildTypeOverride() throws Exception {
        TemporaryProjectModification.doTest(
                noPngCrunch,
                projectModification -> {
                    projectModification.replaceInFile(
                            "build.gradle", "cruncherEnabled = false", "//cruncherEnabled = false");
                    // QA is debuggable, but inits from release, so the cruncher is default enabled,
                    // but is is explicitly disabled here.
                    projectModification.replaceInFile(
                            "build.gradle", "// crunchPngs false", "crunchPngs false");
                    noPngCrunch.executor().run("assembleQa");
                    checkResource(ApkType.of("qa", false), "drawable/icon.png", false);
                });
    }

    private static void checkResource(
            @NonNull ApkType apkType, @NonNull String fileName, boolean shouldBeProcessed)
            throws IOException {
        Path srcFile = noPngCrunch.file("src/main/res/" + fileName).toPath();
        Path destFile = noPngCrunch.getApk(apkType).getResource(fileName);
        assertThat(srcFile).exists();
        assertThat(destFile).exists();

        if (shouldBeProcessed) {
            assertThat(Files.readAllBytes(destFile)).isNotEqualTo(Files.readAllBytes(srcFile));
        } else {
            assertThat(destFile).hasContents(Files.readAllBytes(srcFile));
        }
    }
}
