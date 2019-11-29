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

package com.android.build.gradle.integration.databinding

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Assemble tests for kotlin. */
@RunWith(FilterableParameterized::class)
class DataBindingKotlinAppTest(private val useV2: Boolean, useAndroidX: Boolean) {
    @Rule
    @JvmField
    val project =
        GradleTestProject.builder()
            .fromTestProject("databindingAndKotlin")
            .withDependencyChecker(false) // breaks w/ kapt
            .addGradleProperties(
                BooleanOption.ENABLE_DATA_BINDING_V2.propertyName
                        + "="
                        + useV2
            )
            .addGradleProperties(
                BooleanOption.USE_ANDROID_X.propertyName
                        + "="
                        + useAndroidX
            )
            .create()

    companion object {
        @Parameterized.Parameters(name = "useV2_{0}_useAndroidX_{1}")
        @JvmStatic
        fun params() = listOf(
            arrayOf(true, false),
            arrayOf(true, true),
            arrayOf(false, false),
            arrayOf(false, true)
        )
    }

    @Test
    fun compile() {
        project.executor().run("app:assembleDebug")
        val appBindingClass = "Lcom/example/android/kotlin/databinding/ActivityLayoutBinding;"
        val libBindingClass = "Lcom/example/android/kotlin/lib/databinding/LibActivityLayoutBinding;"
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).containsClass(appBindingClass)
        assertThat(apk).containsClass(libBindingClass)
        if (useV2) {
            // implementations should be in as well.
            assertThat(apk).containsClass(
                appBindingClass.replace(";", "Impl;")
            )
            assertThat(apk).containsClass(
                libBindingClass.replace(";", "Impl;")
            )
        }
    }

    @Test
    fun showErrorOnManualVersionMismatch() {
        val kapt = """
            dependencies {
                kapt "${SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT}:3.0.0"
            }
            """
        TestFileUtils.appendToFile(project.getSubproject(":app").buildFile, kapt)
        val result = project.executor().expectFailure().run("app:assembleDebug")
        assertThat(result.failureMessage)
            .contains("Data Binding annotation processor version needs to match the")
    }
}
