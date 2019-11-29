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


import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Check Jacoco doesn't get broken with annotation processor that dumps .java files in the compiler
 * out folder.
 */
public class JacocoWithButterKnifeTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("jacocoWithButterKnife")
                    .withoutNdk()
                    .create();

    @Test
    public void build() throws Exception {
        project.execute("jacocoDebug");

        File javaFile =
                FileUtils.join(
                        project.getTestDir(),
                        "build",
                        "generated",
                        "source",
                        "apt",
                        "debug",
                        "com",
                        "test",
                        "jacoco",
                        "annotation",
                        "BindActivity$$ViewBinder.java");
        assertThat(javaFile).exists();
    }
}
