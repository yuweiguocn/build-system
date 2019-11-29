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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelContainer
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import com.android.builder.model.SyncIssue.SEVERITY_ERROR
import com.android.builder.model.SyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Integration test for minimum plugin version checks. */
class PluginVersionCheckTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
        .create()

    @Test
    fun testButterKnifeTooOld() {
        // Use an old version of the ButterKnife plugin, expect sync issues
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "android {\n",
            """
                buildscript {
                    dependencies {
                        classpath 'com.jakewharton:butterknife-gradle-plugin:9.0.0-rc1'
                    }
                }
                apply plugin: 'com.jakewharton.butterknife'
                android {
                """.trimIndent()
        )

        val model = project.model().ignoreSyncIssues().fetchAndroidProjects()
        val syncIssues = model.getNonDeprecationIssues()
        assertThat(syncIssues).hasSize(1)
        val syncIssue = syncIssues.single()

        assertThat(syncIssue.type).isEqualTo(TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD)
        assertThat(syncIssue.severity).isEqualTo(SEVERITY_ERROR)
        val expected = "The Android Gradle plugin supports only Butterknife Gradle " +
                "plugin version 9.0.0-rc2 and higher.\n" +
                "The following dependencies do not satisfy the required version:\n" +
                "root project 'project' -> " +
                "com.jakewharton:butterknife-gradle-plugin:9.0.0-rc1"
        assertThat(syncIssue.message).isEqualTo(expected)

        val failure = project.executor().expectFailure().run("generateDebugR2")
        assertThat(failure.stderr).contains(expected)
    }

    @Test
    fun testButterKnifeOk() {
        // Use a sufficiently new version of the ButterKnife plugin, expect no sync issues
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "android {\n",
            """
                buildscript {
                    dependencies {
                        classpath 'com.jakewharton:butterknife-gradle-plugin:9.0.0-rc2'
                    }
                }
                apply plugin: 'com.jakewharton.butterknife'
                android {
                """.trimIndent()
        )

        val model = project.model().fetchAndroidProjects()
        assertThat(model.getNonDeprecationIssues()).isEmpty()

        project.executor().run("generateDebugR2")
    }

    private fun ModelContainer<AndroidProject>.getNonDeprecationIssues() =
        onlyModel.syncIssues.filter { it.type != SyncIssue.TYPE_DEPRECATED_DSL }
}
