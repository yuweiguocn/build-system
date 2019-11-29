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
import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for PNG generation in case of libraries. */
public class VectorDrawableTest_Library {

    public static final String VECTOR_XML_CONTENT =
            "\n"
                    + "        <vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                    + "            android:height=\"256dp\"\n"
                    + "            android:width=\"256dp\"\n"
                    + "            android:viewportWidth=\"32\"\n"
                    + "            android:viewportHeight=\"32\">\n"
                    + "\n"
                    + "            <path\n"
                    + "                android:fillColor=\"#ff0000\"\n"
                    + "                android:pathData=\"M20.5,9.5\n"
                    + "                                c-1.965,0,-3.83,1.268,-4.5,3\n"
                    + "                                c-0.17,-1.732,-2.547,-3,-4.5,-3\n"
                    + "                                C8.957,9.5,7,11.432,7,14\n"
                    + "                                c0,3.53,3.793,6.257,9,11.5\n"
                    + "                                c5.207,-5.242,9,-7.97,9,-11.5\n"
                    + "                                C25,11.432,23.043,9.5,20.5,9.5z\" />\n"
                    + "        </vector>\n"
                    + "        ";

    public static final String VECTOR_XML_PATH = "src/main/res/drawable/lib_vector.xml";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(
                            new MultiModuleTestProject(
                                    ImmutableMap.of(
                                            ":app",
                                            HelloWorldApp.forPlugin("com.android.application"),
                                            ":lib",
                                            new EmptyAndroidTestApp("com.example.lib"))))
                    .create();

    @Before
    public void checkBuildTools() {
        AssumeUtil.assumeBuildToolsAtLeast(21);
    }

    @Before
    public void setUpApp() throws IOException {
        GradleTestProject app = project.getSubproject(":app");
        TestFileUtils.appendToFile(app.getBuildFile(), "dependencies { compile project(':lib') }");

        Files.createParentDirs(app.file("src/main/res/drawable/app_vector.xml"));
        TestFileUtils.appendToFile(
                app.file("src/main/res/drawable/app_vector.xml"), VECTOR_XML_CONTENT);
    }

    @Before
    public void setUpLib() throws IOException {
        GradleTestProject lib = project.getSubproject(":lib");
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "\n"
                        + "        apply plugin: \"com.android.library\"\n"
                        + "\n"
                        + "        android {\n"
                        + "            compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "            buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        }\n"
                        + "        ");

        Files.createParentDirs(lib.file(VECTOR_XML_PATH));
        TestFileUtils.appendToFile(lib.file(VECTOR_XML_PATH), VECTOR_XML_CONTENT);
    }

    @Test
    public void libUsesSupportLibraryWhileAppDoesNot() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                "\n"
                        + "                android.defaultConfig.vectorDrawables {\n"
                        + "                    useSupportLibrary = true\n"
                        + "                }\n"
                        + "        ");

        project.execute(":app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);

        assertThat(apk).containsResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable/app_vector.xml");

        assertThat(apk).containsResource("drawable/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/lib_vector.png");

        modifyVector();

        // Verify incremental build.
        project.execute(":app:assembleDebug");

        assertThat(apk).containsResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable/app_vector.xml");

        assertThat(apk).containsResource("drawable/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/lib_vector.png");
    }

    private void modifyVector() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getSubproject(":lib").file(VECTOR_XML_PATH), "ff0000", "00ff00");
    }

    @Test
    public void appUsesSupportLibraryWhileLibDoesNot() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "\n"
                        + "                android.defaultConfig.vectorDrawables {\n"
                        + "                    // Try the DSL method without \"=\".\n"
                        + "                    useSupportLibrary true\n"
                        + "                }\n"
                        + "        ");

        project.execute(":app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);

        assertThat(apk).containsResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable/lib_vector.xml");

        assertThat(apk).containsResource("drawable/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/app_vector.png");

        modifyVector();

        project.execute(":app:assembleDebug");

        assertThat(apk).containsResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable/lib_vector.xml");

        assertThat(apk).containsResource("drawable/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/app_vector.png");
    }

    @Test
    public void bothUseSupportLibrary() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "\n"
                        + "                android.defaultConfig.vectorDrawables {\n"
                        + "                    useSupportLibrary = true\n"
                        + "                }\n"
                        + "        ");

        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                "\n"
                        + "                android.defaultConfig.vectorDrawables {\n"
                        + "                    useSupportLibrary = true\n"
                        + "                }\n"
                        + "        ");

        project.execute(":app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);

        assertThat(apk).containsResource("drawable/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/app_vector.png");

        assertThat(apk).containsResource("drawable/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/lib_vector.png");

        modifyVector();

        project.execute(":app:assembleDebug");
        assertThat(apk).containsResource("drawable/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/app_vector.png");

        assertThat(apk).containsResource("drawable/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/lib_vector.png");
    }

    @Test
    public void noneUseSupportLibrary() throws Exception {
        project.execute(":app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);

        assertThat(apk).containsResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable/app_vector.xml");

        assertThat(apk).containsResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable/lib_vector.xml");

        modifyVector();

        project.execute(":app:assembleDebug");

        assertThat(apk).containsResource("drawable-anydpi-v21/app_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/app_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/app_vector.png");
        assertThat(apk).doesNotContainResource("drawable/app_vector.xml");

        assertThat(apk).containsResource("drawable-anydpi-v21/lib_vector.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/lib_vector.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/lib_vector.png");
        assertThat(apk).doesNotContainResource("drawable/lib_vector.xml");
    }

    @Test
    public void appGeneratedResourceOverridesLibraryGeneratedResource() throws Exception {
        GradleTestProject app = project.getSubproject(":app");
        GradleTestProject lib = project.getSubproject(":lib");
        String blue = "#00ffff";
        String red = "#ff0000";

        TestFileUtils.appendToFile(
                app.file("src/main/res/drawable/my_vector.xml"),
                VECTOR_XML_CONTENT.replace(red, blue));
        TestFileUtils.appendToFile(
                lib.file("src/main/res/drawable/my_vector.xml"), VECTOR_XML_CONTENT);

        project.execute(":app:assembleDebug");

        File generatedXmlApp =
                FileUtils.join(
                        app.getTestDir(),
                        "build",
                        "generated",
                        "res",
                        "pngs",
                        "debug",
                        "drawable-anydpi-v21",
                        "my_vector.xml");

        File generatedXmlLib =
                FileUtils.join(
                        lib.getTestDir(),
                        "build",
                        "generated",
                        "res",
                        "pngs",
                        "debug",
                        "drawable-anydpi-v21",
                        "my_vector.xml");

        assertThat(generatedXmlApp).isNotSameAs(generatedXmlLib);

        // Library color should be red and should be overridden in the app by the color blue.
        assertThat(generatedXmlApp).contains(blue);
        assertThat(generatedXmlApp).doesNotContain(red);
        assertThat(generatedXmlLib).contains(red);
        assertThat(generatedXmlLib).doesNotContain(blue);

        File generatedPngApp =
                FileUtils.join(
                        app.getTestDir(),
                        "build",
                        "generated",
                        "res",
                        "pngs",
                        "debug",
                        "drawable-hdpi",
                        "my_vector.png");

        File generatedPngLib =
                FileUtils.join(
                        lib.getTestDir(),
                        "build",
                        "generated",
                        "res",
                        "pngs",
                        "debug",
                        "drawable-hdpi",
                        "my_vector.png");

        // Check the generated PNGs too, just to be safe.
        assertThat(generatedPngApp).isNotSameAs(generatedPngLib);
    }
}
