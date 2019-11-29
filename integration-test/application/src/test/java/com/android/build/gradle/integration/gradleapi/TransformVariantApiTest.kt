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

package com.android.build.gradle.integration.gradleapi

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Test for a transform that uses the variant specific registration callback.  */
class TransformVariantApiTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("transformVariantApiTest").create()

    @Test
    fun checkNotRunForDebug() {
        project.executor().run("assemblePlayFreeDebug")
        project.getApk(GradleTestProject.ApkType.DEBUG, "play", "free").use { outputFile ->
            assertThat(outputFile).doesNotContainJavaResource(TRANSFORM_MARKER_FILE)
        }
    }

    @Test
    fun checkRunForRelease() {
        val result = project.executor().run("assemblePlayFreeRelease")

        project.getApk(GradleTestProject.ApkType.RELEASE, "play", "free")
            .use { outputFile -> assertThat(outputFile).containsJavaResource(TRANSFORM_MARKER_FILE) }

        // Check arguments correct for provided variant info.
        assertThat(result.stderrAsLines)
            .containsAllOf(
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: true, "
                        + "variantName: playPaidDebug, "
                        + "buildType: debug, "
                        + "flavor: play, "
                        + "flavor: paid)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: false, "
                        + "variantName: playPaidRelease, "
                        + "buildType: release, "
                        + "flavor: play, "
                        + "flavor: paid)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: true, "
                        + "isDebuggable: true, "
                        + "variantName: playPaidDebugAndroidTest, "
                        + "buildType: debug, "
                        + "flavor: play, "
                        + "flavor: paid)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: true, "
                        + "variantName: playFreeDebug, "
                        + "buildType: debug, "
                        + "flavor: play, "
                        + "flavor: free)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: false, "
                        + "variantName: playFreeRelease, "
                        + "buildType: release, "
                        + "flavor: play, "
                        + "flavor: free)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: true, "
                        + "isDebuggable: true, "
                        + "variantName: playFreeDebugAndroidTest, "
                        + "buildType: debug, "
                        + "flavor: play, "
                        + "flavor: free)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: true, "
                        + "variantName: otherPaidDebug, "
                        + "buildType: debug, "
                        + "flavor: other, "
                        + "flavor: paid)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: false, "
                        + "variantName: otherPaidRelease, "
                        + "buildType: release, "
                        + "flavor: other, "
                        + "flavor: paid)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: true, "
                        + "isDebuggable: true, "
                        + "variantName: otherPaidDebugAndroidTest, "
                        + "buildType: debug, "
                        + "flavor: other, "
                        + "flavor: paid)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: true, "
                        + "variantName: otherFreeDebug, "
                        + "buildType: debug, "
                        + "flavor: other, "
                        + "flavor: free)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: false, "
                        + "isDebuggable: false, "
                        + "variantName: otherFreeRelease, "
                        + "buildType: release, "
                        + "flavor: other, "
                        + "flavor: free)",
                "applyToVariant called with variant=VariantInfo("
                        + "isTest: true, "
                        + "isDebuggable: true, "
                        + "variantName: otherFreeDebugAndroidTest, "
                        + "buildType: debug, "
                        + "flavor: other, "
                        + "flavor: free)"
            )
    }
}
private const val TRANSFORM_MARKER_FILE = "my_custom_transform_ran.txt"