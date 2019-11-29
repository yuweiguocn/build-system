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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import com.google.common.base.Throwables
import org.gradle.tooling.BuildException
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException

/**
 * Integration test for the Jetifier feature.
 */
@RunWith(FilterableParameterized::class)
class JetifierTest(private val withKotlin: Boolean) {

    companion object {

        @Parameterized.Parameters(name = "withKotlin_{0}")
        @JvmStatic
        fun parameters() = listOf(
            arrayOf(true),
            arrayOf(false)
        )
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("jetifier")
        .withKotlinGradlePlugin(withKotlin)
        .create()

    @Before
    @Throws(IOException::class)
    fun setUp() {
        if (withKotlin) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "apply plugin: 'com.android.application'",
                "apply plugin: 'com.android.application'\n" +
                        "apply plugin: 'kotlin-android'\n" +
                        "apply plugin: 'kotlin-kapt'"
            )
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "annotationProcessor 'com.example.annotationprocessor:annotationProcessor:1.0'",
                "kapt 'com.example.annotationprocessor:annotationProcessor:1.0'"
            )
        }
    }

    @Test
    fun testJetifierDisabled() {
        // It's enough to test without Kotlin (to save test execution time)
        assumeFalse(withKotlin)

        // Build the project with Jetifier disabled
        project.executor().with(BooleanOption.ENABLE_JETIFIER, false).run("assembleDebug")
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        apk.use {
            // 1. Check that the old support library is not yet replaced with a new one
            assertThat(apk).containsClass("Landroid/support/v7/preference/Preference;")
            assertThat(apk).doesNotContainClass("Landroidx/preference/Preference;")

            // 2. Check that the library to refactor is not yet refactored
            assertThat(apk).hasClass("Lcom/example/androidlib/MyPreference;")
                .that().hasSuperclass("Landroid/support/v7/preference/Preference;")
        }
    }

    @Test
    fun testJetifierEnabledAndroidXEnabled() {
        prepareProjectForAndroidX()

        // Build the project with Jetifier enabled and AndroidX enabled
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .run("assembleDebug")
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        apk.use {
            // 1. Check that the old support library has been replaced with a new one
            assertThat(apk).doesNotContainClass("Landroid/support/v7/preference/Preference;")
            assertThat(apk).containsClass("Landroidx/preference/Preference;")

            // 2. Check that the library to refactor has been refactored
            assertThat(apk).hasClass("Lcom/example/androidlib/MyPreference;")
                .that().hasSuperclass("Landroidx/preference/Preference;")
        }
    }

    @Test
    fun testJetifierEnabledAndroidXDisabled() {
        // It's enough to test without Kotlin (to save test execution time)
        assumeFalse(withKotlin)

        // Build the project with Jetifier enabled but AndroidX disabled, expect failure
        try {
            project.executor()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, true)
                .run("assembleDebug")
            fail("Expected BuildException")
        } catch (e: BuildException) {
            assertThat(Throwables.getStackTraceAsString(e))
                .contains("AndroidX must be enabled when Jetifier is enabled.")
        }
    }

    @Test
    fun testAndroidArchNavigationLibrariesAreJetified() {
        // It's enough to test without Kotlin (to save test execution time)
        assumeFalse(withKotlin)

        // Regression test for https://issuetracker.google.com/79667498
        prepareProjectForAndroidX()

        // Add an android.arch.navigation dependency
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            "dependencies{\n" +
                    "implementation 'android.arch.navigation:navigation-fragment:1.0.0-alpha01'\n" +
                    "}\n"
        )

        // Build the project with Jetifier enabled and AndroidX enabled
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .run("assembleDebug")
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        apk.use {
            // Check that the android.arch.navigation library has been jetified
            assertThat(apk).hasClass("Landroidx/navigation/fragment/NavHostFragment;")
                .that().hasSuperclass("Landroidx/fragment/app/Fragment;")
        }
    }

    @Test
    fun testBlacklistedLibrariesAreNotJetified() {
        // It's enough to test without Kotlin (to save test execution time)
        assumeFalse(withKotlin)

        // Regression test for https://issuetracker.google.com/119135578
        prepareProjectForAndroidX()
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
            dependencies {
                implementation 'com.example.javalib:doNotJetifyLib:1.0'
            }
            """.trimIndent()
        )

        // We created doNotJetifyLib such that Jetifier would fail to jetify it.
        val result = project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .expectFailure()
            .run("assembleDebug")
        assertThat(result.stderr).contains(
            "Failed to transform artifact 'doNotJetifyLib.jar" +
                    " (com.example.javalib:doNotJetifyLib:1.0)"
        )

        // Add doNotJetifyLib to a blacklist, the build should succeed
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            """android.jetifier.blacklist = doNot.*\\.jar, foo"""
        )
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .run("assembleDebug")
    }

    @Test
    fun testStripSignatures() {
        // It's enough to test without Kotlin (to save test execution time)
        assumeFalse(withKotlin)

        prepareProjectForAndroidX()
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
            dependencies {
                implementation 'com.example.javalib:libWithSignatures:1.0'
            }
            """.trimIndent()
        )

        // Jetifier should be able to convert libWithSignatures
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .run("assembleDebug")
    }

    private fun prepareProjectForAndroidX() {
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "compileSdkVersion rootProject.latestCompileSdk",
            "compileSdkVersion \"android-28\""
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/main/java/com/example/app/MainActivity.java"),
            "import android.support.v7.app.AppCompatActivity;",
            "import androidx.appcompat.app.AppCompatActivity;"
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/main/java/com/example/app/DummyClassToTestAnnotationProcessing.java"),
            "import android.support.annotation.NonNull;",
            "import androidx.annotation.NonNull;"
        )
    }
}
