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

package com.android.build.gradle.integration.databinding;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.TestUtils;
import com.android.testutils.truth.DexClassSubject;
import com.android.testutils.truth.DexSubject;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Integration test to ensure correctness of incremental builds when data binding is used. */
@RunWith(FilterableParameterized.class)
public class DataBindingIncrementalTest {

    @Rule
    public GradleTestProject project;

    private static final String EXPORT_INFO_TASK = ":dataBindingExportBuildInfoDebug";
    private static final String COMPILE_JAVA_TASK = ":compileDebugJavaWithJavac";

    private static final String MAIN_ACTIVITY_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/ActivityMainBinding;";
    private static final String MAIN_ACTIVITY_BINDING_CLASS_IMPL =
            "Landroid/databinding/testapp/databinding/ActivityMainBindingImpl;";
    private static final String MAIN_ACTIVITY_BINDING_CLASS_LAND_IMPL =
            "Landroid/databinding/testapp/databinding/ActivityMainBindingLandImpl;";

    private static final String MAIN_ACTIVITY_2_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/Activity2Binding;";
    private static final String MAIN_ACTIVITY_2_BINDING_CLASS_IMPL =
            "Landroid/databinding/testapp/databinding/Activity2BindingImpl;";


    private static final String ACTIVITY_MAIN_XML = "src/main/res/layout/activity_main.xml";
    private static final String ACTIVITY_MAIN_JAVA
            = "src/main/java/android/databinding/testapp/MainActivity.java";
    private static final String USER_JAVA = "src/main/java/android/databinding/testapp/User.java";

    private final boolean enableV2;
    private final boolean withKotlin;

    private final List<String> mainActivityBindingClasses;

    @Parameterized.Parameters(name = "useV2_{0}_useAndroidX_{1}_withKotlin_{2}")
    public static Iterable<Boolean[]> classNames() {
        return ImmutableList.of(
                new Boolean[] {true, false, false},
                new Boolean[] {false, false, false},
                new Boolean[] {true, true, false},
                new Boolean[] {false, true, false},
                // Test one scenario with Kotlin is probably enough (instead of four)
                new Boolean[] {true, true, true});
    }

    public DataBindingIncrementalTest(boolean enableV2, boolean useAndroidX, boolean withKotlin) {
        this.enableV2 = enableV2;
        this.withKotlin = withKotlin;
        if (enableV2) {
            mainActivityBindingClasses =
                    ImmutableList.of(MAIN_ACTIVITY_BINDING_CLASS, MAIN_ACTIVITY_BINDING_CLASS_IMPL);
        } else {
            mainActivityBindingClasses = ImmutableList.of(MAIN_ACTIVITY_BINDING_CLASS);
        }
        String v2Prop = BooleanOption.ENABLE_DATA_BINDING_V2.getPropertyName() + "=" + enableV2;
        String androidXProp = BooleanOption.USE_ANDROID_X.getPropertyName() + "=" + useAndroidX;
        project =
                GradleTestProject.builder()
                        .fromTestProject("databindingIncremental")
                        .addGradleProperties(v2Prop)
                        .addGradleProperties(androidXProp)
                        .withKotlinGradlePlugin(withKotlin)
                        .create();
    }

    @Before
    public void setUp() throws IOException {
        if (withKotlin) {
            TestFileUtils.searchAndReplace(
                    project.getBuildFile(),
                    "apply plugin: 'com.android.application'",
                    "apply plugin: 'com.android.application'\n"
                            + "apply plugin: 'kotlin-android'\n"
                            + "apply plugin: 'kotlin-kapt'");
        }
    }

    private File getGeneratedInfoClass() {
        return project.getGeneratedSourceFile(
                "source",
                "dataBinding",
                "trigger",
                "debug",
                "android",
                "databinding",
                "testapp",
                "DataBindingInfo.java");
    }

    private File getGeneratedSourceFile() {
        return project.getGeneratedSourceFile(
                "source",
                withKotlin ? "kapt" : "apt",
                "debug",
                "android",
                "databinding",
                "testapp",
                "databinding",
                enableV2 ? "ActivityMainBindingImpl.java" : "ActivityMainBinding.java");
    }

    private File getInfoIntermediate(String fileName) {
        return project.getIntermediateFile(
                "data_binding_layout_info_type_merge",
                "debug",
                "mergeDebugResources",
                "out",
                fileName);
    }

    @Test
    public void compileWithoutChange() throws Exception {
        project.executor().run(EXPORT_INFO_TASK);
        File infoClass = getGeneratedInfoClass();
        assertThat(infoClass).exists();
        String contents = FileUtils.readFileToString(infoClass, Charsets.UTF_8);
        project.executor().run(EXPORT_INFO_TASK);
        assertThat(getGeneratedInfoClass()).hasContents(contents);
    }

    @Test
    public void changeIrrelevantJavaCode() throws Exception {
        // Compile fully the first time
        project.execute(COMPILE_JAVA_TASK);

        File generatedInfoFile = getGeneratedInfoClass();
        File generatedSourceFile = getGeneratedSourceFile();
        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();

        String infoFileContents = FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String sourceFileContents = FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        long sourceFileTimestamp = generatedSourceFile.lastModified();

        // Make an irrelevant change, ideally data binding should not be invoked. However, since
        // data binding does not yet fully support incrementality, the sources are currently
        // re-generated for now.
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_JAVA), "return true;", "return false;");
        GradleBuildResult result = project.executor().run(COMPILE_JAVA_TASK);

        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();
        String updatedInfoFileContents =
                FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String updatedSourceFileContents =
                FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        assertThat(updatedInfoFileContents).isEqualTo(infoFileContents);
        assertThat(updatedSourceFileContents).isEqualTo(sourceFileContents);

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasUpToDate();
        assertThat(result.getTask(COMPILE_JAVA_TASK)).didWork();

        TestUtils.waitForFileSystemTick();
        assertThat(generatedSourceFile).isNewerThan(sourceFileTimestamp);
    }

    @Test
    public void changeRelevantJavaCode() throws Exception {
        // Compile fully the first time
        project.execute(COMPILE_JAVA_TASK);

        File generatedInfoFile = getGeneratedInfoClass();
        File generatedSourceFile = getGeneratedSourceFile();
        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();

        String infoFileContents = FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String sourceFileContents = FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        long sourceFileTimestamp = generatedSourceFile.lastModified();

        // Make a relevant change, data binding should be invoked and the sources should be
        // re-generated.
        TestFileUtils.searchAndReplace(
                project.file(USER_JAVA), "return this.name;", "return name;");
        GradleBuildResult result = project.executor().run(COMPILE_JAVA_TASK);

        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();
        String updatedInfoFileContents =
                FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String updatedSourceFileContents =
                FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        assertThat(updatedInfoFileContents).isEqualTo(infoFileContents);
        assertThat(updatedSourceFileContents).isEqualTo(sourceFileContents);

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasUpToDate();
        assertThat(result.getTask(COMPILE_JAVA_TASK)).didWork();

        TestUtils.waitForFileSystemTick();
        assertThat(generatedSourceFile).isNewerThan(sourceFileTimestamp);
    }

    @Test
    public void breakRelevantJavaCodeExpectFailure() throws Exception {
        // Compile fully the first time
        project.execute(COMPILE_JAVA_TASK);

        File generatedInfoFile = getGeneratedInfoClass();
        File generatedSourceFile = getGeneratedSourceFile();
        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();

        // Make a relevant change that breaks data binding, data binding should be invoked and
        // compilation should fail.
        TestFileUtils.searchAndReplace(
                project.file(USER_JAVA),
                "public String getName() {",
                "public String getFirstName() {");
        GradleBuildResult result = project.executor().expectFailure().run(COMPILE_JAVA_TASK);
        String stacktrace = Throwables.getStackTraceAsString(checkNotNull(result.getException()));

        if (withKotlin) {
            assertThat(stacktrace.contains("Execution failed for task ':kaptDebugKotlin'"));
            // The root cause could be printed out on stdout or stderr, possibly based on build bot
            // configurations.
            assertThat(result.getStdout() + result.getStderr())
                    .contains("Could not find accessor android.databinding.testapp.User.name");
        } else {
            assertThat(result.getTask(COMPILE_JAVA_TASK)).failed();
            assertThat(stacktrace)
                    .contains("Could not find accessor android.databinding.testapp.User.name");
        }
    }

    @Test
    public void changeVariableName() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<variable name=\"foo\" type=\"String\"/>",
                "<variable name=\"foo2\" type=\"String\"/>");
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<TextView android:text='@{foo + \" \" + foo}'",
                "<TextView android:text='@{foo2 + \" \" + foo2}'");
        project.executor().run("assembleDebug");

        for (String className : mainActivityBindingClasses) {
            DexClassSubject bindingClass =
                    assertThat(project.getApk(DEBUG))
                            .hasMainDexFile()
                            .that()
                            .containsClass(className)
                            .that();
            bindingClass.doesNotHaveMethod("setFoo");
            bindingClass.hasMethod("setFoo2");
        }
    }

    @Test
    public void addVariable() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<variable name=\"foo\" type=\"String\"/>",
                "<variable name=\"foo\" type=\"String\"/><variable name=\"foo2\" type=\"String\"/>");
        project.executor().run("assembleDebug");

        for (String className : mainActivityBindingClasses) {
            assertThat(project.getApk(DEBUG))
                    .hasMainDexFile()
                    .that()
                    .containsClass(className)
                    .that()
                    .hasMethods("setFoo", "setFoo2");
        }
    }

    @Test
    public void addIdToView() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<TextView android:text='@{foo + \" \" + foo}'",
                "<TextView android:text='@{foo + \" \" + foo}'\n"
                        + "android:id=\"@+id/myTextView\"");
        project.executor().run("assembleDebug");

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that()
                .hasField("myTextView");

        if (enableV2) {
            assertThat(project.getApk(DEBUG))
                    .hasMainDexFile()
                    .that()
                    .containsClass(MAIN_ACTIVITY_BINDING_CLASS_IMPL)
                    .that()
                    .doesNotHaveField("myTextView");
        }

        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML), "android:id=\"@+id/myTextView\"", "");
        project.executor().run("assembleDebug");

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that()
                .doesNotHaveField("myTextView");
    }

    @Test
    public void addNewLayoutFolderAndFile() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File landscapeActivity = new File(mainActivity
                .getParentFile().getParentFile(), "layout-land/activity_main.xml");
        assertThat(landscapeActivity.getParentFile().mkdirs()).isTrue();
        Files.copy(mainActivity, landscapeActivity);
        project.executor().run("assembleDebug");

        DexSubject apk = assertThat(project.getApk(DEBUG)).hasMainDexFile().that();
        apk.containsClass(MAIN_ACTIVITY_BINDING_CLASS);
        apk.containsClass(MAIN_ACTIVITY_BINDING_CLASS_LAND_IMPL);
        apk.containsClass(MAIN_ACTIVITY_BINDING_CLASS_IMPL);

        // delete and recompile
        assertThat(landscapeActivity.delete()).isTrue();
        project.executor().run("assembleDebug");
        assertThat(project.getApk(DEBUG))
                .doesNotContainClass(MAIN_ACTIVITY_BINDING_CLASS_LAND_IMPL);
        if (!enableV2) {
            assertThat(project.getApk(DEBUG))
                    .doesNotContainClass(MAIN_ACTIVITY_2_BINDING_CLASS_IMPL);
        }
        for (String className : mainActivityBindingClasses) {
            assertThat(project.getApk(DEBUG)).containsClass(className);
        }
    }

    @Test
    public void addNewLayout() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        assertThat(getInfoIntermediate("activity2-layout.xml")).doesNotExist();

        project.executor().run("assembleDebug");

        assertThat(getInfoIntermediate("activity2-layout.xml")).exists();

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_2_BINDING_CLASS)
                .that()
                .hasMethod("setFoo");
        if (enableV2) {
            assertThat(project.getApk(DEBUG))
                    .hasMainDexFile()
                    .that()
                    .containsClass(MAIN_ACTIVITY_2_BINDING_CLASS_IMPL)
                    .that()
                    .hasMethod("setFoo");
        }
    }

    @Test
    public void removeLayout() throws Exception {
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        project.execute("assembleDebug");
        assertThat(project.getApk(DEBUG)).containsClass(MAIN_ACTIVITY_2_BINDING_CLASS);
        if (enableV2) {
            assertThat(project.getApk(DEBUG)).containsClass(MAIN_ACTIVITY_2_BINDING_CLASS_IMPL);
        }

        assertThat(getInfoIntermediate("activity2-layout.xml")).exists();
        assertThat(activity2.delete()).isTrue();
        project.execute("assembleDebug");
        assertThat(project.getApk(DEBUG)).doesNotContainClass(MAIN_ACTIVITY_2_BINDING_CLASS);
        assertThat(project.getApk(DEBUG)).doesNotContainClass(MAIN_ACTIVITY_2_BINDING_CLASS_IMPL);
        assertThat(getInfoIntermediate("activity2-layout.xml")).doesNotExist();
    }

    @Test
    public void renameLayout() throws Exception {
        String activity3ClassName = "Landroid/databinding/testapp/databinding/Activity3Binding;";
        String activity3ClassNameImpl =
                "Landroid/databinding/testapp/databinding/Activity3BindingImpl;";
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity3 = new File(mainActivity.getParentFile(), "activity3.xml");
        Files.copy(mainActivity, activity3);
        project.executor().run("assembleDebug");

        File activity3DataBindingInfo = getInfoIntermediate("activity3-layout.xml");
        assertThat(activity3DataBindingInfo).exists();
        long dataBindingInfoLastModified = activity3DataBindingInfo.lastModified();
        TestUtils.waitForFileSystemTick();

        assertThat(project.getApk(DEBUG)).containsClass(activity3ClassName);
        if (enableV2) {
            assertThat(project.getApk(DEBUG)).containsClass(activity3ClassNameImpl);
        }

        // Modify the file.
        long activity3LayoutLastModified = activity3.lastModified();
        TestUtils.waitForFileSystemTick();
        TestFileUtils.searchAndReplace(activity3, "<data>", "<data class=\"MyCustomName\">");

        // Make sure that the file was actually modified.
        assertThat(activity3.lastModified()).isNotEqualTo(activity3LayoutLastModified);

        project.executor().run("assembleDebug");

        assertThat(activity3DataBindingInfo).exists();
        assertThat(activity3DataBindingInfo.lastModified())
                .isNotEqualTo(dataBindingInfoLastModified);
        
        assertThat(project.getApk(DEBUG)).doesNotContainClass(activity3ClassName);
        assertThat(project.getApk(DEBUG)).doesNotContainClass(activity3ClassNameImpl);

        String customName = "Landroid/databinding/testapp/databinding/MyCustomName;";
        String customNameImpl = "Landroid/databinding/testapp/databinding/MyCustomNameImpl;";
        assertThat(project.getApk(DEBUG)).containsClass(customName);
        if (enableV2) {
            assertThat(project.getApk(DEBUG)).containsClass(customNameImpl);
        }
    }
}
