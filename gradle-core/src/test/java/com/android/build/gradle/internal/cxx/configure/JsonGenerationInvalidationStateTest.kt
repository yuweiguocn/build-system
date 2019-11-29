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

package com.android.build.gradle.internal.cxx.configure

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JsonGenerationInvalidationStateTest {
    private val expectedJson = File("./android_gradle.json")
    private val commandFile = File("./command_file.txt")
    private val dependentBuildFile = File("./CMakeLists.txt")

    @After
    fun after() {
        expectedJson.delete()
        commandFile.delete()
        dependentBuildFile.delete()
    }

    @Test
    fun testBasicClean() {
        createInTimestampOrder(dependentBuildFile, expectedJson, commandFile)
        val state = JsonGenerationInvalidationState(
                forceRegeneration = false,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "build command",
                previousBuildCommand = "build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isFalse()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly()
    }

    @Test
    fun testBasicDirty() {
        val state = JsonGenerationInvalidationState(
                forceRegeneration = false,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "current build command",
                previousBuildCommand = "previous build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly(
                "- expected json $expectedJson file is not present, will remove stale json folder",
                "- missing previous command file $commandFile, will remove stale json folder",
                "- command changed from previous, will remove stale json folder")
    }

    @Test
    fun testEverythingDirtyExceptDependentBuildFile() {
        // Note that dependent build file state is mutually exclusive with expectedJson missing
        val state = JsonGenerationInvalidationState(
                forceRegeneration = true,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "current build command",
                previousBuildCommand = "previous build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly(
                "- force flag, will remove stale json folder",
                "- expected json $expectedJson file is not present, will remove stale json folder",
                "- missing previous command file $commandFile, will remove stale json folder",
                "- command changed from previous, will remove stale json folder")
    }

    @Test
    fun testEverythingDirtyExceptMissingJson() {
        // Note that dependent build file state is mutually exclusive with expectedJson missing
        createInTimestampOrder(expectedJson, dependentBuildFile)
        val state = JsonGenerationInvalidationState(
                forceRegeneration = true,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "current build command",
                previousBuildCommand = "previous build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly(
                "- force flag, will remove stale json folder",
                "- missing previous command file $commandFile, will remove stale json folder",
                "- command changed from previous, will remove stale json folder",
                "- a dependent build file changed",
                "  - ${dependentBuildFile.absolutePath}")
    }

    @Test
    fun testBasicOnlyLastCommandChanged() {
        createInTimestampOrder(dependentBuildFile, expectedJson, commandFile)
        val state = JsonGenerationInvalidationState(
                forceRegeneration = false,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "current build command",
                previousBuildCommand = "previous build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly(
                "- command changed from previous, will remove stale json folder")
    }

    @Test
    fun testOnlyExpectJsonIsMissing() {
        createInTimestampOrder(commandFile)
        val state = JsonGenerationInvalidationState(
                forceRegeneration = false,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "build command",
                previousBuildCommand = "build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly(
                "- expected json $expectedJson file is not present, will remove stale json folder")
    }

    @Test
    fun testOnlyCommandFileIsMissing() {
        createInTimestampOrder(dependentBuildFile, expectedJson)
        val state = JsonGenerationInvalidationState(
                forceRegeneration = false,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "build command",
                previousBuildCommand = "build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly(
                "- missing previous command file $commandFile, will remove stale json folder")
    }

    @Test
    fun testOnlyForceFlag() {
        createInTimestampOrder(dependentBuildFile, expectedJson, commandFile)
        val state = JsonGenerationInvalidationState(
                forceRegeneration = true,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "build command",
                previousBuildCommand = "build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isFalse()
        assertThat(state.rebuildReasons).containsExactly(
                "- force flag, will remove stale json folder")
    }

    @Test
    fun testOnlyDependentBuildFileChanged() {
        createInTimestampOrder(expectedJson, commandFile, dependentBuildFile)
        val state = JsonGenerationInvalidationState(
                forceRegeneration = false,
                expectedJson = expectedJson,
                commandFile = commandFile,
                currentBuildCommand = "build command",
                previousBuildCommand = "build command",
                dependentBuildFiles = listOf(dependentBuildFile))
        assertThat(state.rebuild).isTrue()
        assertThat(state.softRegeneration).isTrue()
        assertThat(state.rebuildReasons).containsExactly(
                "- a dependent build file changed",
                "  - ${dependentBuildFile.absolutePath}"
        )
    }

    /*
    The best file system timestamp is millisecond and lower resolution is available depending on
    operating system and Java versions. This implementation of touch makes sure that the new
    timestamp isn't the same as the old timestamp by spinning until the clock increases.
     */
    private fun spinTouch(file: File, lastTimestamp: Long) {
        // This function repeatedly creates new File objects because Java can cache
        // get/setLastModified
        File(file.absolutePath).setLastModified(System.currentTimeMillis())
        while (getHighestResolutionTimeStamp(File(file.absolutePath)) <= lastTimestamp) {
            File(file.absolutePath).setLastModified(System.currentTimeMillis())
        }
    }

    private fun getHighestResolutionTimeStamp(file: File): Long {
        return Files.getLastModifiedTime(file.toPath()).toMillis()
    }

    /*
    Ensure that files have last modified timestamp in strictly ascending order.
     */
    private fun createInTimestampOrder(vararg files: File) {
        var lastTimestamp = 0L;
        for(file in files) {
            file.createNewFile()
            assertThat(file.exists()).isTrue()
            spinTouch(file, lastTimestamp)
            lastTimestamp = getHighestResolutionTimeStamp(File(file.absolutePath))
        }
    }
}