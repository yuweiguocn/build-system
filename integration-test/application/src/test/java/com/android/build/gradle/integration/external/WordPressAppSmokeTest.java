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
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.LocalRepoDebugger;
import com.android.build.gradle.integration.common.utils.ModelContainerUtils;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import java.io.IOException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

// b/117786329
@Ignore
public class WordPressAppSmokeTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromExternalProject("gradle-perf-android-medium").create();

    @Rule public LocalRepoDebugger localRepoDebugger = new LocalRepoDebugger(project);

    @Before
    public void setUp() throws IOException {
        PerformanceTestProjects.initializeWordpress(project);
    }

    @Test
    public void build() throws Exception {
        ModelContainer<AndroidProject> modelContainer =
                project.model()
                        // Project uses 'compile' configurations
                        // rather than 'api' and 'implementation'
                        // Therefore, ignore the warning given.
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .withLoggingLevel(LoggingLevel.DEBUG)
                        .fetchAndroidProjects();

        project.executor().withLoggingLevel(LoggingLevel.DEBUG).run("clean");

        project.executor()
                .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                .withLoggingLevel(LoggingLevel.DEBUG)
                .run(
                        ModelContainerUtils.getGenerateSourcesCommands(
                                modelContainer,
                                project ->
                                        project.equals(":WordPress") ? "vanillaDebug" : "debug"));

        project.executor().withLoggingLevel(LoggingLevel.DEBUG).run("assembleVanillaDebug");
    }
}
