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

package com.android.build.gradle.integration.instant

import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.Logcat
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.OptionalCompilationStep
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.tools.ir.client.InstantRunArtifactType
import com.google.common.truth.Truth.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.regex.Pattern

@RunWith(Parameterized::class)
class ManifestFileChangeTest(val separateResourcesApk: Boolean) {

    @Rule @JvmField
    var mProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Rule @JvmField
    var logcat = Logcat.create()

    @Rule @JvmField
    val adb = Adb()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "separateResourcesApk={0}")
        fun getParameters(): Array<Boolean> {
            return arrayOf(java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFullAndIncrementalBuilds() {

        val instantRunModel = InstantRunTestUtils.getInstantRunModel(mProject.model().fetchAndroidProjects().onlyModel)

        val androidVersion = AndroidVersion(if (separateResourcesApk) SdkVersionInfo.HIGHEST_KNOWN_STABLE_API else 21, null)
        mProject.executor()
                .withInstantRun(androidVersion, OptionalCompilationStep.FULL_APK)
                .with(BooleanOption.ENABLE_SEPARATE_APK_RESOURCES, separateResourcesApk)
                .run("assembleDebug")

        val originalBuildInfo = InstantRunTestUtils.loadContext(instantRunModel)
        val artifactsNumber = originalBuildInfo.artifacts.size

        // now modify the manifest file.
        TemporaryProjectModification.doTest(mProject) { modifiedProject ->
            modifiedProject.replaceInFile(
                    "src/main/AndroidManifest.xml",
                    "android:label=\"@string/app_name\"",
                    "android:label=\"ManifestFileChangeTest\"")

            val run = mProject.executor()
                    .withInstantRun(androidVersion)
                    .with(
                            BooleanOption.ENABLE_SEPARATE_APK_RESOURCES,
                            separateResourcesApk)
                    .run("assembleDebug")
            assertThat(run.failureMessage).isNull()

            val modifiedBuildInfo = InstantRunTestUtils.loadContext(instantRunModel)
            assertThat(modifiedBuildInfo.verifierStatus).isEqualTo("MANIFEST_FILE_CHANGE")

            // the resources apk is not rebuilt when only manifest content is changed (unless
            // a referenced resource would be changed, covered in another test).
            assertThat(modifiedBuildInfo.artifacts).hasSize(
                    if (separateResourcesApk) (artifactsNumber-1) else artifactsNumber)

            val apks = InstantRunTestUtils.getArtifactsOfType(modifiedBuildInfo,
                    InstantRunArtifactType.SPLIT_MAIN)
            assertThat(apks).hasSize(1)
            val mainApk = apks[0]
            assertThat(mainApk.type).isEqualTo(InstantRunArtifactType.SPLIT_MAIN)

            TruthHelper.assertThatApk(mainApk.file).hasManifestContent(
                    Pattern.compile(".*ManifestFileChangeTest.*"))
        }
    }
}