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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Assemble tests for packagingOptions.
 *
 * <p>Creates two jar files and test various packaging options.
 */
public class PackagingOptionsTest {

    // Projects to create jar files.
    private static AndroidTestModule jarProject1 = new EmptyAndroidTestApp();

    static {
        jarProject1.addFile(new TestSourceFile("build.gradle", "apply plugin: 'java'"));
        jarProject1.addFile(new TestSourceFile("src/main/resources", "conflict.txt", "foo"));
        jarProject1.addFile(new TestSourceFile("META-INF/proguard", "rules.pro", "# none"));
    }

    private static AndroidTestModule jarProject2 = new EmptyAndroidTestApp();

    static {
        jarProject2.addFile(new TestSourceFile("build.gradle", "apply plugin: 'java'"));
        jarProject2.addFile(new TestSourceFile("src/main/resources", "conflict.txt", "foo"));
        // add an extra file so that jar1 is different from jar2.
        jarProject2.addFile(new TestSourceFile("src/main/resources", "dummy2.txt", "bar"));
    }

    @ClassRule
    public static GradleTestProject jar1 =
            GradleTestProject.builder().fromTestApp(jarProject1).withName("jar1").create();

    @ClassRule
    public static GradleTestProject jar2 =
            GradleTestProject.builder().fromTestApp(jarProject2).withName("jar2").create();

    @BeforeClass
    public static void createJars() throws IOException, InterruptedException {
        jar1.execute("assemble");
        jar2.execute("assemble");
    }


    // Main test project.
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Before
    public void setUp() throws IOException {
        Files.copy(jar1.file("build/libs/jar1.jar"), project.file("jar1.jar"));
        Files.copy(jar2.file("build/libs/jar2.jar"), project.file("jar2.jar"));

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "}\n");
    }

    @Test
    public void metaInfProguardExcludedByDefault() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("META-INF/proguard/rules.pro");
    }

    @Test
    public void protobufMetaExcludedByDefault() throws IOException, InterruptedException {
        createFile("src/main/resources/protobuf.meta");
        project.execute("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotContain("protobuf.meta");
    }

    @Test
    public void checkPickFirst() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    packagingOptions {\n"
                        + "        pickFirst 'conflict.txt'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile files('jar1.jar')\n"
                        + "    compile files('jar2.jar')\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).contains("conflict.txt");
    }

    @Test
    public void checkExcludeOnJars() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    packagingOptions {\n"
                        + "        exclude 'conflict.txt'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile files('jar1.jar')\n"
                        + "    compile files('jar2.jar')\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotContain("conflict.txt");
    }

    @Test
    public void checkExcludeOnDirectFiles() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    packagingOptions {\n"
                        + "        exclude 'conflict.txt'\n"
                        + "    }\n"
                        + "}\n");

        createFile("src/main/resources/conflict.txt");
        project.execute("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotContain("conflict.txt");
    }

    @Test
    public void checkMergeOnJarEntries() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    packagingOptions {\n"
                        + "        merge 'conflict.txt'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile files('jar1.jar')\n"
                        + "    compile files('jar2.jar')\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("conflict.txt", "foo\nfoo");
    }

    @Test
    public void checkMergeOnLocalResFile() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    packagingOptions {\n"
                        + "        // this will not be used since debug will override the main one.\n"
                        + "        merge 'file.txt'\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(createFile("src/main/resources/file.txt"), "main");
        TestFileUtils.appendToFile(createFile("src/debug/resources/file.txt"), "debug");
        project.execute("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("file.txt", "debug");
    }

    @Test
    public void checkMergeOnADirectFileAndAJarEntry() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n" + "dependencies {\n" + "    compile files('jar1.jar')\n" + "}\n");

        TestFileUtils.appendToFile(createFile("src/main/resources/conflict.txt"), "project-foo");

        project.execute("clean", "assembleDebug");
        // we expect to only see the one in src/main because it overrides the dependency one.
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("conflict.txt", "project-foo");
    }

    @Test
    public void checkMergeActionSpecifiedOnADirectFileAndAJarEntry()
            throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile files('jar1.jar')\n"
                        + "}\n"
                        + "\n"
                        + "android{\n"
                        + "    packagingOptions {\n"
                        + "        merge 'conflict.txt'\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(createFile("src/main/resources/conflict.txt"), "project-foo");

        project.execute("clean", "assembleDebug");
        // files should be merged
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsFileWithContent("conflict.txt", "project-foo\nfoo");
    }

    @Test
    public void throwsExceptionWithoutMergeActionForConflict()
            throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile files('jar1.jar')\n"
                        + "    compile files('jar2.jar')\n"
                        + "}\n");

        GradleBuildResult result = project.executor().expectFailure().run("clean", "assembleDebug");
        assertThat(result.getFailureMessage()).contains("conflict.txt");
    }


    /** Create a new empty file including its directories. */
    private File createFile(String filename) throws IOException {
        File newFile = project.file(filename);
        newFile.getParentFile().mkdirs();
        newFile.createNewFile();
        assertThat(newFile).exists();
        return newFile;
    }
}
