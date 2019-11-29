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

package com.android.build.gradle.internal.crash

import com.google.common.truth.Truth.assertThat
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.junit.Test

class PluginExceptionReportTest {
    @Test
    fun testExceptionSerialized() {
        val pluginExceptionReport = PluginExceptionReport.create(NullPointerException())

        val builder = MultipartEntityBuilder.create()
        pluginExceptionReport!!.serialize(builder)

        val content = builder.build().content.bufferedReader().use {
            it.readText()
        }

        assertThat(content).contains("Content-Disposition: form-data; name=\"type\"")
        assertThat(content).contains(REPORT_TYPE)
        assertThat(content).contains("Content-Disposition: form-data; name=\"exception_info\"")
        assertThat(content).contains(
            "java.lang.NullPointerException: <message removed>\n" +
                    "\tat com.android.build.gradle.internal.crash.PluginExceptionReportTest.testExceptionSerialized(PluginExceptionReportTest.kt:"
        )
    }

    @Test
    fun testExceptionMessageRemoved() {
        val pluginExceptionReport =
            PluginExceptionReport.create(NullPointerException("message to be removed"))

        val builder = MultipartEntityBuilder.create()
        pluginExceptionReport!!.serialize(builder)

        val content = builder.build().content.bufferedReader().use {
            it.readText()
        }

        assertThat(content).doesNotContain("message to be removed")
    }
}