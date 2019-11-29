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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for maxSdkVersion. */
public class MaxSdkVersionTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("maxSdkVersion").create();

    @Test
    public void maxSdkVersion() throws Exception {
        project.execute("clean", "assembleDebug");
        assertThat(project.getApk("f1", "debug")).hasMaxSdkVersion(21);
        assertThat(project.getApk("f2", "debug")).hasMaxSdkVersion(19);
    }
}
