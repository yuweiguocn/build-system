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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.options.StringOption
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Test build transform using source sets.
 */

class SourceSetBuildTransformTest {
    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()


    @Test
    fun transformAndroidRes() {
        project.buildFile.appendText("""
import com.android.build.gradle.tasks.MergeResources

android {
    sourceSets {
        debug.res.replace(
            "generateCustomResource",
            DefaultTask.class) { task, input, output ->
                // force the output creation by looking up our output directory
                output.file
            }

        main.res.appendTo(
            "generateCustomResource1",
            DefaultTask.class) { task, input, output ->
                // force the output creation by looking up our output directory
                output.file
            }

        main.res.appendTo(
            "generateCustomResource2",
            DefaultTask.class) { task, input, output ->
                // force the output creation by looking up our output directory
                output.file
        }
    }
}

// Verify inputs are transformed.
gradle.taskGraph.whenReady { taskGraph ->
    def task = tasks.getByName("mergeDebugResources")
    def files = task.getResources().collect { it.files }.flatten()
    assert files.collect { it.name }.contains("android_resources1")
}
""")
        val result =
                project.executor()
                        .with(StringOption.BUILD_ARTIFACT_REPORT_FILE, "debugReport.txt")
                        .run("reportSourceSetTransformDebug", "assembleDebug")
        assertThat(result.getTask(":generateCustomResourceDebug")).wasUpToDate()
        assertThat(result.getTask(":generateCustomResourceDebug")).ranBefore(":mergeDebugResources")
        assertThat(result.getTask(":generateCustomResource1Main")).wasUpToDate()
        assertThat(result.getTask(":generateCustomResource1Main")).ranBefore(":mergeDebugResources")
        assertThat(result.getTask(":generateCustomResource2Main")).wasUpToDate()
        assertThat(result.getTask(":generateCustomResource2Main")).ranBefore(":mergeDebugResources")

        val debugReport = BuildArtifactsHolder.parseReport(project.file("debugReport.txt"))
        assertThat(debugReport).containsKey(SourceArtifactType.ANDROID_RESOURCES)

        val debugArtifacts = debugReport[SourceArtifactType.ANDROID_RESOURCES]!!
        assertThat(debugArtifacts[1].files.map(File::getName)).containsExactly("android_resources1")

        project.executor()
                .with(StringOption.BUILD_ARTIFACT_REPORT_FILE, "mainReport.txt")
                .run("reportSourceSetTransformMain")
        val mainReport = BuildArtifactsHolder.parseReport(project.file("mainReport.txt"))
        val mainArtifacts = mainReport[SourceArtifactType.ANDROID_RESOURCES]!!
        assertThat(mainArtifacts[1].files.map(File::getName))
                .containsExactly("android_resources1", "res")
        assertThat(mainArtifacts[2].files.map(File::getName))
                .containsExactly("android_resources2", "android_resources1", "res")
    }
}
