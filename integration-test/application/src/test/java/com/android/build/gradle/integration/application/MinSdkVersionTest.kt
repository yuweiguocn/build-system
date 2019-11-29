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

import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.builder.model.SyncIssue
import com.google.common.collect.Iterables
import org.junit.Rule
import org.junit.Test

/** Test basic project with min sdk version in the manifest.  */
class MinSdkVersionTest {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
            .fromTestApp(helloWorldApp)
            .create()

    companion object {
        val helloWorldApp = HelloWorldApp.forPlugin("com.android.application")
        init {
            helloWorldApp.replaceFile(
                    TestSourceFile(
                            "src/main/AndroidManifest.xml",
                            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                    + "      package=\"com.example.helloworld\"\n"
                                    + "      android:versionCode=\"1\"\n"
                                    + "      android:versionName=\"1.0\">\n"
                                    + "\n"
                                    + "    <uses-sdk android:minSdkVersion=\"3\" />\n"
                                    + "    <application android:label=\"@string/app_name\">\n"
                                    + "        <activity android:name=\".HelloWorld\"\n"
                                    + "                  android:label=\"@string/app_name\">\n"
                                    + "            <intent-filter>\n"
                                    + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                    + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                    + "            </intent-filter>\n"
                                    + "        </activity>\n"
                                    + "    </application>\n"
                                    + "</manifest>\n"))
        }
    }

    @Test
    fun checkMinSdkSyncIssue() {
        val model = project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel()

        val issues = model.getSyncIssues()
        assertThat(issues).hasSize(1)

        val issue = Iterables.getOnlyElement(issues)
        assertThat(issue).hasType(SyncIssue.TYPE_MIN_SDK_VERSION_IN_MANIFEST)
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_ERROR)
    }

}
