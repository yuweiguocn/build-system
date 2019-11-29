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

import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration test for the lintFix target on the synthetic accessor warnings found in the Kotlin
 * project.
 */
public class LintFixTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintKotlin").create();

    @Before
    public void setUp() throws Exception {
        @SuppressWarnings("ThrowableNotThrown")
        Throwable exception = project.executeExpectingFailure("clean", ":app:lintFix");
        while (exception.getCause() != null && exception.getCause() != exception) {
            exception = exception.getCause();
        }
        assertThat(exception.getMessage())
                .contains(
                        "Aborting build since sources were modified to apply quickfixes after compilation");
    }

    @Test
    public void checkFindNestedResult() {
        // Make sure quickfixes worked too
        File source = project.file("app/src/main/kotlin/test/pkg/AccessTest2.kt");
        // The original source has this:
        //    private val field5 = arrayOfNulls<Inner>(100)
        //    ...
        //    private constructor()
        //    ...
        // After applying quickfixes, it contains this:
        //    internal val field5 = arrayOfNulls<Inner>(100)
        //    ...
        //    internal constructor()
        //    ...
        assertThat(source).contains("internal val field5");
        assertThat(source).contains("internal constructor()");
    }
}
