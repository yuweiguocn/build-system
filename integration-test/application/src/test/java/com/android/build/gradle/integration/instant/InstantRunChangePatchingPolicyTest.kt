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

package com.android.build.gradle.integration.instant

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.tasks.InstantRunResourcesApkBuilder
import com.android.builder.model.OptionalCompilationStep
import com.android.sdklib.AndroidVersion
import com.android.tools.ir.client.InstantRunArtifact
import com.android.tools.ir.client.InstantRunArtifactType
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class InstantRunChangePatchingPolicyTest(private val firstBuild: BuildTarget,
        private val secondBuild: BuildTarget) {

    companion object {

        @ClassRule @JvmField
        var mProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create()

        @JvmStatic @Parameterized.Parameters(name = "from {0} to {1}")
        fun scenarios(): Collection<Array<BuildTarget>> =
            listOf(arrayOf(BuildTarget.MULTI_APK, BuildTarget.MULTI_APK_SEPARATE_RESOURCES),
                    arrayOf(BuildTarget.MULTI_APK_SEPARATE_RESOURCES, BuildTarget.MULTI_APK))

        private fun buildAndAssertResults(build: BuildTarget) {
            mProject.executor()
                .withInstantRun(
                        AndroidVersion(build.apiLevel, null),
                        OptionalCompilationStep.FULL_APK)
                .run("assembleDebug")

            val model = mProject.model().fetchAndroidProjects().onlyModel
            val instantRunModel = InstantRunTestUtils.getInstantRunModel(model)
            val buildContext = InstantRunTestUtils.loadContext(instantRunModel)
            assertBuild(build, buildContext.artifacts)
        }

        /**
         * Asserts that the build result contains or not the resources APK depending on the passed
         * build target.
         */
        private fun assertBuild(build: BuildTarget, outputs: List<InstantRunArtifact>) {
            if (build == BuildTarget.MULTI_APK) {
                assertThat(findResourcesApk(outputs)).isNull()
            } else {
                assertThat(findResourcesApk(outputs)).isNotNull()
            }
        }

        private fun findResourcesApk(outputs: Iterable<InstantRunArtifact>) : InstantRunArtifact? {
            return outputs.find { it.type == InstantRunArtifactType.SPLIT
                    && it.file.name.startsWith(InstantRunResourcesApkBuilder.APK_FILE_NAME)}
        }
    }

    @Test
    @Throws(Exception::class)
    fun switchScenario() {
        mProject.execute("clean")
        buildAndAssertResults(firstBuild)
        // switch mode
        buildAndAssertResults(secondBuild)
        // and make a round trip.
        buildAndAssertResults(firstBuild)
    }

    enum class BuildTarget constructor(internal val apiLevel: Int) {
        MULTI_APK(23),
        MULTI_APK_SEPARATE_RESOURCES(28)
    }
}