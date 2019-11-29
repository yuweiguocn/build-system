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

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Assemble tests for privateResources.
 *
 * <p>Tip: To execute just this test after modifying the annotations extraction code:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:application:test -D:base:build-system:integration-test:application:test.single=PrivateResourceTest
 * </pre>
 */
public class PrivateResourceTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("privateResources").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        project.execute("clean", "assemble");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkPrivateResources() throws IOException {
        String expected =
                ""
                        + "style Mylib_My_Theme\n"
                        + "string mylib_app_name\n"
                        + "string mylib_public_string\n"
                        + "string mylib_shared_name\n"
                        + "id mylib_shared_name";

        assertThat(project.getSubproject("mylibrary").getAar("release"))
                .containsFileWithContent("public.txt", expected);
        assertThat(project.getSubproject("mylibrary").getAar("debug"))
                .containsFileWithContent("public.txt", expected);

        // No public resources: file should exist but be empty
        assertThat(project.getSubproject("mylibrary2").getAar("debug"))
                .containsFileWithContent("public.txt", "");
        assertThat(project.getSubproject("mylibrary2").getAar("release"))
                .containsFileWithContent("public.txt", "");
    }
}
