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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.options.BooleanOption
import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class DexingArtifactTransformTest {

    @Rule
    @JvmField
    val project =
        GradleTestProject.builder().fromTestApp(
            MinimalSubProject.app("com.example.test")).create()

    @Test
    fun testMonoDex() {
        project.buildFile.appendText("\nandroid.defaultConfig.multiDexEnabled = false")
        val result = executor().run("assembleDebug")
        assertThat(result.tasks).containsAllIn(listOf(":mergeExtDexDebug", ":mergeDexDebug"))
        assertThat(result.tasks).doesNotContain(":mergeLibDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeProjectDexDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
            .containsClass("Lcom/example/test/BuildConfig;")
    }

    @Test
    fun testMultiDex() {
        project.buildFile.appendText(
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 21
        """.trimIndent()
        )
        val result = executor().run("assembleDebug")
        assertThat(result.tasks).containsAllIn(
            listOf(
                ":mergeExtDexDebug",
                ":mergeProjectDexDebug",
                ":mergeLibDexDebug"
            )
        )
        assertThat(result.tasks).doesNotContain(":mergeDexDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
            .containsClass("Lcom/example/test/BuildConfig;")
    }

    @Test
    fun testLegacyMultiDex() {
        project.buildFile.appendText(
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 19
        """.trimIndent()
        )
        val result = executor().run("assembleDebug")
        // Merge legacy multidex in a single task. This is so synthesized classes that originate
        // from the main dex classes are packaged in the primary dex.
        assertThat(result.tasks).contains(":mergeDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeExtDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeLibDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeProjectDexDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
            .containsClass("Landroid/support/multidex/MultiDexApplication;")
    }

    @Test
    fun testAndroidTest() {
        project.buildFile.appendText(
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 21
        """.trimIndent()
        )
        val result = executor().run("assembleAndroidTest")
        assertThat(result.tasks).containsAllIn(
            listOf(
                ":mergeExtDexDebugAndroidTest",
                ":mergeLibDexDebugAndroidTest",
                ":mergeProjectDexDebugAndroidTest"
            )
        )
    }

    @Test
    fun testExternalDeps() {
        project.buildFile.appendText(
            """
            android.defaultConfig.minSdkVersion = 21
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).hasDexVersion(35)

        project.buildFile.appendText("\nandroid.defaultConfig.minSdkVersion = 26")
        executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).hasDexVersion(38)
    }

    @Test
    fun testAddingExternalDeps() {
        project.buildFile.appendText(
            """
            android.defaultConfig.minSdkVersion = 21
            android.defaultConfig.multiDexEnabled = true
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).hasDexVersion(35)

        project.buildFile.appendText(
            """

            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }
        """.trimIndent()
        )
        val run = executor().run("assembleDebug")
        assertThat(run.didWorkTasks).contains(":mergeExtDexDebug")
        assertThat(run.upToDateTasks).containsAllOf(":mergeLibDexDebug", ":mergeProjectDexDebug")
    }

    @Test
    fun testInstantRunDoesNotUseNewPipeline() {
        val result = executor().withInstantRun(AndroidVersion(21)).run("assembleDebug")
        assertThat(result.tasks).doesNotContain(":mergeExtDexDebug")
    }

    @Test
    fun testMinifiedDoesNotUseNewPipeline() {
        project.buildFile.appendText("\nandroid.buildTypes.debug.minifyEnabled true")
        val result = executor().run("assembleDebug")
        assertThat(result.tasks).doesNotContain(":mergeExtDexDebug")
    }

    @Test
    fun testDesugaringDoesNotUseNewPipeline() {
        project.buildFile.appendText("\nandroid.compileOptions.targetCompatibility 1.8")
        val result = executor().run("assembleDebug")
        assertThat(result.tasks).doesNotContain(":mergeExtDexDebug")
    }

    private fun executor() =
        project.executor().with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, true)
}