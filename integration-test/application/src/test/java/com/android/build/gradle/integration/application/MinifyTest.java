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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TaskStateList;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Version;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for minify. */
@RunWith(FilterableParameterized.class)
public class MinifyTest {

    @Parameterized.Parameters(name = "shrinker = {0}")
    public static CodeShrinker[] getConfigurations() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD, CodeShrinker.R8};
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minify").create();

    @Test
    public void appApkIsMinified() throws Exception {
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                        .run("assembleMinified");
        assertThat(result.getStdout()).doesNotContain("Note");
        assertThat(result.getStdout()).doesNotContain("duplicate");

        Apk apk = project.getApk("minified");
        Set<String> allClasses = Sets.newHashSet();
        for (Dex dex : apk.getAllDexes()) {
            allClasses.addAll(
                    dex.getClasses()
                            .keySet()
                            .stream()
                            .filter(
                                    c ->
                                            !c.startsWith("Lorg/jacoco")
                                                    && !c.equals("Lcom/vladium/emma/rt/RT;"))
                            .collect(Collectors.toSet()));
        }
        assertThat(allClasses)
                .containsExactly(
                        "Lcom/android/tests/basic/a;",
                        "Lcom/android/tests/basic/Main;",
                        "Lcom/android/tests/basic/IndirectlyReferencedClass;");

        File defaultProguardFile =
                project.file(
                        "build/"
                                + AndroidProject.FD_INTERMEDIATES
                                + "/proguard-files"
                                + "/proguard-android.txt"
                                + "-"
                                + Version.ANDROID_GRADLE_PLUGIN_VERSION);
        assertThat(defaultProguardFile).exists();

        assertThat(apk)
                .hasMainClass("Lcom/android/tests/basic/Main;")
                .that()
                // Make sure default ProGuard rules were applied.
                .hasMethod("handleOnClick");
    }

    @Test
    public void appTestDefaultKeepAnnotations() throws Exception {
        String classContent =
                "package example;\n"
                        + "public class ToBeKept {\n"
                        + "  @android.support.annotation.Keep String field1;\n"
                        + "  @androidx.annotation.Keep String field2;\n"
                        + "  String field3;\n"
                        + "  @androidx.annotation.Keep void foo() { }\n"
                        + "  @android.support.annotation.Keep void baz() { }\n"
                        + "  void fab() { }\n"
                        + "}";
        Path toBeKept = project.getMainSrcDir().toPath().resolve("example/ToBeKept.java");
        Files.createDirectories(toBeKept.getParent());
        Files.write(toBeKept, classContent.getBytes());

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "dependencies {\n"
                        + "    implementation 'com.android.support:support-annotations:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    implementation 'androidx.annotation:annotation:1.0.0'\n"
                        + "}");

        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assembleMinified");

        Apk minified = project.getApk(GradleTestProject.ApkType.of("minified", true));
        assertThat(minified).hasClass("Lexample/ToBeKept;").that().hasField("field1");
        assertThat(minified).hasClass("Lexample/ToBeKept;").that().hasField("field2");
        assertThat(minified).hasClass("Lexample/ToBeKept;").that().doesNotHaveField("field3");

        assertThat(minified).hasClass("Lexample/ToBeKept;").that().hasMethods("foo", "baz");

        assertThat(minified).hasClass("Lexample/ToBeKept;").that().doesNotHaveMethod("fab");
    }

    @Test
    public void testApkIsNotMinified_butMappingsAreApplied() throws Exception {
        // Run just a single task, to make sure task dependencies are correct.
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assembleMinifiedAndroidTest");

        GradleTestProject.ApkType testMinified =
                GradleTestProject.ApkType.of("minified", "androidTest", true);

        Apk apk = project.getApk(testMinified);
        Set<String> allClasses = Sets.newHashSet();
        for (Dex dex : apk.getAllDexes()) {
            allClasses.addAll(
                    dex.getClasses()
                            .keySet()
                            .stream()
                            .filter(c -> !c.startsWith("Lorg/"))
                            .filter(c -> !c.startsWith("Ljunit/"))
                            .filter(c -> !c.startsWith("Landroid/support/"))
                            .collect(Collectors.toSet()));
        }

        assertThat(allClasses)
                .containsExactly(
                        "Lcom/android/tests/basic/MainTest;",
                        "Lcom/android/tests/basic/UnusedTestClass;",
                        "Lcom/android/tests/basic/UsedTestClass;",
                        "Lcom/android/tests/basic/test/BuildConfig;",
                        "Lcom/android/tests/basic/test/R;");

        assertThat(apk)
                .hasClass("Lcom/android/tests/basic/MainTest;")
                .that()
                .hasFieldWithType("stringProvider", "Lcom/android/tests/basic/a;");
    }

    @Test
    public void testProguardOptimizedBuildsSuccessfully() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "getDefaultProguardFile('proguard-android.txt')",
                "getDefaultProguardFile('proguard-android-optimize.txt')");
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assembleMinified");
    }

    @Test
    public void testJavaResourcesArePackaged() throws IOException, InterruptedException {
        Path javaRes = project.getTestDir().toPath().resolve("src/main/resources/my_res.txt");
        Files.createDirectories(javaRes.getParent());
        Files.createFile(javaRes);
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assembleMinified");
        assertThat(project.getApk(GradleTestProject.ApkType.of("minified", true)))
                .contains("my_res.txt");
    }

    @Test
    public void testAndroidTestIsNotUpToDate() throws IOException, InterruptedException {
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assembleMinified", "assembleMinifiedAndroidTest");

        TestFileUtils.appendToFile(project.file("proguard-rules.pro"), "\n-keep class **");
        GradleBuildResult minifiedAndroidTest =
                project.executor()
                        .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                        .run("assembleMinifiedAndroidTest");

        TaskStateList.TaskInfo taskInfo =
                minifiedAndroidTest.findTask(
                        codeShrinker == CodeShrinker.R8
                                ? ":transformClassesAndResourcesWithR8ForMinifiedAndroidTest"
                                : ":transformClassesAndResourcesWithProguardForMinifiedAndroidTest");

        assertThat(taskInfo).didWork();
    }
}
