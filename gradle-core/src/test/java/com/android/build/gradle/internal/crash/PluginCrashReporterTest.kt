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

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettingsData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import com.android.build.gradle.internal.crash.PluginCrashReporter.maybeReportExceptionForTest as reportForTest

class PluginCrashReporterTest {
    @Test
    fun testUserOptOut() {
        val settings = AnalyticsSettingsData()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = false
        assertThat(reportForTest(NullPointerException())).isFalse()
    }

    @Test
    fun testReportingWhiteListedException() {
        val settings = AnalyticsSettingsData()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = true

        assertThat(reportForTest(NullPointerException())).isTrue()
        assertThat(reportForTest(RuntimeException(NullPointerException())))
            .isTrue()
        assertThat(
            reportForTest(RuntimeException(RuntimeException(NullPointerException())))
        ).isTrue()
    }

    @Test
    fun testReportingNonWhiteListedException() {
        val settings = AnalyticsSettingsData()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = true

        assertThat(reportForTest(RuntimeException())).isFalse()
        assertThat(reportForTest(IllegalStateException(RuntimeException()))).isFalse()
    }

    @Test
    fun testExternalApiUsageException() {
        val settings = AnalyticsSettingsData()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = true

        assertThat(reportForTest(ExternalApiUsageException(RuntimeException()))).isFalse()
    }

    @Test
    fun testReportingInitializesAnalyticsSettings() {
        assertThat(reportForTest(NullPointerException())).isTrue()
        assertThat(AnalyticsSettings.initialized).isTrue()
    }
}
