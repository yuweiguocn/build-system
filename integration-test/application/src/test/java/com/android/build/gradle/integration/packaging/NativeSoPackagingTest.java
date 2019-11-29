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

import static com.android.build.gradle.integration.common.fixture.TemporaryProjectModification.doTest;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * test for packaging of asset files.
 */
public class NativeSoPackagingTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;
    private GradleTestProject libProject2;
    private GradleTestProject testProject;
    private GradleTestProject jarProject;

    private void execute(String... tasks) throws Exception {
        // TODO: Remove once we understand the cause of flakiness.
        TestUtils.waitForFileSystemTick();
        project.executor().run(tasks);
    }

    @Before
    public void setUp() throws Exception {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");
        libProject2 = project.getSubproject("library2");
        testProject = project.getSubproject("test");
        jarProject = project.getSubproject("jar");

        // rewrite settings.gradle to remove un-needed modules
        Files.asCharSink(new File(project.getTestDir(), "settings.gradle"), Charsets.UTF_8)
                .write(
                        "include 'app'\n"
                                + "include 'library'\n"
                                + "include 'library2'\n"
                                + "include 'test'\n"
                                + "include 'jar'\n");

        // setup dependencies.
        TestFileUtils.appendToFile(appProject.getBuildFile(),
                "android {\n"
                + "    publishNonDefault true\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':library')\n"
                + "    compile project(':jar')\n"
                + "}\n");

        TestFileUtils.appendToFile(libProject.getBuildFile(),
                "dependencies {\n"
                + "    compile project(':library2')\n"
                + "}\n");

        TestFileUtils.appendToFile(testProject.getBuildFile(),
                "android {\n"
                + "    targetProjectPath ':app'\n"
                + "    targetVariant 'debug'\n"
                + "}\n");

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getTestDir();
        createOriginalSoFile(appDir,  "main",        "libapp.so",         "app:abcd");
        createOriginalSoFile(appDir,  "androidTest", "libapptest.so",     "appTest:abcd");

        File testDir = testProject.getTestDir();
        createOriginalSoFile(testDir, "main",        "libtest.so",        "test:abcd");

        File libDir = libProject.getTestDir();
        createOriginalSoFile(libDir,  "main",        "liblibrary.so",      "library:abcd");
        createOriginalSoFile(libDir,  "androidTest", "liblibrarytest.so",  "libraryTest:abcd");

        File lib2Dir = libProject2.getTestDir();
        createOriginalSoFile(lib2Dir, "main",        "liblibrary2.so",     "library2:abcd");
        createOriginalSoFile(lib2Dir, "androidTest", "liblibrary2test.so", "library2Test:abcd");

        File jarDir = jarProject.getTestDir();
        File resFolder = FileUtils.join(jarDir, "src", "main", "resources", "lib", "x86");
        FileUtils.mkdirs(resFolder);
        Files.asCharSink(new File(resFolder, "libjar.so"), Charsets.UTF_8).write("jar:abcd");
    }

    private static void createOriginalSoFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content)
            throws Exception {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "jniLibs", "x86");
        FileUtils.mkdirs(assetFolder);
        Files.asCharSink(new File(assetFolder, filename), Charsets.UTF_8).write(content);
    }

    @Test
    public void testNonIncrementalPackaging() throws Exception {
        execute("clean", "assembleDebug", "assembleAndroidTest");

        // check the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "liblibrary2.so",     "library2:abcd");
        checkTestApk(libProject2, "liblibrary2.so",     "library2:abcd");
        checkTestApk(libProject2, "liblibrary2test.so", "library2Test:abcd");

        checkAar(    libProject,  "liblibrary.so",     "library:abcd");
        // aar does not contain dependency's assets
        checkAar(    libProject, "liblibrary2.so",     null);
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "liblibrary.so",      "library:abcd");
        checkTestApk(libProject, "liblibrary2.so",     "library2:abcd");
        checkTestApk(libProject, "liblibrarytest.so",  "libraryTest:abcd");
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "liblibrary2test.so", null);

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "libapp.so",         "app:abcd");
        checkApk(    appProject, "liblibrary.so",      "library:abcd");
        checkApk(    appProject, "liblibrary2.so",     "library2:abcd");
        checkApk(    appProject, "libjar.so",          "jar:abcd");
        checkTestApk(appProject, "libapptest.so",     "appTest:abcd");
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "liblibrarytest.so",  null);
        checkTestApk(appProject, "liblibrary2test.so", null);
    }

    // ---- APP DEFAULT ---

    @Test
    public void testAppProjectWithNewAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewapp.so", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "libnewapp.so", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.removeFile("src/main/jniLibs/x86/libapp.so");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", null);
        });
    }

    @Test
    public void testAppProjectWithModifiedAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/libapp.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", "new content");
        });
    }

    @Test
    public void testAppProjectWithNewAssetFileOverridingDependency() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/main/jniLibs/x86/liblibrary.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary.so", "new content");

            // now remove it to test it works in the other direction
            project.removeFile("src/main/jniLibs/x86/liblibrary.so");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary.so", "library:abcd");
        });
    }

    @Test
    public void testAppProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/jniLibs/x86/libapp.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", "new content");

            // now remove it to test it works in the other direction
            project.removeFile("src/debug/jniLibs/x86/libapp.so");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", "app:abcd");
        });
    }

    @Test
    public void testAppProjectWithModifiedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/liblibrary.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary.so", "new content");
        });
    }

    @Test
    public void testAppProjectWithAddedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewlibrary.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "libnewlibrary.so", "new content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/jniLibs/x86/liblibrary.so");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary.so", null);
        });
    }

    // ---- APP TEST ---

    @Test
    public void testAppProjectTestWithNewAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.addFile("src/androidTest/jniLibs/x86/libnewapp.so", "new file content");
            execute("app:assembleAT");

            checkTestApk(appProject, "libnewapp.so", "new file content");
        });
    }

    @Test
    public void testAppProjectTestWithRemovedAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.removeFile("src/androidTest/jniLibs/x86/libapptest.so");
            execute("app:assembleAT");

            checkTestApk(appProject, "libapptest.so", null);
        });
    }

    @Test
    public void testAppProjectTestWithModifiedAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.replaceFile("src/androidTest/jniLibs/x86/libapptest.so", "new content");
            execute("app:assembleAT");

            checkTestApk(appProject, "libapptest.so", "new content");
        });
    }

    // ---- LIB DEFAULT ---

    @Test
    public void testLibProjectWithNewAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewlibrary.so", "newfile content");
            execute("library:assembleDebug");

            checkAar(libProject, "libnewlibrary.so", "newfile content");
        });
    }

    @Test
    public void testLibProjectWithRemovedAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/jniLibs/x86/liblibrary.so");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", null);
        });
    }

    @Test
    public void testLibProjectWithModifiedAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/liblibrary.so", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", "new content");
        });
    }

    @Test
    public void testLibProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/debug/jniLibs/x86/liblibrary.so", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", "new content");

            // now remove it to test it works in the other direction
            project.removeFile("src/debug/jniLibs/x86/liblibrary.so");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", "library:abcd");
        });
    }

    // ---- LIB TEST ---

    @Test
    public void testLibProjectTestWithNewAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/jniLibs/x86/libnewlibrary.so", "new file content");
            execute("library:assembleAT");

            checkTestApk(libProject, "libnewlibrary.so", "new file content");
        });
    }

    @Test
    public void testLibProjectTestWithRemovedAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.removeFile("src/androidTest/jniLibs/x86/liblibrarytest.so");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrarytest.so", null);
        });
    }

    @Test
    public void testLibProjectTestWithModifiedAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.replaceFile("src/androidTest/jniLibs/x86/liblibrarytest.so", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrarytest.so", "new content");
        });
    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingTestedLib() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/jniLibs/x86/liblibrary.so", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrary.so", "new content");

            // now remove it to test it works in the other direction
            project.removeFile("src/androidTest/jniLibs/x86/liblibrary.so");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrary.so", "library:abcd");
        });
    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingDepenency() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/jniLibs/x86/liblibrary2.so", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrary2.so", "new content");

            // now remove it to test it works in the other direction
            project.removeFile("src/androidTest/jniLibs/x86/liblibrary2.so");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrary2.so", "library2:abcd");
        });
    }

    // ---- TEST DEFAULT ---

    @Test
    public void testTestProjectWithNewAssetFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewtest.so", "newfile content");
            execute("test:assembleDebug");

            checkApk(testProject, "libnewtest.so", "newfile content");
        });
    }

    @Test
    public void testTestProjectWithRemovedAssetFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.removeFile("src/main/jniLibs/x86/libtest.so");
            execute("test:assembleDebug");

            checkApk(testProject, "libtest.so", null);
        });
    }

    @Test
    public void testTestProjectWithModifiedAssetFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/libtest.so", "new content");
            execute("test:assembleDebug");

            checkApk(testProject, "libtest.so", "new content");
        });
    }

    // ---- SO ALIGNMENT ----
    @Test
    public void testSharedObjectFilesAlignment() throws Exception {
        TestFileUtils.searchAndReplace(
                appProject.file("src/main/AndroidManifest.xml"),
                "<application ",
                "<application android:extractNativeLibs=\"false\" ");

        execute("app:assembleDebug");
        checkApk(appProject, "libapp.so", "app:abcd");
        PackagingTests.checkZipAlignWithPageAlignedSoFiles(appProject.getApk("debug"));
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
        Apk apk = project.getApk("debug");
        check(assertThatApk(apk), "lib", filename, content);
        PackagingTests.checkZipAlign(apk);
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
    private void checkTestApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(TruthHelper.assertThat(project.getTestApk()), "lib", filename, content);
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
        check(TruthHelper.assertThat(project.getAar("debug")), "jni", filename, content);
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String folderName,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        if (content != null) {
            subject.containsFileWithContent(folderName + "/x86/" + filename, content);
        } else {
            subject.doesNotContain(folderName + "/x86/" + filename);
        }
    }
}
