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

package com.android.build.gradle.integration.nativebuild;

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for extracting RS enabled annotations. */
public class RsEnabledAnnotationTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("extractRsEnabledAnnotations")
                    .setCmakeVersion("3.10.4819442")
                    .setWithCmakeDirInLocalProp(true)
                    .create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkExtractAnnotation() throws IOException {
        // check the resulting .aar file to ensure annotations.zip inclusion.
        assertThat(project.getAar("debug")).contains("annotations.zip");
        assertThat(project.getAar("debug")).doesNotContain("libs/renderscript-v8.zip");
    }
}
