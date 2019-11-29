/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.testutils.truth.FileSubject.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.testutils.apk.Aar;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for publishing a custom jar in a library model, used by a consuming app, as well as having a
 * local lint jar.
 */
public class LintCustomLocalAndPublishTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintCustomLocalAndPublishRules").create();

    @Test
    public void checkCustomLint() throws Exception {
        project.executor().expectFailure().run("clean", ":library:lintDebug");
        project.executor().expectFailure().run(":app:lintDebug");
        String appexpected =
                "build.gradle:15: Error: Unknown issue id \"UnitTestLintCheck2\" [LintError]\n"
                        + "        check 'UnitTestLintCheck2'\n"
                        + "               ~~~~~~~~~~~~~~~~~~\n"
                        + "src"
                        + File.separator
                        + "main"
                        + File.separator
                        + "AndroidManifest.xml:11: Error: Should not specify <activity>. [UnitTestLintCheck]\n"
                        + "        <activity android:name=\".MainActivity\">\n"
                        + "        ^\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnitTestLintCheck\":\n"
                        + "   This app should not have any activities.\n"
                        + "\n"
                        + "2 errors, 0 warnings";
        String libexpected =
                "build.gradle:16: Error: Unknown issue id \"UnitTestLintCheck\" [LintError]\n"
                        + "        check 'UnitTestLintCheck'\n"
                        + "               ~~~~~~~~~~~~~~~~~\n"
                        + "src"
                        + File.separator
                        + "main"
                        + File.separator
                        + "java"
                        + File.separator
                        + "com"
                        + File.separator
                        + "example"
                        + File.separator
                        + "app"
                        + File.separator
                        + "MyClass.java:19: Error: Do not implement java.util.List directly [UnitTestLintCheck2]\n"
                        + "public abstract class MyClass implements java.util.List {}\n"
                        + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnitTestLintCheck2\":\n"
                        + "   This app should not have implement java.util.List.\n"
                        + "\n"
                        + "2 errors, 0 warnings";
        File applintfile = new File(project.getSubproject("app").getTestDir(), "lint-results.txt");
        File liblintfile =
                new File(project.getSubproject("library").getTestDir(), "library-lint-results.txt");
        assertThat(applintfile).exists();
        assertThat(liblintfile).exists();
        assertThat(applintfile).contentWithUnixLineSeparatorsIsExactly(appexpected);
        assertThat(liblintfile).contentWithUnixLineSeparatorsIsExactly(libexpected);
    }

    @Test
    public void checkAarHasLintJar() throws Exception {
        project.executor().run("clean");
        project.executor().run(":library:assembleDebug");
        project.executor().run(":library-publish-only:assembleDebug");
        project.executor().run(":library-local-only:assembleDebug");

        Aar localAndPublish = project.getSubproject("library").getAar("debug");
        assertNotNull(localAndPublish.getEntry(FN_LINT_JAR));

        Aar publishOnly = project.getSubproject("library-publish-only").getAar("debug");
        assertNotNull(publishOnly.getEntry(FN_LINT_JAR));

        Aar localOnly = project.getSubproject("library-local-only").getAar("debug");
        assertNull(localOnly.getEntry(FN_LINT_JAR));
    }
}
