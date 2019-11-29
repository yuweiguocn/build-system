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

package com.android.build.gradle

import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.google.common.truth.Expect
import org.gradle.api.Project
import org.gradle.api.Task
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Path
import java.util.ArrayList
import java.util.HashMap

/**
 * Tests to ensure that no two tasks share an output.
 *
 * This currently only tests the tasks set up in the conventional project setup.
 */
class NoTaskOutputFileOverlapTest {
    @get:Rule
    val projectDirectory = TemporaryFolder()

    @get:Rule
    val expect = Expect.create()!!

    @Test
    @Throws(IOException::class)
    fun testLibrary() {
        val projectDir = projectDirectory.newFolder("library").toPath()
        val project = TestProjects.builder(projectDir)
            .withPlugin(TestProjects.Plugin.LIBRARY)
            .build()
        val android = project.extensions.getByType(LibraryExtension::class.java)
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        android.buildToolsVersion = TestConstants.BUILD_TOOL_VERSION
        val plugin = project.plugins.getPlugin(LibraryPlugin::class.java)
        plugin.createAndroidTasks()

        validateNoOverlappingTaskOutputs(project, projectDir)
    }
    @Test
    @Throws(IOException::class)
    fun testApplication() {
        val projectDir = projectDirectory.newFolder("library").toPath()
        val project = TestProjects.builder(projectDir)
            .withPlugin(TestProjects.Plugin.APP)
            .build()
        val android = project.extensions.getByType(AppExtension::class.java)
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        android.buildToolsVersion = TestConstants.BUILD_TOOL_VERSION
        val plugin = project.plugins.getPlugin(AppPlugin::class.java)
        plugin.createAndroidTasks()

        validateNoOverlappingTaskOutputs(project, projectDir)
    }


    private fun validateNoOverlappingTaskOutputs(
        project: Project,
        projectDir: Path
    ) {
        val outputToTasks = HashMap<String, MutableList<Task>>()
        for (task in project.tasks) {
            task.outputs.files.forEach { file ->
                val path = projectDir.relativize(file.toPath()).toString()
                outputToTasks.getOrPut(path, { ArrayList() }).add(task)
            }
        }
        outputToTasks.forEach { output, tasks ->
            if (tasks.size > 1) {
                expect.fail("Output file or directory $output shared between multiple tasks $tasks")
            }
        }
    }
}
