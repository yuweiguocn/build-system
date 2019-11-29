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

package com.android.build.gradle.integration.external;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.LoggingLevel;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class UberSkeletonNamespacedSmokeTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromExternalProject("android-studio-gradle-test").create();

    @Before
    public void setUp() throws IOException {
        PerformanceTestProjects.initializeUberSkeleton(project);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "subprojects {\n "
                        + "    pluginManager.withPlugin(\"com.android.application\") {\n"
                        + "        project.extensions.android.aaptOptions.namespaced = true\n"
                        + "    }\n"
                        + "    pluginManager.withPlugin(\"com.android.library\") {\n"
                        + "        project.extensions.android.aaptOptions.namespaced = true\n"
                        + "    }"
                        + "}");
    }

    @Test
    public void checkBuilds() throws Exception {
        AssumeUtil.assumeNotWindows(); // b/67975239
        project.executor()
                .withLoggingLevel(LoggingLevel.QUIET)
                .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
                .run(":phthalic:assembleDebug");
    }
}
