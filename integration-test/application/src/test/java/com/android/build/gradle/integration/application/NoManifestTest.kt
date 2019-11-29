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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.SyncIssue
import com.google.common.collect.Iterables
import org.junit.Rule
import org.junit.Test
import java.util.stream.Collectors
import kotlin.test.assertEquals

class NoManifestTest {

    val app = MinimalSubProject.app("com.example.app")
    init {
        app.removeFileByName("AndroidManifest.xml")
    }
    private val testApp = MultiModuleTestProject.builder().subproject(":app", app).build()
    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun noManifestConfigurationPassesTest() {
        project.executor().with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true).run("tasks")
        // we should be able to create task list, without a valid manifest.
    }

    @Test
    fun noManifestSyncNoApplicationIdTest() {
        val issues = project.model().with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true).ignoreSyncIssues().fetchAndroidProjects().onlyModel.syncIssues
        assertEquals(2, issues.size)
        val errors = issues.stream()
            .filter { syncIssue -> syncIssue.severity === SyncIssue.SEVERITY_ERROR }
            .collect(Collectors.toList<SyncIssue>())

        assertThat<SyncIssue, Iterable<SyncIssue>>(errors).hasSize(1)
        val error = Iterables.getOnlyElement<SyncIssue>(errors)
        assertThat(error.type).isEqualTo(SyncIssue.TYPE_GENERIC)

        val warnings = issues.stream()
            .filter { syncIssue -> syncIssue.severity === SyncIssue.SEVERITY_WARNING }
            .collect(Collectors.toList<SyncIssue>())
        assertThat<SyncIssue, Iterable<SyncIssue>>(warnings).hasSize(1)
        val warning = Iterables.getOnlyElement<SyncIssue>(warnings)
        assertThat(warning).hasType(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
    }

    @Test
    fun noManifestSyncNoIssuesTest() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            "\nandroid.defaultConfig.applicationId \"com.example.app\"\n"
        )
        val issues = project.model().with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true).ignoreSyncIssues().fetchAndroidProjects().onlyModel.syncIssues

        assertEquals(1, issues.size)
        val issue = Iterables.getOnlyElement(issues)
        assertThat(issue).hasType(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_WARNING)
    }

}
