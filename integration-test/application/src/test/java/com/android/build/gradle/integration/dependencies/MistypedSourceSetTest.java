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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.Iterables;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;

public class MistypedSourceSetTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void exitsWithSyncIssue() throws Exception {
        appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color'\n"
                        + "    productFlavors {\n"
                        + "        red {\n"
                        + "            dimension 'color'\n"
                        + "        }\n"
                        + "        blue {\n"
                        + "            dimension 'color'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    sourceSets {\n"
                        + "        red {\n"
                        + "            java {\n"
                        + "                exclude 'some/unwanted/packageName/**'"
                        + "            }\n"
                        + "        }\n"
                        + "        blooo {\n"
                        + "            java {\n"
                        + "                exclude 'some/unwanted/packageName/**'"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");

        AndroidProject model =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel();

        Collection<SyncIssue> issues = model.getSyncIssues();
        assertThat(issues).hasSize(1);

        SyncIssue issue = Iterables.getOnlyElement(issues);
        assertThat(issue).hasType(SyncIssue.TYPE_GENERIC);
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_ERROR);
        assertThat(issue.getMessage()).contains("blooo");
    }
}
