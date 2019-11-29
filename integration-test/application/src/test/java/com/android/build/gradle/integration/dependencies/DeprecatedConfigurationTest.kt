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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.SyncIssue
import org.junit.Rule
import org.junit.Test

class DeprecatedConfigurationTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("projectWithModules")
        .create()

    @Test
    fun testTestCompileGeneratesOneWarning() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile, """
                    dependencies {
                      testCompile project(':library2')
                    }
                    """)
        val model = project.model().ignoreSyncIssues().fetchAndroidProjects()
        assertThat(model.onlyModelMap[":app"]).hasSingleIssue(
                    SyncIssue.SEVERITY_WARNING,
                    SyncIssue.TYPE_DEPRECATED_CONFIGURATION,
                    "testCompile::testImplementation::CONFIG_NAME")
    }
}