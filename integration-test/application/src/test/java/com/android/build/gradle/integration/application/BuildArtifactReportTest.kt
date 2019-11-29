/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.BuildArtifactReportTask
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for [BuildArtifactReportTask].
 */
class BuildArtifactReportTest {
    @get:Rule
    val project =
            GradleTestProject
                    .builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create()

    @Before
    fun setUp() {
        // TODO: Actually do some transform.
        // The DSL for build artifact is not implemented yet.  So we currently only test the base
        // case without any transforms.
        project.buildFile.appendText("""
android {
}""")
    }

    @Test
    fun reportBuildArtifactsDebug() {
        // Run once to ensure it does not fail.
        project.executor().run("reportBuildArtifactsDebug")
        // Run with output file set to verify the result.
        project.executor()
                .with(StringOption.BUILD_ARTIFACT_REPORT_FILE, "report.txt")
                .run("reportBuildArtifactsDebug")
        val report = BuildArtifactsHolder.parseReport(project.file("report.txt"))
        for (data in report.values) {
            assertThat(data).hasSize(1)
        }
    }
}
