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

package com.android.build.gradle.integration.common.fixture.app

/** Builder for the contents of an AndroidManifest.xml file. */
class ManifestFileBuilder(private val packageName: String) {

    private val tags = StringBuilder()

    fun addApplicationTag(activityClassName: String) {
        tags.append(
            """
            <application
                android:allowBackup="true"
                android:supportsRtl="true"
                android:theme="@style/Theme.AppCompat.Light">
                <activity android:name=".$activityClassName">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />

                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
            """.trimIndent()
        )
    }

    fun build(): String {
        val contents = StringBuilder()
        contents.append(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName">
            """.trimIndent()
        )
        if (!tags.isEmpty()) {
            contents.append("\n\n$tags\n\n")
        }
        contents.append("</manifest>")
        return contents.toString()
    }
}