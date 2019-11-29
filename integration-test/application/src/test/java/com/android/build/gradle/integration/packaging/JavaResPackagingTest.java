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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * test for packaging of java resources.
 */
public class JavaResPackagingTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;
    private GradleTestProject libProject2;
    private GradleTestProject testProject;
    private GradleTestProject jarProject;

    @Before
    public void setUp() throws Exception {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");
        libProject2 = project.getSubproject("library2");
        testProject = project.getSubproject("test");
        jarProject = project.getSubproject("jar");

        // rewrite settings.gradle to remove un-needed modules
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write(
                        "include 'app'\n"
                                + "include 'library'\n"
                                + "include 'library2'\n"
                                + "include 'test'\n"
                                + "include 'jar'\n");

        // setup dependencies.
        appendToFile(appProject.getBuildFile(),
                "android {\n"
                + "    publishNonDefault true\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':library')\n"
                + "    compile project(':jar')\n"
                + "}\n");

        appendToFile(libProject.getBuildFile(),
                "dependencies {\n"
                + "    compile project(':library2')\n"
                + "}\n");

        appendToFile(testProject.getBuildFile(),
                "android {\n"
                + "    targetProjectPath ':app'\n"
                + "}\n");

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getTestDir();
        createOriginalResFile(appDir,  "main",        "app.txt",         "app:abcd");
        createOriginalResFile(appDir,  "androidTest", "apptest.txt",     "appTest:abcd");

        File testDir = testProject.getTestDir();
        createOriginalResFile(testDir, "main",        "test.txt",        "test:abcd");

        File libDir = libProject.getTestDir();
        createOriginalResFile(libDir,  "main",        "library.txt",      "library:abcd");
        createOriginalResFile(libDir,  "androidTest", "librarytest.txt",  "libraryTest:abcd");

        File lib2Dir = libProject2.getTestDir();
        createOriginalResFile(lib2Dir, "main",        "library2.txt",     "library2:abcd");
        createOriginalResFile(lib2Dir, "androidTest", "library2test.txt", "library2Test:abcd");

        File jarDir = jarProject.getTestDir();
        File resFolder = FileUtils.join(jarDir, "src", "main", "resources", "com", "foo");
        FileUtils.mkdirs(resFolder);
        Files.asCharSink(new File(resFolder, "jar.txt"), Charsets.UTF_8).write("jar:abcd");
    }

    @After
    public void cleanUp() {
        project = null;
        appProject = null;
        testProject = null;
        libProject = null;
        libProject2 = null;
        jarProject = null;
    }

    private static void createOriginalResFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content)
            throws Exception {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "resources", "com", "foo");
        FileUtils.mkdirs(assetFolder);
        Files.asCharSink(new File(assetFolder, filename), Charsets.UTF_8).write(content);
    }

    private void execute(String... tasks) throws IOException, InterruptedException {
        project.executor().run(tasks);
    }

    @Test
    public void testNonIncrementalPackaging() throws Exception {
        execute("clean", "assembleDebug", "assembleAndroidTest");

        // check the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "library2.txt",     "library2:abcd");
        checkTestApk(libProject2, "library2.txt",     "library2:abcd");
        checkTestApk(libProject2, "library2test.txt", "library2Test:abcd");

        checkAar(    libProject,  "library.txt",     "library:abcd");
        // aar does not contain dependency's assets
        checkAar(    libProject, "library2.txt",     null);
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "library.txt",      "library:abcd");
        checkTestApk(libProject, "library2.txt",     "library2:abcd");
        checkTestApk(libProject, "librarytest.txt",  "libraryTest:abcd");
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "library2test.txt", null);

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "app.txt",          "app:abcd");
        checkApk(    appProject, "library.txt",      "library:abcd");
        checkApk(    appProject, "library2.txt",     "library2:abcd");
        checkApk(    appProject, "jar.txt",          "jar:abcd");
        checkTestApk(appProject, "apptest.txt",      "appTest:abcd");
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "librarytest.txt",  null);
        checkTestApk(appProject, "library2test.txt", null);
    }

    // ---- APP DEFAULT ---

    @Test
    public void testAppProjectWithNewResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/main/resources/com/foo/newapp.txt", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "newapp.txt", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.removeFile("src/main/resources/com/foo/app.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", null);
        });
    }

    @Test
    public void testAppProjectWithModifiedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.replaceFile("src/main/resources/com/foo/app.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithNewDebugResFileOverridingMain() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/resources/com/foo/app.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "app.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithNewResFileOverridingDependency() throws Exception {
        String resourcePath = "src/main/resources/com/foo/library.txt";

        execute("app:clean", "app:assembleDebug");
        checkApk(appProject, "library.txt", "library:abcd");


        doTest(appProject, project -> {
            project.addFile(resourcePath, "new content");
            assertThat(appProject.file(resourcePath)).exists();
            execute("app:assembleDebug");

            checkApk(appProject, "library.txt", "new content");
        });

        // Trying to figure out why the test is flaky?
        assertThat(appProject.file(resourcePath)).doesNotExist();

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "library.txt", "library:abcd");
    }

    @Test
    public void testAppProjectWithNewResFileInDebugSourceSet() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/resources/com/foo/app.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "app.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithModifiedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/resources/com/foo/library.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "library.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithAddedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/resources/com/foo/newlibrary.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "newlibrary.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithRemovedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/resources/com/foo/library.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "library.txt", null);
        });
    }

    // ---- APP TEST ---

    @Test
    public void testAppProjectTestWithNewResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.addFile("src/androidTest/resources/com/foo/newapp.txt", "new file content");
            execute("app:assembleAT");

            checkTestApk(appProject, "newapp.txt", "new file content");
        });
    }

    @Test
    public void testAppProjectTestWithRemovedResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.removeFile("src/androidTest/resources/com/foo/apptest.txt");
            execute("app:assembleAT");

            checkTestApk(appProject, "apptest.txt", null);
        });
    }

    @Test
    public void testAppProjectTestWithModifiedResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.replaceFile("src/androidTest/resources/com/foo/apptest.txt", "new content");
            execute("app:assembleAT");

            checkTestApk(appProject, "apptest.txt", "new content");
        });
    }

    // ---- LIB DEFAULT ---

    @Test
    public void testLibProjectWithNewResFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/resources/com/foo/newlibrary.txt", "newfile content");
            execute("library:assembleDebug");

            checkAar(libProject, "newlibrary.txt", "newfile content");
        });
    }

    @Test
    public void testLibProjectWithRemovedFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/resources/com/foo/library.txt");
            execute("library:assembleDebug");

            checkAar(libProject, "library.txt", null);
        });
    }

    @Test
    public void testLibProjectWithModifiedResFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/resources/com/foo/library.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "library.txt", "new content");
        });
    }

    @Test
    public void testLibProjectWithNewResFileInDebugSourceSet() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/debug/resources/com/foo/library.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "library.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug");
        checkAar(libProject, "library.txt", "library:abcd");

    }

    // ---- LIB TEST ---

    @Test
    public void testLibProjectTestWithNewResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/resources/com/foo/newlibrary.txt", "new file content");
            execute("library:assembleAT");

            checkTestApk(libProject, "newlibrary.txt", "new file content");
        });
    }

    @Test
    public void testLibProjectTestWithRemovedResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.removeFile("src/androidTest/resources/com/foo/librarytest.txt");
            execute("library:assembleAT");

            checkTestApk(libProject, "librarytest.txt", null);
        });
    }

    @Test
    public void testLibProjectTestWithModifiedResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.replaceFile("src/androidTest/resources/com/foo/librarytest.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "librarytest.txt", "new content");
        });
    }

    @Test
    public void testLibProjectTestWithNewResFileOverridingTestedLib() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/resources/com/foo/library.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "library.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "library.txt", "library:abcd");
    }

    @Test
    public void testLibProjectTestWtihNewResFileOverridingDependency() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/resources/com/foo/library2.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "library2.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "library2.txt", "library2:abcd");
    }

    // ---- TEST DEFAULT ---

    @Test
    public void testTestProjectWithNewResFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.addFile("src/main/resources/com/foo/newtest.txt", "newfile content");
            execute("test:assembleDebug");

            checkApk(testProject, "newtest.txt", "newfile content");
        });
    }

    @Test
    public void testTestProjectWithRemovedResFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.removeFile("src/main/resources/com/foo/test.txt");
            execute("test:assembleDebug");

            checkApk(testProject, "test.txt", null);
        });
    }

    @Test
    public void testTestProjectWithModifiedResFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.replaceFile("src/main/resources/com/foo/test.txt", "new content");
            execute("test:assembleDebug");

            checkApk(testProject, "test.txt", "new content");
        });
    }

    // --------------------------------

    /**
     * check an apk has (or not) the given res file name.
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
        check(assertThat(project.getApk("debug")), filename, content);
    }

    /**
     * check a test apk has (or not) the given res file name.
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
        check(assertThat(project.getTestApk()), filename, content);
    }

    /**
     * check an aat has (or not) the given res file name.
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
        check(assertThat(project.getAar("debug")), filename, content);
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        if (content != null) {
            subject.containsJavaResourceWithContent("com/foo/" + filename, content);
        } else {
            subject.doesNotContainJavaResource("com/foo/" + filename);
        }
    }
}
