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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp;
import com.android.build.gradle.integration.common.utils.ModelContainerUtils;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class GenerateSourcesOnlyTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create();

    @Test
    public void checkLibraryNotBuilt() throws Exception {
        List<String> generateSources =
                ModelContainerUtils.getDebugGenerateSourcesCommands(
                        project.model().fetchAndroidProjects());

        GradleBuildResult result = project.executor().run(generateSources);

        assertThat(result.getStdout()).doesNotContain("compileDebugJava");
        assertThat(result.getStdout()).doesNotContain("compileReleaseJava");
    }
}
