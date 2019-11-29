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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.OptionalCompilationStep
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import org.junit.Rule
import org.junit.Test

class InstantRunSeparateTestModuleTest {

    @Rule
    @JvmField
    var mProject = GradleTestProject.builder()
            .fromTestProject("separateTestModule")
            .create()

    @Test
    @Throws(Exception::class)
    fun testFullAndIncrementalBuilds() {

        mProject.executor()
                .withInstantRun(AndroidVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API),
                        OptionalCompilationStep.FULL_APK)
                .run("assembleDebug")
    }
}