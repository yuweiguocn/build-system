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


import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Variant;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Tests for annotation processor.
 */
public class AnnotationProcessorTest {

    @Rule
    public GradleTestProject project;

    @Rule
    public Adb adb = new Adb();

    public AnnotationProcessorTest() {

        project =
                GradleTestProject.builder()
                        .fromTestApp(
                                new MultiModuleTestProject(
                                        ImmutableMap.of(
                                                ":app", sApp,
                                                ":lib",
                                                        AnnotationProcessorLib.Companion
                                                                .createLibrary(),
                                                ":lib-compiler",
                                                        AnnotationProcessorLib.Companion
                                                                .createCompiler())))
                        .create();
    }
    private static AndroidTestModule sApp = HelloWorldApp.noBuildFile();
    static {
        sApp.replaceFile(
                new TestSourceFile(
                        "src/main/java/com/example/helloworld/HelloWorld.java",
                        "package com.example.helloworld;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.widget.TextView;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.example.annotation.ProvideString;\n"
                                + "\n"
                                + "@ProvideString\n"
                                + "public class HelloWorld extends Activity {\n"
                                + "    /** Called when the activity is first created. */\n"
                                + "    @Override\n"
                                + "    public void onCreate(Bundle savedInstanceState) {\n"
                                + "        super.onCreate(savedInstanceState);\n"
                                + "        TextView tv = new TextView(this);\n"
                                + "        tv.setText(getString());\n"
                                + "        setContentView(tv);\n"
                                + "    }\n"
                                + "\n"
                                + "    public static String getString() {\n"
                                + "        return new com.example.helloworld.HelloWorldStringValue().value;\n"
                                + "    }\n"
                                + "\n"
                                + "    public static String getProcessor() {\n"
                                + "        return new com.example.helloworld.HelloWorldStringValue().processor;\n"
                                + "    }\n"
                                + "}\n"));

        sApp.removeFileByName("HelloWorldTest.java");

        sApp.addFile(
                new TestSourceFile(
                        "src/test/java/com/example/helloworld/HelloWorldTest.java",
                        "package com.example.helloworld;\n"
                                + "import com.example.annotation.ProvideString;\n"
                                + "\n"
                                + "@ProvideString\n"
                                + "public class HelloWorldTest {\n"
                                + "}\n"));

        sApp.addFile(
                new TestSourceFile(
                        "src/androidTest/java/com/example/hellojni/HelloWorldAndroidTest.java",
                        "package com.example.helloworld;\n"
                                + "\n"
                                + "import android.support.test.runner.AndroidJUnit4;\n"
                                + "import org.junit.Assert;\n"
                                + "import org.junit.Test;\n"
                                + "import org.junit.runner.RunWith;\n"
                                + "import com.example.annotation.ProvideString;\n"
                                + "\n"
                                + "@ProvideString\n"
                                + "@RunWith(AndroidJUnit4.class)\n"
                                + "public class HelloWorldAndroidTest {\n"
                                + "\n"
                                + "    @Test\n"
                                + "    public void testStringValue() {\n"
                                + "        Assert.assertTrue(\"Hello\".equals(HelloWorld.getString()));\n"
                                + "    }\n"
                                + "    @Test\n"
                                + "    public void testProcessor() {\n"
                                + "        Assert.assertTrue(\"Processor\".equals(HelloWorld.getProcessor()));\n"
                                + "    }\n"
                                + "}\n"));
    }

    @Before
    public void setUp() throws Exception {
        String buildScript =
                "\n"
                        + "apply from: \"../../commonHeader.gradle\"\n"
                        + "buildscript { apply from: \"../../commonBuildScript.gradle\" }\n"
                        + "apply from: \"../../commonLocalRepo.gradle\"\n"
                        + "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "        javaCompileOptions {\n"
                        + "            annotationProcessorOptions {\n"
                        + "                argument \"value\", \"Hello\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "        minSdkVersion rootProject.supportLibMinSdk\n"
                        + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "    androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n";
        Files.asCharSink(project.getSubproject(":app").file("build.gradle"), Charsets.UTF_8)
                .write(buildScript);
    }

    @Test
    public void normalBuild() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "dependencies {\n"
                        + "    api project(':lib')\n"
                        + "    annotationProcessor project(':lib-compiler')\n"
                        + "}\n");

        project.executor().run("assembleDebug");
        File aptOutputFolder = project.getSubproject(":app").file("build/generated/source/apt/debug");
        assertThat(new File(aptOutputFolder, "com/example/helloworld/HelloWorldStringValue.java"))
                .exists();

        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModelMap().get(":app");
        Variant debugVariant = AndroidProjectUtils.getDebugVariant(model);

        assertThat(debugVariant.getMainArtifact().getGeneratedSourceFolders())
                .contains(aptOutputFolder);

        // Ensure that test sources also have their generated sources files sent to the IDE. This
        // specifically tests for the issue described in
        // https://issuetracker.google.com/37121918.
        File testAptOutputFolder =
                project.getSubproject(":app").file("build/generated/source/apt/test/debug");
        JavaArtifact testArtifact = VariantUtils.getUnitTestArtifact(debugVariant);
        assertThat(testArtifact.getGeneratedSourceFolders()).contains(testAptOutputFolder);

        // Ensure that test projects also have their generated sources files sent to the IDE. This
        // specifically tests for the issue described in
        // https://issuetracker.google.com/37121918.
        File androidTestAptOutputFolder =
                project.getSubproject(":app").file("build/generated/source/apt/androidTest/debug");
        AndroidArtifact androidTest = VariantUtils.getAndroidTestArtifact(debugVariant);
        assertThat(androidTest.getGeneratedSourceFolders()).contains(androidTestAptOutputFolder);

        // check incrementality.
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getUpToDateTasks()).contains(":app:javaPreCompileDebug");
    }

    @Test
    public void testBuild() throws Exception {
        Files.append(
                "\n"
                        + "dependencies {\n"
                        + "    annotationProcessor project(':lib-compiler')\n"
                        + "    testAnnotationProcessor project(':lib-compiler')\n"
                        + "    androidTestAnnotationProcessor project(':lib-compiler')\n"
                        + "    api project(':lib')\n"
                        + "}\n",
                project.getSubproject(":app").getBuildFile(),
                Charsets.UTF_8);

        project.executor().run("assembleDebugAndroidTest", "testDebug");
        File aptOutputFolder = project.getSubproject(":app").file("build/generated/source/apt");
        assertThat(
                        new File(
                                aptOutputFolder,
                                "androidTest/debug/com/example/helloworld/HelloWorldAndroidTestStringValue.java"))
                .exists();
        assertThat(
                        new File(
                                aptOutputFolder,
                                "test/debug/com/example/helloworld/HelloWorldTestStringValue.java"))
                .exists();
    }

    @Test
    public void precompileCheck() throws Exception {
        Files.append(
                "\n"
                        + "dependencies {\n"
                        + "    api project(':lib-compiler')\n"
                        + "    api project(':lib')\n"
                        + "}\n",
                project.getSubproject(":app").getBuildFile(),
                Charsets.UTF_8);

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        String message = result.getFailureMessage();
        assertThat(message).contains("Annotation processors must be explicitly declared now");
        assertThat(message).contains("- lib-compiler.jar (project :lib-compiler)");
    }


    /**
     * Test compile classpath is being added to processor path.
     */
    @Test
    public void compileClasspathIncludedInProcessor() throws Exception {
        File emptyJar = project.getSubproject("app").file("empty.jar");
        assertThat(emptyJar.createNewFile()).isTrue();

        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "    android {\n"
                        + "        defaultConfig {\n"
                        + "            javaCompileOptions {\n"
                        + "                annotationProcessorOptions {\n"
                        + "                    includeCompileClasspath = true\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "dependencies {\n"
                        + "    api project(':lib-compiler')\n"
                        + "    annotationProcessor files('empty.jar')\n"
                        + "}\n");

        project.executor().run("assembleDebug");
    }

    @Test
    public void androidAptPluginFail() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "apply plugin: 'com.neenbedankt.android-apt'\n");

        project.executor().expectFailure().run("assembleDebug");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "dependencies {\n"
                        + "    api project(':lib')\n"
                        + "    annotationProcessor project(':lib-compiler')\n"
                        + "}\n");
        project.executeConnectedCheck();
    }
}
