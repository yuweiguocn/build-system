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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.ANDROIDTEST_DEBUG;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for minifyLib. */
@RunWith(FilterableParameterized.class)
public class MinifyLibTest {
    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static List<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {CodeShrinker.PROGUARD, false},
                    {CodeShrinker.PROGUARD, true},
                    {CodeShrinker.R8, false},
                    {CodeShrinker.R8, true},
                });
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;
    @Parameterized.Parameter(1)
    public boolean separateRClass;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minifyLib").create();

    @Test
    public void consumerProguardFile() throws Exception {
        getExecutor().run(":app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(DEBUG);
        TruthHelper.assertThatApk(apk).containsClass("Lcom/android/tests/basic/StringProvider;");
        TruthHelper.assertThatApk(apk).containsClass("Lcom/android/tests/basic/UnusedClass;");
    }

    @Test
    public void checkDefaultRulesExtraction() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "\nandroid.buildTypes.debug.minifyEnabled true");
        getExecutor().run(":app:assembleDebug");

        assertThat(project.getIntermediateFile("proguard-files")).doesNotExist();
        assertThat(project.getSubproject("app").getIntermediateFile("proguard-files")).exists();
    }

    @Test
    public void wrongConsumerProguardFile() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                "android {\n"
                        + "defaultConfig.consumerProguardFiles getDefaultProguardFile('proguard-android.txt')\n"
                        + "}\n");

        AndroidProject model =
                project.model()
                        .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                        .ignoreSyncIssues()
                        .fetchAndroidProjects()
                        .getOnlyModelMap()
                        .get(":lib");
        assertThat(model)
                .hasSingleError(SyncIssue.TYPE_GENERIC)
                .that()
                .hasMessageThatContains(
                        "proguard-android.txt should not be used as a consumer configuration file");
    }

    @Test
    public void shrinkingTheLibrary() throws Exception {
        enableLibShrinking();

        GradleBuildResult result = getExecutor().run(":app:assembleDebug");


        if (codeShrinker == CodeShrinker.R8) {
            assertThat(result.getTask(":app:transformClassesAndResourcesWithR8ForDebug")).didWork();
        } else {
            assertThat(result.getTask(":app:transformClassesAndResourcesWithProguardForDebug"))
                    .didWork();
        }

        Apk apk = project.getSubproject(":app").getApk(DEBUG);
        assertThat(apk).containsClass("Lcom/android/tests/basic/StringProvider;");
        assertThat(apk).doesNotContainClass("Lcom/android/tests/basic/UnusedClass;");
    }

    /**
     * Ensure androidTest compile uses consumer proguard files from library.
     *
     * <p>The library contains an unused method that reference a class in guava, and guava is not in
     * the runtime classpath. Library also contains a consumer proguard file which would ignore
     * undefined reference during proguard. The test will fail during proguard if androidTest is not
     * using the proguard file.
     */
    @Test
    public void androidTestWithShrinkedLibrary() throws Exception {
        enableLibShrinking();

        // Test with only androidTestCompile.  Replacing the compile dependency is fine because the
        // app in the test project don't actually reference the library class directly during
        // compile time.
        TestFileUtils.searchAndReplace(
                project.getSubproject(":app").getBuildFile(),
                "api project(':lib')",
                "androidTestImplementation project\\(':lib'\\)");
        GradleBuildResult result = getExecutor().run(":app:assembleAndroidTest");

        if (codeShrinker == CodeShrinker.R8) {
            assertThat(result.getTask(":app:transformClassesAndResourcesWithR8ForDebug")).didWork();
            assertThat(result.getTask(":app:transformClassesAndResourcesWithR8ForDebugAndroidTest"))
                    .didWork();
        } else {
            assertThat(result.getTask(":app:transformClassesAndResourcesWithProguardForDebug"))
                    .didWork();
            assertThat(
                            result.getTask(
                                    ":app:transformClassesAndResourcesWithProguardForDebugAndroidTest"))
                    .didWork();
        }


        Apk apk = project.getSubproject(":app").getApk(ANDROIDTEST_DEBUG);
        assertThat(apk).exists();
    }

    /**
     * Tests the edge case of a library with no classes (after shrinking). We should at least not
     * crash.
     */
    @Test
    public void shrinkingTheLibrary_noClasses() throws Exception {
        enableLibShrinking();
        // Remove the -keep rules.
        File config = project.getSubproject(":lib").file("config.pro");
        FileUtils.deleteIfExists(config);
        TestFileUtils.appendToFile(config, "");
        getExecutor().run(":lib:assembleDebug");
    }

    private void enableLibShrinking() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                ""
                        + "android {\n"
                        + "    buildTypes.debug {\n"
                        + "        minifyEnabled true\n"
                        + "        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'config.pro'\n"
                        + "    }\n"
                        + "}");
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "android {\n"
                        + "    buildTypes.debug {\n"
                        + "        minifyEnabled true\n"
                        + "        proguardFiles getDefaultProguardFile('proguard-android.txt')\n"
                        + "    }\n"
                        + "}\n");
    }

    @NonNull
    private GradleTaskExecutor getExecutor() {
        return project.executor()
                .with(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION, separateRClass)
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8);
    }
}
