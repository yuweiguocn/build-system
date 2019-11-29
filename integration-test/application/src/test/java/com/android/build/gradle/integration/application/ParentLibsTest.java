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

package com.android.build.gradle.integration.application;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Assemble tests for parentLibTest
 */
public class ParentLibsTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("parentLibsTest").create();

    @Before
    public void moveLocalProperties() throws IOException {
        Files.move(
                project.file(SdkConstants.FN_LOCAL_PROPERTIES),
                project.getSubproject("app").file(SdkConstants.FN_LOCAL_PROPERTIES));
    }

    @Test
    public void assembleAndLint() throws IOException, InterruptedException {
        project.execute(ImmutableList.of("-p", "app"), "clean", "assembleDebug", "lint");
    }
}
