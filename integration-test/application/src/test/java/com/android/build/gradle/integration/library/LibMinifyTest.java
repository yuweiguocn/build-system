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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for libMinify. */
public class LibMinifyTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libMinify").create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        project.execute("clean", "build");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkLibraryHasItsFieldsObfuscated() throws IOException {
        // test whether a library project has its fields obfuscated
        TestFileUtils.checkContent(
                project.getOutputFile("mapping", "release", "mapping.txt"),
                "int obfuscatedInt -> a");
    }

    @Test
    public void checkRClassIsNotPackaged() throws IOException, ProcessException {
        assertThatAar(project.getAar("debug")).doesNotContainClass("Lcom/android/tests/basic.R;");
    }
}
