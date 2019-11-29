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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DataBindingWithFeaturesEnforceV2Test {
    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("databindingWithFeatures")
        .addGradleProperties(
                BooleanOption
                    .ENABLE_EXPERIMENTAL_FEATURE_DATABINDING.propertyName + "=true")
        .addGradleProperties(
                BooleanOption
                    .ENABLE_DATA_BINDING_V2.propertyName + "=false")
        .withDependencyChecker(false)
        .create()

    @Test
    fun mustEnableDataBindingV2() {
        val result = project.executor().expectFailure().run("clean", "assembleDebug")
        assertThat(result.failureMessage).contains(
                "you must enable data binding v2"
        )
    }
}
