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

class DynamicAppSigningConfigTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
            .fromTestProject("dynamicApp")
        .withoutNdk()
        .create()

    @Test
    fun testSyncWarning() {
       project.getSubproject("feature1").buildFile.appendText(
                """
                    android {
                        signingConfigs {
                            myConfig {
                                storeFile file("foo.keystore")
                                storePassword "bar"
                                keyAlias "foo"
                                keyPassword "bar"
                            }
                        }
                        buildTypes {
                            debug.signingConfig signingConfigs.myConfig
                        }
                    }
                """.trimIndent());
        val model = project.model().ignoreSyncIssues().fetchAndroidProjects()

        TruthHelper.assertThat(model.rootBuildModelMap[":feature1"])
                .hasSingleIssue(
                        EvalIssueReporter.Severity.WARNING.severity,
                        SyncIssue.TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE,
                        null,
                    "Signing configuration should not be declared in the "
                            + "dynamic-feature. Dynamic-features use the signing configuration "
                            + "declared in the application module."
                )
    }

    @Test
    fun testNoSyncWarning() {
        val model = project.model().ignoreSyncIssues().fetchAndroidProjects()
        TruthHelper.assertThat(model.rootBuildModelMap[":feature2"]).hasIssueSize(0)
    }
}