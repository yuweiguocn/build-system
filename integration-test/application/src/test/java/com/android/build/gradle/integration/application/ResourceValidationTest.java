/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.Rule;
import org.junit.Test;

public class ResourceValidationTest {

    public static final AndroidTestModule TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    static {
        TEST_APP.addFile(
                new TestSourceFile("src/main/res/drawable", "not_a_drawable.ext", "Content"));
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder().fromTestApp(TEST_APP).create();

    @Test
    public void checkResourceValidationCanBeDisabled() throws Exception {
        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");

        //noinspection ThrowableResultOfMethodCallIgnored
        assertThat(result.getFailureMessage()).contains("file name must end with");

        assertThat(result.getStdout()).contains(FileUtils.join("src", "main", "res",
                "drawable", "not_a_drawable.ext"));

        Files.asCharSink(project.file("gradle.properties"), Charsets.UTF_8)
                .write("android.disableResourceValidation=true\n");

        project.execute("assembleDebug");

        assertThat(project.getApk("debug")).containsResource("drawable/not_a_drawable.ext");
    }
}
