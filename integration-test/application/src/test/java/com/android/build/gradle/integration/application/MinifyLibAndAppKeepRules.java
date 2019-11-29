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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test that keep rules are applied properly when the main app references classes from the library
 * project.
 */
@RunWith(FilterableParameterized.class)
public class MinifyLibAndAppKeepRules {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] data() {
        // enable for R8 once http://b/36847655 is fixed
        return new CodeShrinker[] {CodeShrinker.PROGUARD};
    }

    @Parameterized.Parameter() public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minifyLibWithJavaRes")
            .create();

    @Test
    public void testReleaseClassesPackaging() throws Exception {
        File noPackage =
                FileUtils.join(project.getSubproject("lib").getMainSrcDir(), "NoPackage.java");
        Files.asCharSink(noPackage, Charsets.UTF_8).write("public class NoPackage{}");

        File referencesNoPackage =
                FileUtils.join(
                        project.getSubproject("app").getMainSrcDir(), "ReferencesNoPackage.java");
        Files.asCharSink(referencesNoPackage, Charsets.UTF_8)
                .write(
                        "public class ReferencesNoPackage { static { NoPackage np = new NoPackage(); } }");

        // add the proguard rule that should keep all the classes
        Files.asCharSink(
                        FileUtils.join(
                                project.getSubproject("app").getTestDir(), "proguard-rules.pro"),
                        Charsets.UTF_8)
                .write("-keep class *");

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "android {\n" +
                        "    buildTypes {\n" +
                        "        release {\n" +
                        "           proguardFiles getDefaultProguardFile('proguard-android.txt')," +
                        "'proguard-rules.pro'\n" +
                        "        }\n" +
                        "    }\n" +
                        "}");

        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run(":app:assembleRelease");
        assertThat(project.getSubproject("app").getApk("release"))
                .containsClass("LNoPackage;");
    }

    /** Regression test for b/119758914. */
    @Test
    public void testKeepRulesGeneratedCorrectly() throws Exception {
        File confOutput = new File(project.getTestDir(), "conf.out");
        // add the proguard rule to print configuration
        TestFileUtils.appendToFile(
                FileUtils.join(project.getSubproject("app").getTestDir(), "proguard-rules.pro"),
                "-printconfiguration \"" + confOutput + "\"");

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "android {\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "           proguardFiles getDefaultProguardFile('proguard-android.txt'),"
                        + "'proguard-rules.pro'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");

        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run(":app:assembleRelease");
        assertThat(confOutput).exists();

        // file path for libraryjar can be surrounded by single quotes on Windows.
        List<String> libraryJars =
                Files.readLines(confOutput, Charsets.UTF_8)
                        .stream()
                        .filter(i -> i.startsWith("-libraryjar"))
                        .map(i -> {
                            String filePathPossiblyWithQuotes = i.substring(i.indexOf(' ') + 1);
                            String filePathWithoutQuotes = filePathPossiblyWithQuotes.replace("\'","");
                            return new File(filePathWithoutQuotes).getName();
                        })
                        .collect(Collectors.toList());

        assertThat(libraryJars)
                .named("keep rules libraryjars")
                .containsExactly(
                        "android.jar",
                        "org.apache.http.legacy.jar",
                        "android.test.mock.jar",
                        "android.test.base.jar",
                        "android.test.runner.jar");
    }
}
