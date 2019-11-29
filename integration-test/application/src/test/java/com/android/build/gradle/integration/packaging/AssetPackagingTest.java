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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * test for packaging of asset files.
 */
public class AssetPackagingTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .withDependencyChecker(false)
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;
    private GradleTestProject libProject2;
    private GradleTestProject testProject;

    @Before
    public void setUp() throws Exception {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");
        libProject2 = project.getSubproject("library2");
        testProject = project.getSubproject("test");

        // rewrite settings.gradle to remove un-needed modules
        Files.write(project.getSettingsFile().toPath(),
                Arrays.asList(
                        "include 'app'",
                        "include 'library'",
                        "include 'library2'",
                        "include 'test'"));

        // setup dependencies.
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    publishNonDefault true\n"
                        + "\n"
                        + "    aaptOptions {}\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile project(':library')\n"
                        + "}");

        TestFileUtils.appendToFile(libProject.getBuildFile(), "\n"
                + "dependencies {\n"
                + "    compile project(':library2')\n"
                + "}");
        TestFileUtils.appendToFile(testProject.getBuildFile(), "\n"
                + "android {\n"
                + "    targetProjectPath ':app'\n"
                + "    targetVariant 'debug'\n"
                + "}");

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getTestDir();
        createOriginalAsset(createAssetFile(appDir, "main", "file.txt"), "app:abcd");
        createOriginalAsset(createAssetFile(appDir, "main", "subdir", "file.txt"), "app:defg");
        createOriginalAsset(createAssetFile(appDir, "main", "_anotherdir", "file.txt"), "app:hijk");
        createOriginalAsset(createAssetFile(appDir, "androidTest", "filetest.txt"), "appTest:abcd");

        File testDir = testProject.getTestDir();
        createOriginalAsset(createAssetFile(testDir, "main", "file.txt"), "test:abcd");

        File libDir = libProject.getTestDir();
        createOriginalAsset(createAssetFile(libDir, "main", "filelib.txt"), "library:abcd");
        createOriginalAsset(
                createAssetFile(libDir, "androidTest", "filelibtest.txt"), "libraryTest:abcd");

        File lib2Dir = libProject2.getTestDir();
        // Include a gzipped asset, which should be extracted.
        createOriginalGzippedAsset(
                createAssetFile(lib2Dir, "main", "filelib2.txt.gz"),
                "library2:abcd".getBytes(Charsets.UTF_8));
        createOriginalAsset(
                createAssetFile(lib2Dir, "androidTest", "filelib2test.txt"), "library2Test:abcd");
    }

    @After
    public void cleanUp() {
        project = null;
        appProject = null;
        testProject = null;
        libProject = null;
        libProject2 = null;
    }

    private void execute(@NonNull String... tasks) throws IOException, InterruptedException {
        project.executor().run(tasks);
    }

    private static void createOriginalAsset(@NonNull File assetFile, @NonNull String content)
            throws Exception {
        createOriginalAsset(assetFile, content.getBytes(Charsets.UTF_8));
    }

    @SuppressWarnings("SameParameterValue") // Helper function, ready for future tests.
    private static void createOriginalGzippedAsset(@NonNull File assetFile, @NonNull byte[] content)
            throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(byteArrayOutputStream)) {
            out.write(content);
        }
        createOriginalAsset(assetFile, byteArrayOutputStream.toByteArray());
    }

    private static void createOriginalAsset(@NonNull File assetFile, @NonNull byte[] content)
            throws Exception {
        Path assetFolder = assetFile.getParentFile().toPath();
        Files.createDirectories(assetFolder);
        Files.write(assetFile.toPath(), content);
    }

    private static File createAssetFile(
            @NonNull File projectDirectory, @NonNull String dimension, @NonNull String... path) {
        File assetBase = FileUtils.join(projectDirectory, "src", dimension, "assets");
        return FileUtils.join(assetBase, Arrays.asList(path));
    }

    @Test
    public void testNonIncrementalPackaging() throws Exception {
        execute("assembleDebug", "assembleAndroidTest");

        // check the files are there. Start from the bottom of the dependency graph
        checkAar(libProject2, "filelib2.txt", "library2:abcd");
        checkTestApk(libProject2, "filelib2.txt", "library2:abcd");
        checkTestApk(libProject2, "filelib2test.txt", "library2Test:abcd");

        checkAar(libProject, "filelib.txt", "library:abcd");
        // aar does not contain dependency's assets
        checkAar(libProject, "filelib2.txt", null);
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "filelib.txt", "library:abcd");
        checkTestApk(libProject, "filelib2.txt", "library2:abcd");
        checkTestApk(libProject, "filelibtest.txt", "libraryTest:abcd");
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "filelib2test.txt", null);

        // app contain own assets + all dependencies' assets.
        checkApk(appProject, "file.txt", "app:abcd");
        checkApk(appProject, "subdir/file.txt", "app:defg");
        checkApk(appProject, "filelib.txt", "library:abcd");
        checkApk(appProject, "filelib2.txt", "library2:abcd");
        // This should be null because of the default AaptOptions.ignoreAssetsPattern
        checkApk(appProject, "_anotherdir/file.txt", null);
        checkTestApk(appProject, "filetest.txt", "appTest:abcd");
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "filelibtest.txt", null);
        checkTestApk(appProject, "filelib2test.txt", null);
    }

    // ---- APP DEFAULT ---

    @Test
    public void testAppProjectWithNewAssetFile() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/main/assets/newfile.txt", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetFile() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.removeFile("src/main/assets/file.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", null);
        });
    }

    @Test
    public void testAppProjectWithModifiedAssetFile() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.replaceFile("src/main/assets/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithNewDebugAssetFileOverridingMain() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/debug/assets/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "file.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithNewAssetFileOverridingDependency() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/main/assets/filelib.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "filelib.txt", "library:abcd");
    }

    @Test
    public void testAppProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/debug/assets/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "file.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithModifiedAssetInDependency() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.replaceFile("src/main/assets/filelib.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithAddedAssetInDependency() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/main/assets/new_lib_file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "new_lib_file.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetInDependency() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.removeFile("src/main/assets/filelib.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", null);
        });
    }

    @Test
    public void testAppProjectWithAddedAssetThatOverrideAaptOptions() throws Exception {
        TemporaryProjectModification.doTest(
                appProject,
                it -> {
                    it.replaceInFile(
                            appProject.getBuildFile().getPath(),
                            "aaptOptions \\{\\}",
                            "aaptOptions \\{ ignoreAssetsPattern \"!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~\" \\}");

                    // Override AaptOptions and check that the file has been included.
                    execute("app:assembleDebug");
                    checkApk(appProject, "_anotherdir/file.txt", "app:hijk");

                    // Another run with more files and they all should be included too as part of
                    // incremental build.
                    it.addFile("src/main/assets/_file.txt", "app:1234");
                    it.addFile("src/main/assets/_anotherdir/_file.txt", "app:5678");
                    it.addFile("src/main/assets/_onemoredir/file.txt", "app:9012");
                    execute("app:assembleDebug");
                    checkApk(appProject, "_anotherdir/file.txt", "app:hijk");
                    checkApk(appProject, "_file.txt", "app:1234");
                    checkApk(appProject, "_anotherdir/_file.txt", "app:5678");
                    checkApk(appProject, "_onemoredir/file.txt", "app:9012");
                });
    }

    @Test
    @Ignore("http://b.android.com/238185")
    public void testAppProjectWithAddedAndRemovedAsset() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(
                appProject,
                it -> {
                    it.addFile("src/main/assets/newFile.txt", "foo");
                    execute("app:assembleDebug");

                    checkApk(appProject, "newFile.txt", "foo");
                });

        // Asset file has been removed. Check it's removed from the APK after another inc build.
        execute("app:assembleDebug");
        checkApk(appProject, "newFile.txt", null);
    }

    // ---- APP TEST ---

    @Test
    public void testAppProjectTestWithNewAssetFile() throws Exception {
        execute("app:assembleAT");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/androidTest/assets/newfile.txt", "new file content");
            execute("app:assembleAT");

            checkTestApk(appProject, "newfile.txt", "new file content");
        });
    }

    @Test
    public void testAppProjectTestWithRemovedAssetFile() throws Exception {
        execute("app:assembleAT");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.removeFile("src/androidTest/assets/filetest.txt");
            execute("app:assembleAT");

            checkTestApk(appProject, "filetest.txt", null);
        });
    }

    @Test
    public void testAppProjectTestWithModifiedAssetFile() throws Exception {
        execute("app:assembleAT");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.replaceFile("src/androidTest/assets/filetest.txt", "new content");
            execute("app:assembleAT");

            checkTestApk(appProject, "filetest.txt", "new content");
        });
    }

    // ---- LIB DEFAULT ---

    @Test
    public void testLibProjectWithNewAssetFile() throws Exception {
        execute("library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/main/assets/newfile.txt", "newfile content");
            execute("library:assembleDebug");

            checkAar(libProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testLibProjectWithRemovedAssetFile() throws Exception {
        execute("library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.removeFile("src/main/assets/filelib.txt");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", null);
        });
    }

    @Test
    public void testLibProjectWithModifiedAssetFile() throws Exception {
        execute("library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.replaceFile("src/main/assets/filelib.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", "new content");
        });
    }

    @Test
    public void testLibProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("library:assembleDebug");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/debug/assets/filelib.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug");
        checkAar(libProject, "filelib.txt", "library:abcd");
    }

    // ---- LIB TEST ---

    @Test
    public void testLibProjectTestWithNewAssetFile() throws Exception {
        execute("library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/androidTest/assets/newfile.txt", "new file content");
            execute("library:assembleAT");

            checkTestApk(libProject, "newfile.txt", "new file content");
        });
    }

    @Test
    public void testLibProjectTestWithRemovedAssetFile() throws Exception {
        execute("library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.removeFile("src/androidTest/assets/filelibtest.txt");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelibtest.txt", null);
        });
    }

    @Test
    public void testLibProjectTestWithModifiedAssetFile() throws Exception {
        execute("library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.replaceFile("src/androidTest/assets/filelibtest.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelibtest.txt", "new content");
        });
    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingTestedLib() throws Exception {
        execute("library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/androidTest/assets/filelib.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "filelib.txt", "library:abcd");

    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingDependency() throws Exception {
        execute("library:assembleAT");

        TemporaryProjectModification.doTest(libProject, it -> {
            it.addFile("src/androidTest/assets/filelib2.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelib2.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "filelib2.txt", "library2:abcd");
    }

    // ---- TEST DEFAULT ---

    @Test
    public void testTestProjectWithNewAssetFile() throws Exception {
        execute("test:assembleDebug");

        TemporaryProjectModification.doTest(testProject, it -> {
            it.addFile("src/main/assets/newfile.txt", "newfile content");
            execute("test:assembleDebug");

            checkApk(testProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testTestProjectWithRemovedAssetFile() throws Exception {
        execute("test:assembleDebug");

        TemporaryProjectModification.doTest(testProject, it -> {
            it.removeFile("src/main/assets/file.txt");
            execute("test:assembleDebug");

            checkApk(testProject, "file.txt", null);
        });
    }

    @Test
    public void testTestProjectWithModifiedAssetFile() throws Exception {
        execute("test:assembleDebug");

        TemporaryProjectModification.doTest(testProject, it -> {
            it.replaceFile("src/main/assets/file.txt", "new content");
            execute("test:assembleDebug");

            checkApk(testProject, "file.txt", "new content");
        });
    }

    // -----------------------

    @Test
    public void testPackageAssetsWithUnderscoreRegression() throws Exception {
        execute("app:assembleDebug");

        TemporaryProjectModification.doTest(appProject, it -> {
            it.addFile("src/main/assets/_newfile.txt", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "_newfile.txt", "newfile content");
        });
    }

    @Test
    public void testIgnoreAssets() throws Exception {
        File projectFile = appProject.getBuildFile();
        TestFileUtils.appendToFile(
                projectFile,
                "android { aaptOptions { ignoreAssets '*a:b*' } } ");

        byte[] aaData = new byte[] { 'e' };
        byte[] abData = new byte[] { 'f' };
        byte[] baData = new byte[] { 'g' };
        byte[] bbData = new byte[] { 'h' };

        File aaAsset = FileUtils.join(appProject.getTestDir(), "src", "main", "assets", "aa");
        File abAsset = FileUtils.join(appProject.getTestDir(), "src", "main", "assets", "ab");
        File baAsset = FileUtils.join(appProject.getTestDir(), "src", "main", "assets", "ba");
        File bbAsset = FileUtils.join(appProject.getTestDir(), "src", "main", "assets", "bb");

        FileUtils.mkdirs(aaAsset.getParentFile());
        FileUtils.mkdirs(abAsset.getParentFile());
        FileUtils.mkdirs(baAsset.getParentFile());
        FileUtils.mkdirs(bbAsset.getParentFile());

        Files.write(aaAsset.toPath(), aaData);
        Files.write(abAsset.toPath(), abData);
        Files.write(baAsset.toPath(), baData);
        Files.write(bbAsset.toPath(), bbData);

        execute("app:assembleDebug");

        TruthHelper.assertThat(appProject.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("assets/aa");
        TruthHelper.assertThat(appProject.getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("assets/ab", abData);
        TruthHelper.assertThat(appProject.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("assets/ba");
        TruthHelper.assertThat(appProject.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("assets/bb");
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(
                TruthHelper.assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)),
                filename,
                content);
    }

    /**
     * check a test apk has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkTestApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(TruthHelper.assertThat(project.getTestApk()), filename, content);
    }

    /**
     * check an aat has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(TruthHelper.assertThat(project.getAar("debug")), filename, content);
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        if (content != null) {
            subject.containsFileWithContent("assets/" + filename, content);
        } else {
            subject.doesNotContain("assets/" + filename);
        }
    }

    private static void check(
            @NonNull ApkSubject subject, @NonNull String filename, @Nullable String content)
            throws Exception {
        if (content != null) {
            subject.containsFileWithContent("assets/" + filename, content);
        } else {
            subject.doesNotContain("assets/" + filename);
        }
    }
}
