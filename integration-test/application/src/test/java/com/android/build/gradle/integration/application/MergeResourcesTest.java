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

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;

public class MergeResourcesTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @Test
    public void mergesRawWithLibraryWithOverride() throws Exception {
        /*
         * Set app to depend on library.
         */
        File appBuild = project.getSubproject("app").getBuildFile();
        TestFileUtils.appendToFile(
                appBuild,
                "dependencies { compile project(':library') }" + System.lineSeparator());

        /*
         * Create raw/me.raw in library and see that it comes out in the apk.
         *
         * It should also show up in build/intermediates/res/merged/debug/raw/me.raw
         */
        File libraryRaw =
                FileUtils.join(project.getTestDir(), "library", "src", "main", "res", "raw");
        FileUtils.mkdirs(libraryRaw);
        Files.write(new File(libraryRaw, "me.raw").toPath(), new byte[] { 0, 1, 2 });

        project.executor().run(":app:assembleDebug");

        assertThat(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("res/raw/me.raw", new byte[] {0, 1, 2});

        File inIntermediate =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "res",
                        "merged",
                        "debug",
                        "raw_me.raw.flat");
        assertThat(inIntermediate).exists();

        /*
         * Create raw/me.raw in application and see that it comes out in the apk, overriding the
         * library's.
         *
         * The change should also show up in build/intermediates/res/merged/debug/raw/me.raw
         */

        File appRaw = FileUtils.join(project.getTestDir(), "app", "src", "main", "res", "raw");
        FileUtils.mkdirs(appRaw);
        Files.write(new File(appRaw, "me.raw").toPath(), new byte[] { 3 });

        project.executor().run(":app:assembleDebug");

        assertThat(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("res/raw/me.raw", new byte[] {3});
        assertThat(inIntermediate).exists();

        /*
         * Now, modify the library's and check that nothing changed.
         */
        File apUnderscore =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "processed_res",
                        "debug",
                        "processDebugResources",
                        "out",
                        "resources-debug.ap_");

        assertThat(apUnderscore).exists();
        File apk =
                project.getSubproject("app")
                        .getApk(GradleTestProject.ApkType.DEBUG)
                        .getFile()
                        .toFile();

        // Remember all the old timestamps
        long intermediateModified = inIntermediate.lastModified();
        long apUModified = apUnderscore.lastModified();
        //long apkModified = apk.lastModified();

        Files.write(new File(libraryRaw, "me.raw").toPath(), new byte[] {0, 1, 2, 4});

        project.executor().run(":app:assembleDebug");

        assertThat(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("res/raw/me.raw", new byte[] {3});

        assertThat(inIntermediate).wasModifiedAt(intermediateModified);
        assertThat(apUnderscore).wasModifiedAt(apUModified);
        // Sometimes fails with the APK being modified even when it shouldn't. b/37617310
        //assertThat(apk).wasModifiedAt(apkModified);
    }

    @Test
    public void removeResourceFile() throws Exception {
        /*
         * Add a resource file to the project and build it.
         */
        File raw = FileUtils.join(project.getTestDir(), "app", "src", "main", "res", "raw");
        FileUtils.mkdirs(raw);
        Files.write(new File(raw, "me.raw").toPath(), new byte[] { 0, 1, 2 });
        project.executor().run(":app:assembleDebug");

        /*
         * Check that the file is merged and in the apk.
         */
        File inIntermediate =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "res",
                        "merged",
                        "debug",
                        "raw_me.raw.flat");
        assertThat(inIntermediate).exists();

        File apUnderscore =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "processed_res",
                        "debug",
                        "processDebugResources",
                        "out",
                        "resources-debug.ap_");

        assertThat(apUnderscore).exists();
        try (Zip zip = new Zip(apUnderscore)) {
            assertThatZip(zip).containsFileWithContent("res/raw/me.raw", new byte[] {0, 1, 2});
        }

        /*
         * Remove the resource from the project and build the project incrementally.
         */
        assertTrue(new File(raw, "me.raw").delete());
        project.executor().run(":app:assembleDebug");

        /*
         * Check that the file has been removed from the intermediates and from the apk.
         */
        assertThat(inIntermediate).doesNotExist();
        try (Zip zip = new Zip(apUnderscore)) {
            assertThatZip(zip).doesNotContain("res/raw/me.raw");
        }
    }

    @Test
    public void updateResourceFile() throws Exception {
        /*
         * Add a resource file to the project and build it.
         */
        File raw = FileUtils.join(project.getTestDir(), "app", "src", "main", "res", "raw");
        FileUtils.mkdirs(raw);
        Files.write(new File(raw, "me.raw").toPath(), new byte[] { 0, 1, 2 });
        project.executor().run(":app:assembleDebug");

        /*
         * Check that the file is merged and in the apk.
         */
        File inIntermediate =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "res",
                        "merged",
                        "debug",
                        "raw_me.raw.flat");
        assertThat(inIntermediate).exists();

        File apUnderscore =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "processed_res",
                        "debug",
                        "processDebugResources",
                        "out",
                        "resources-debug.ap_");

        assertThat(apUnderscore).exists();
        try (Apk apk = new Apk(apUnderscore)) {
            assertThat(apk).containsFileWithContent("res/raw/me.raw", new byte[] {0, 1, 2});
        }

        /*
         * Change the resource file from the project and build the project incrementally.
         */
        Files.write(new File(raw, "me.raw").toPath(), new byte[] { 1, 2, 3, 4 });
        project.executor().run(":app:assembleDebug");

        /*
         * Check that the file has been updated in the intermediates directory and in the project.
         */
        assertThat(inIntermediate).exists();

        try (Apk apk = new Apk(apUnderscore)) {
            assertThat(apk).containsFileWithContent("res/raw/me.raw", new byte[] {1, 2, 3, 4});
        }
    }

    @Test
    public void replaceResourceFileWithDifferentExtension() throws Exception {
        /*
         * Add a resource file to the project and build it.
         */
        File raw = FileUtils.join(project.getTestDir(), "app", "src", "main", "res", "raw");
        FileUtils.mkdirs(raw);
        Files.write(new File(raw, "me.raw").toPath(), new byte[] { 0, 1, 2 });
        project.executor().run(":app:assembleDebug");

        /*
         * Check that the file is merged and in the apk.
         */
        File inIntermediate =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "res",
                        "merged",
                        "debug",
                        "raw_me.raw.flat");
        assertThat(inIntermediate).exists();

        File apUnderscore =
                FileUtils.join(
                        project.getSubproject("app").getTestDir(),
                        "build",
                        "intermediates",
                        "processed_res",
                        "debug",
                        "processDebugResources",
                        "out",
                        "resources-debug.ap_");

        assertThat(apUnderscore).exists();
        try (Apk apk = new Apk(apUnderscore)) {
            assertThat(apk).containsFileWithContent("res/raw/me.raw", new byte[] {0, 1, 2});
        }

        /*
         * Change the resource file with one with a different extension and build the project
         * incrementally.
         */
        assertTrue(new File(raw, "me.raw").delete());
        Files.write(new File(raw, "me.war").toPath(), new byte[] { 1, 2, 3, 4 });
        project.executor().run(":app:assembleDebug");

        /*
         * Check that the file has been updated in the intermediates directory and in the project.
         */
        assertThat(inIntermediate).doesNotExist();
        assertThat(new File(inIntermediate.getParent(), "raw_me.war.flat")).exists();
        assertThat(apUnderscore).doesNotContain("res/raw/me.raw");
        try (Apk apk = new Apk(apUnderscore)) {
            assertThat(apk).containsFileWithContent("res/raw/me.war", new byte[] {1, 2, 3, 4});
        }
    }

    @Test
    public void injectedMinSdk() throws Exception {
        GradleTestProject appProject = project.getSubproject(":app");
        File newMainLayout = appProject.file("src/main/res/layout-v23/main.xml");
        Files.createDirectories(newMainLayout.getParentFile().toPath());

        // This layout does not define the "foo" ID.
        FileUtils.createFile(
                newMainLayout,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                        + "android:orientation=\"horizontal\" "
                        + "android:layout_width=\"fill_parent\" "
                        + "android:layout_height=\"fill_parent\"> "
                        + "</LinearLayout>\n");

        TestFileUtils.addMethod(
                appProject.file("src/main/java/com/example/android/multiproject/MainActivity.java"),
                "public int useFoo() { return R.id.foo; }");

        project.executor().with(IntegerOption.IDE_TARGET_DEVICE_API, 23).run(":app:assembleDebug");
    }

    @Test
    public void testIncrementalBuildWithShrinkResources() throws Exception {
        // Regression test for http://issuetracker.google.com/65829618
        GradleTestProject appProject = project.getSubproject("app");
        File appBuildFile = project.getSubproject("app").getBuildFile();
        TestFileUtils.appendToFile(
                appBuildFile,
                "android {\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            minifyEnabled true\n"
                        + "            shrinkResources true\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    implementation 'com.android.support:appcompat-v7:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

        // Run a full build with shrinkResources enabled
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.ENABLE_R8, false)
                        .run(":app:clean", ":app:assembleDebug");
        assertThat(result.getTask(":app:mergeDebugResources")).didWork();
        long apkSizeWithShrinkResources =
                appProject.getApk(GradleTestProject.ApkType.DEBUG).getContentsSize();

        // Run an incremental build with shrinkResources disabled, the MergeResources task should
        // not be UP-TO-DATE and the apk size should be larger
        TestFileUtils.searchAndReplace(
                appBuildFile, "shrinkResources true", "shrinkResources false");
        result =
                project.executor()
                        .with(BooleanOption.ENABLE_R8, false)
                        .run(":app:assembleDebug");
        assertThat(result.getTask(":app:mergeDebugResources")).didWork();
        long apkSizeWithoutShrinkResources =
                appProject.getApk(GradleTestProject.ApkType.DEBUG).getContentsSize();
        assertThat(apkSizeWithoutShrinkResources).isGreaterThan(apkSizeWithShrinkResources);

        // Run an incremental build again with shrinkResources enabled, the MergeResources task
        // again should not be UP-TO-DATE and the apk size must be exactly the same as the first
        TestFileUtils.searchAndReplace(
                appBuildFile, "shrinkResources false", "shrinkResources true");
        result =
                project.executor()
                        .with(BooleanOption.ENABLE_R8, false)
                        .run(":app:assembleDebug");
        assertThat(result.getTask(":app:mergeDebugResources")).didWork();
        long sameApkSizeShrinkResources =
                appProject.getApk(GradleTestProject.ApkType.DEBUG).getContentsSize();
        assertThat(sameApkSizeShrinkResources).isEqualTo(apkSizeWithShrinkResources);
    }
}
