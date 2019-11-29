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

package com.android.build.gradle.integration.lint;

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Assemble tests for lintLibraryModel.
 *
 * <p>To run just this test: ./gradlew :base:build-system:integration-test:application:test
 * -D:base:build-system:integration-test:application:test.single=LintLibraryModelTest
 */
public class LintLibraryModelTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintLibraryModel").create();

    @Test
    public void checkLintLibraryModel() throws Exception {
        project.execute("clean", ":app:lintDebug");
        String expected =
                FileUtils.join("src", "main", "java", "com", "android", "test", "lint", "lintmodel", "mylibrary", "MyLibrary.java") + ":5: Warning: Assertions are unreliable in Dalvik and unimplemented in ART. Use BuildConfig.DEBUG conditional checks instead. [Assert]\n"
                        + "       assert arg > 5;\n"
                        + "       ~~~~~~\n"
                        + FileUtils.join("src", "main", "java", "com", "android", "test", "lint", "javalib", "JavaLib.java") + ":4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    public static final String SD_CARD = \"/sdcard/something\";\n"
                        + "                                         ~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings";
        File file = new File(project.getSubproject("app").getTestDir(), "lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly(expected);
    }
}
