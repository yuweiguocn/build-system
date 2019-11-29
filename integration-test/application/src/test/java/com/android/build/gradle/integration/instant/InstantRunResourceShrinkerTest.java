/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.google.common.io.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Checks that building with resource shrinking enabled has no effect with Instant Run. */
public class InstantRunResourceShrinkerTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void enableResourceShrinking() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.buildTypes.debug.shrinkResources = true\n"
                        + "\nandroid.buildTypes.debug.minifyEnabled = true\n");

        File unusedResource = project.file("src/main/res/drawable/not_used.xml");
        Files.createParentDirs(unusedResource);

        Files.asCharSink(unusedResource, StandardCharsets.UTF_8)
                .write(
                        "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "        android:width=\"24dp\"\n"
                                + "        android:height=\"24dp\"\n"
                                + "        android:viewportHeight=\"108.0\"\n"
                                + "        android:viewportWidth=\"108.0\">\n"
                                + "    <path\n"
                                + "            android:fillColor=\"#26A69A\"\n"
                                + "            android:pathData=\"M0,0h108v108h-108z\"\n"
                                + "            android:strokeColor=\"#66FFFFFF\"\n"
                                + "            android:strokeWidth=\"0.8\" />\n"
                                + "</vector>\n");
    }

    @Test
    public void checkPackaging() throws Exception {
        GradleBuildResult result =
                project.executor()
                        // resource shrinking and R8, http://b/72370175
                        .with(BooleanOption.ENABLE_R8, false)
                        .withInstantRun(
                                new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                        .run("assembleDebug");
        assertThat(result.getDidWorkTasks())
                .doesNotContain("transformClassesAndDexWithShrinkResForDebug");
    }
}
