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

import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus
import com.android.build.gradle.tasks.InstantRunResourcesApkBuilder
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.tools.ir.client.InstantRunArtifactType
import com.google.common.collect.Iterables
import org.junit.Rule
import org.junit.Test

class ResourcesAccessInManifest {
    @get:Rule
    var project: GradleTestProject

    @get:Rule
    var adb = Adb()

    init {
        project = GradleTestProject.builder().fromTestApp(sApp).create()
    }

    @Test
    @Throws(Exception::class)
    fun testBuild() {

        val instantRunModel = InstantRunTestUtils.getInstantRunModel(project.model().fetchAndroidProjects().onlyModel)

        InstantRunTestUtils.doInitialBuild(project, AndroidVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, null))

        var context = InstantRunTestUtils.loadContext(instantRunModel)
        TruthHelper.assertThat(context.verifierStatus)
                .isEqualTo(InstantRunVerifierStatus.FULL_BUILD_REQUESTED.toString())

        // now touch the resources.
        val resFile = project.file("src/main/res/values/no_translate.xml")
        TestFileUtils.replaceLine(resFile, 3,
                "<string name=\"app_version\">2.7.0</string>")

        project.executor()
                .withInstantRun(AndroidVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, null))
                .run("assembleDebug")

        context = InstantRunTestUtils.loadContext(instantRunModel)
        assertThat(context.artifacts).hasSize(1)
        val artifact = Iterables.getOnlyElement(context.artifacts)
        assertThat(artifact.type).isEqualTo(InstantRunArtifactType.SPLIT)
        assertThat(artifact.file.name).startsWith(InstantRunResourcesApkBuilder.APK_FILE_NAME)
    }

    companion object {

        private val sApp = HelloWorldApp.forPlugin("com.android.application")

        init {
            val manifestFile = TestSourceFile(
                    "src/main",
                    "AndroidManifest.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "      package=\"com.example.helloworld\"\n"
                            + "      android:versionName=\"@string/app_version\"\n"
                            + "      android:versionCode=\"1\">\n"
                            + "\n"
                            + "    <application android:label=\"@string/app_name\">\n"
                            + "        <activity android:name=\".HelloWorld\"\n"
                            + "                  android:label=\"@string/app_name\">\n"
                            + "            <intent-filter>\n"
                            + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                            + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "</manifest>\n")
            sApp.replaceFile(manifestFile)

            val resFile = TestSourceFile(
                    "src/main/res/values",
                    "no_translate.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources>\n"
                            + "    <string name=\"app_version\">2.6.0</string>\n"
                            + "</resources>\n")
            sApp.addFile(resFile)
        }
    }
}
