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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for api. */
public class ApiTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("api").create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("app/build.gradle")))
                .isEqualTo("615a3da978dfad628ba409bc050d110e9afcc45a");
        assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("lib/build.gradle")))
                .isEqualTo("1bbaf50d84cbd308538765aef021fba7e1a62d33");
    }
}
