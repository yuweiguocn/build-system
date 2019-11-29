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

import com.android.build.VariantOutput
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * Integration test that test resConfig(s) settings with full or pure splits.
 */
@RunWith(FilterableParameterized::class)
class SplitHandlingTest(private val pureSplit: Boolean) {

    @get:Rule
    val project = GradleTestProject.builder()
                .fromTestProject("combinedDensityAndLanguagePureSplits")
                .create()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "pureSplits_{0}")
        fun data() = listOf(true, false)
    }

    /**
     * It is not allowed to have density based splits and resConfig(s) with a density restriction.
     */
    @Test
    @Throws(IOException::class)
    fun testDensityInResConfigAndSplits() {
        TestFileUtils.appendToFile(
                project.buildFile,
               "android {\n"
               + "    defaultConfig {\n"
               + "        resConfig \"xxhdpi\"\n"
               + "    }\n"
               + "    generatePureSplits " + pureSplit + "\n"
               + "    \n"
               + "    splits {\n"
               + "        density {\n"
               + "            enable true\n"
               + "        }\n"
               + "        language {\n"
               + "            enable false\n"
               + "        }\n"
               + "    }\n"
               + "}\n"
               + "\n"
               + "dependencies {\n"
               + "    compile 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
               + "'\n"
               + "    compile 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
               + "'\n"
               + "}\n")
        val failure = project.executeExpectingFailure("clean", "assembleDebug")
        val cause = getCause(failure.cause)
        assertThat(cause?.message).contains("xxhdpi")
    }

    /**
     * It is not allowed to have density based splits and resConfig(s) with a density restriction.
     */
    @Test
    @Throws(IOException::class)
    fun testDensitySplitsAndLanguagesInResConfig() {
        TestFileUtils.appendToFile(
                project.buildFile,
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        resConfigs \"fr\", \"de\"\n"
                        + "    }\n"
                        + "    generatePureSplits " + pureSplit + "\n"
                        + "    \n"
                        + "    splits {\n"
                        + "        density {\n"
                        + "            enable true\n"
                        + "        }\n"
                        + "        language {\n"
                        + "            enable false\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n")

        project.execute("clean", "assembleDebug")

        val apkOutputFolder = File(project.outputDir, "apk/debug")
        ExistingBuildElements.load(
                apkOutputFolder.toPath(),
                null,
                FileReader(ExistingBuildElements.getMetadataFile(apkOutputFolder)))
                .forEach { output ->
                    when(output.apkData.type) {
                        VariantOutput.OutputType.SPLIT -> {
                            val languageFilter = output.getFilter(VariantOutput.FilterType.LANGUAGE.name)
                            // no language splits.
                            assertThat(languageFilter).isNull()
                        }
                        VariantOutput.OutputType.MAIN, VariantOutput.OutputType.FULL_SPLIT -> {
                            val manifestContent = ApkSubject.getManifestContent(output.outputPath)
                            assertThat(manifestContains(manifestContent, "config fr:")).isTrue()
                            assertThat(manifestContains(manifestContent, "config de:")).isTrue()
                            assertThat(manifestContains(manifestContent, "config en:")).isFalse()

                        }
                    }
                }
    }


    /**
     * Test language splits with resConfig(s) splits. The split settings will generate full or pure
     * splits for the specified languages, all other language mentioned in resConfig will be
     * packaged in the main APK. Remaining languages will be dropped.
     */
    @Test
    @Throws(IOException::class)
    fun testLanguagesInResConfigAndSplits() {
        TestFileUtils.appendToFile(
                project.buildFile,
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        resConfig \"es\"\n"
                        + "    }\n"
                        + "    generatePureSplits " + pureSplit + "\n"
                        + "    \n"
                        + "    splits {\n"
                        + "        language {\n"
                        + "            enable true\n"
                        + "            include \"fr,fr-rBE\", \"fr-rCA\", \"en\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n")
        project.execute("clean", "assembleDebug")

        val apkOutputFolder = File(project.outputDir, "apk/debug")
        ExistingBuildElements.load(
                apkOutputFolder.toPath(),
                null,
                FileReader(ExistingBuildElements.getMetadataFile(apkOutputFolder)))
                .forEach { output ->
            when(output.apkData.type) {
                VariantOutput.OutputType.SPLIT -> {
                    val manifestContent = ApkSubject.getManifestContent(output.outputPath)
                    val languageFilter = output.getFilter(VariantOutput.FilterType.LANGUAGE.name)
                    if (languageFilter != null) {
                        for (language in languageFilter.split(",")) {
                            assertThat(manifestContains(manifestContent, "config $language:")).isTrue()
                        }
                    }
                }
                VariantOutput.OutputType.MAIN, VariantOutput.OutputType.FULL_SPLIT  -> {
                    val manifestContent = ApkSubject.getManifestContent(output.outputPath)
                    assertThat(manifestContains(manifestContent, "config es:")).isTrue()
                    assertThat(manifestContains(manifestContent, "config fr:")).isFalse()
                    assertThat(manifestContains(manifestContent, "config de:")).isFalse()

                }
            }
        }
    }

    /**
     * Languages splits without resConfigs. Main APK will contain all languages not packaged in
     * pure splits.
     */
    @Test
    @Throws(IOException::class)
    fun testLanguagesInSplitsWithoutResConfigs() {
        TestFileUtils.appendToFile(
                project.buildFile,
                "android {\n"
                        + "    generatePureSplits " + pureSplit + "\n"
                        + "    \n"
                        + "    splits {\n"
                        + "        language {\n"
                        + "            enable true\n"
                        + "            include \"fr,fr-rBE\", \"fr-rCA\", \"en\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n")
        project.execute("clean", "assembleDebug")

        val apkOutputFolder = File(project.outputDir, "apk/debug")
        ExistingBuildElements.load(
                apkOutputFolder.toPath(),
                null,
                FileReader(ExistingBuildElements.getMetadataFile(apkOutputFolder)))
                .forEach { output ->
                    when(output.apkData.type) {
                        VariantOutput.OutputType.SPLIT -> {
                            val manifestContent = ApkSubject.getManifestContent(output.outputPath)
                            val languageFilter = output.getFilter(VariantOutput.FilterType.LANGUAGE.name)
                            if (languageFilter != null) {
                                for (language in languageFilter.split(",")) {
                                    assertThat(manifestContains(manifestContent, "config $language:")).isTrue()
                                }
                            }
                        }
                        VariantOutput.OutputType.MAIN -> {
                            val manifestContent = ApkSubject.getManifestContent(output.outputPath)
                            // all remaining languages are packaged in the main APK.
                            assertThat(manifestContains(manifestContent, "config de:")).isTrue()
                            assertThat(manifestContains(manifestContent, "config fr:")).isFalse()
                            assertThat(manifestContains(manifestContent, "config en:")).isFalse()

                        }
                        VariantOutput.OutputType.FULL_SPLIT -> {
                            // we don't do language based multi-apk so all languages should be packaged.
                            val manifestContent = ApkSubject.getManifestContent(output.outputPath)
                            assertThat(manifestContains(manifestContent, "config es:")).isTrue()
                            assertThat(manifestContains(manifestContent, "config fr:")).isTrue()
                            assertThat(manifestContains(manifestContent, "config de:")).isTrue()
                        }
                    }
                }
    }

    private fun manifestContains(manifestContent: List<String> , pattern: String) : Boolean {
        return manifestContent
                .stream()
                .filter({ line -> line.contains(pattern) })
                .findFirst()
                .isPresent
    }

    private fun getCause(t: Throwable?) : Throwable? {
        var cause = t
        while (cause?.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        return cause
    }
}