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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import org.junit.Rule
import org.junit.Test

class DynamicAppMultidexTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .withoutNdk()
        .create()

    @Test
    fun testSyncWarning() {
        project.getSubproject("feature1").buildFile.appendText(
            "android.buildTypes.debug.multiDexEnabled true")
        val model = project.model().ignoreSyncIssues().fetchAndroidProjects()

        TruthHelper.assertThat(model.rootBuildModelMap[":feature1"])
            .hasSingleIssue(
                EvalIssueReporter.Severity.WARNING.severity,
                SyncIssue.TYPE_GENERIC,
                null,
                "Native multidex is always used for dynamic features. Please remove " +
                        "'multiDexEnabled true|false' from your build.gradle file."
            )
    }
}