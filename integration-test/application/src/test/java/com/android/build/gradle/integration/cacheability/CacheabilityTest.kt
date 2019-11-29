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

package com.android.build.gradle.integration.cacheability

import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils

import com.google.common.collect.Sets
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Tests cacheability of tasks.
 *
 * See https://guides.gradle.org/using-build-cache/ for information on the Gradle build cache.
 */
@RunWith(JUnit4::class)
class CacheabilityTest {

    companion object {

        private const val GRADLE_BUILD_CACHE_DIR = "gradle-build-cache"

        /**
         * The expected states of tasks when running a second build with the Gradle build cache
         * enabled from an identical project at a different location.
         */
        private val EXPECTED_TASK_STATES =
            mapOf(
                UP_TO_DATE to setOf(
                    ":clean",
                    ":preBuild",
                    ":generateDebugResources",
                    ":compileDebugSources"
                ),
                FROM_CACHE to setOf(
                    ":preDebugBuild",
                    ":generateDebugBuildConfig",
                    ":javaPreCompileDebug",
                    ":generateDebugResValues",
                    ":mergeDebugResources",
                    ":compileDebugJavaWithJavac",
                    ":checkDebugDuplicateClasses",
                    ":mergeDebugShaders",
                    ":mergeDebugAssets",
                    ":mergeExtDexDebug",
                    ":mergeDexDebug",
                    ":mergeDebugJniLibFolders",
                    ":processDebugManifest",
                    ":processDebugResources",
                    ":mainApkListPersistenceDebug",
                    ":createDebugCompatibleScreenManifests",
                    ":validateSigningDebug",
                    ":signingConfigWriterDebug"
                ),
                DID_WORK to setOf(
                    ":checkDebugManifest",
                    ":prepareLintJar",
                    ":compileDebugShaders",
                    ":transformClassesWithDexBuilderForDebug",
                    ":transformNativeLibsWithMergeJniLibsForDebug",
                    ":transformNativeLibsWithStripDebugSymbolForDebug",
                    ":transformResourcesWithMergeJavaResForDebug",
                    ":packageDebug"
                ),
                SKIPPED to setOf(
                    ":compileDebugAidl",
                    ":compileDebugRenderscript",
                    ":generateDebugSources",
                    ":generateDebugAssets",
                    ":processDebugJavaRes",
                    ":assembleDebug"
                ),
                FAILED to setOf()
            )

        /**
         * Tasks that should be cacheable but are not yet cacheable.
         *
         * If you add a task to this list, remember to file a bug for it. The master bug for this
         * list is Bug 69668176.
         */
        private val NOT_YET_CACHEABLE = setOf(
            ":checkDebugManifest" /* Bug 74595857 */,
            ":prepareLintJar" /* Bug 120413672 */,
            ":compileDebugShaders" /* Bug 120413401 */,
            ":transformClassesWithDexBuilderForDebug" /* Bug 74595921 */,
            ":transformNativeLibsWithMergeJniLibsForDebug" /* Bug 74595223 */,
            ":transformNativeLibsWithStripDebugSymbolForDebug" /* Bug 120414535 */,
            ":transformResourcesWithMergeJavaResForDebug" /* Bug 74595224 */,
            ":packageDebug" /* Bug 74595859 */
        )

        /**
         * Tasks that are never cacheable.
         */
        private val NEVER_CACHEABLE = setOf<String>()
    }

    @get:Rule
    var projectCopy1 = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE_DIR"))
        .withName("projectCopy1")
        .dontOutputLogOnFailure()
        .create()

    @get:Rule
    var projectCopy2 = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE_DIR"))
        .withName("projectCopy2")
        .dontOutputLogOnFailure()
        .create()

    @Before
    fun setUp() {
        // Normally the APK name wouldn't include the project name. However, because the current
        // test project has only one module located at the root project (as opposed to residing in a
        // subdirectory under the root project), the APK name in this test does include the project
        // name, which would break relocatability. To fix that, we need to apply the following
        // workaround to use a generic name for the APK that is independent of the project name.
        //
        // NOTE: This project setup is not configured by default in Android Studio, so we assume
        // that not many users have this setup. However, if we later find that users do run into
        // cacheability issues because of this, we will need to think of a proper fix.
        TestFileUtils.appendToFile(projectCopy1.buildFile, "archivesBaseName = 'project'")
        TestFileUtils.appendToFile(projectCopy2.buildFile, "archivesBaseName = 'project'")
    }

    @Test
    fun testRelocatability() {
        // Build the first project
        val buildCacheDir = File(projectCopy1.testDir.parent, GRADLE_BUILD_CACHE_DIR)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
        projectCopy1.executor().withArgument("--build-cache").run("clean", "assembleDebug")

        // Check that the build cache has been populated
        assertThat(buildCacheDir).exists()

        // Build the second project
        val result =
            projectCopy2.executor().withArgument("--build-cache").run("clean", "assembleDebug")

        // When running this test with bazel, StripDebugSymbolTransform does not run as the NDK
        // directory is not available. We need to remove that task from the expected tasks' states.
        var expectedDidWorkTasks = EXPECTED_TASK_STATES[DID_WORK]!!
        if (result.findTask(":transformNativeLibsWithStripDebugSymbolForDebug") == null) {
            expectedDidWorkTasks =
                    expectedDidWorkTasks.minus(":transformNativeLibsWithStripDebugSymbolForDebug")
        }

        // Check that the tasks' states are as expected
        assertThat(result.upToDateTasks)
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[UP_TO_DATE]!!)
        assertThat(result.fromCacheTasks)
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[FROM_CACHE]!!)
        assertThat(result.didWorkTasks).containsExactlyElementsIn(expectedDidWorkTasks)
        assertThat(result.skippedTasks).containsExactlyElementsIn(EXPECTED_TASK_STATES[SKIPPED]!!)
        assertThat(result.failedTasks).containsExactlyElementsIn(EXPECTED_TASK_STATES[FAILED]!!)

        // Sanity-check that all the tasks that did work (were not cacheable) have been looked at
        // and categorized into either NOT_YET_CACHEABLE or NEVER_CACHEABLE.
        assertThat(EXPECTED_TASK_STATES[DID_WORK]).containsExactlyElementsIn(
            Sets.union(NOT_YET_CACHEABLE, NEVER_CACHEABLE)
        )

        // Clean up the cache
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }
}
