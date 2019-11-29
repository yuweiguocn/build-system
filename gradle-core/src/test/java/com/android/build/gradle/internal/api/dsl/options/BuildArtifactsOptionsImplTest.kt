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

package com.android.build.gradle.internal.api.dsl.options

import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.api.dsl.options.BuildArtifactsOptions
import com.android.build.gradle.internal.api.artifact.BuildArtifactTransformBuilderImpl
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.TestClass
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.scope.VariantBuildArtifactsHolder
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.test.assertFailsWith

/**
 * Tests for [BuildArtifactsOptions].
 */
class BuildArtifactsOptionsImplTest {
    open class TestTask : DefaultTask() {
        @InputFiles
        lateinit var inputFiles : BuildableArtifact

        @OutputFile
        lateinit var outputFile : File
    }

    private val issueReporter = FakeEvalIssueReporter(throwOnError = true)
    private val dslScope = DslScopeImpl(issueReporter, FakeDeprecationReporter(), FakeObjectFactory())
    lateinit private var project : Project
    lateinit private var taskHolder : BuildArtifactsHolder
    private val buildArtifactsActions= DelayedActionsExecutor()
    lateinit private var options : BuildArtifactsOptions
    lateinit private var task0 : Task

    @Before
    fun setUp() {
        project = ProjectBuilder().build()!!
        taskHolder =
                VariantBuildArtifactsHolder(
                    project,
                    "debug",
                    project.file("root"),
                    dslScope)
        options = BuildArtifactsOptionsImpl(project, taskHolder, buildArtifactsActions, dslScope)
        task0 = project.tasks.create("task0")
        taskHolder.appendArtifact(JAVAC_CLASSES, task0, "out")
    }

    @Test
    fun appendTo() {
        options.appendTo(JAVAC_CLASSES, "task1", TestTask::class.java) { input, output ->
            inputFiles = input
            outputFile = output
        }
        buildArtifactsActions.runAll()

        project.tasks.getByName("task1Debug") { t ->
            assertThat(t).isInstanceOf(TestTask::class.java)
            val task = t as TestTask
            assertThat(task.inputFiles.single()).hasName("out")
            assertThat(task.outputFile).hasName("${JAVAC_CLASSES.name.toLowerCase(Locale.US)}1")
            // TaskDependency.getDependencies accepts null.
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            assertThat(task.inputFiles.buildDependencies.getDependencies(null))
                    .containsExactly(task0)
        }
        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly("${JAVAC_CLASSES.name.toLowerCase(Locale.US)}1", "out")
    }

    @Test
    fun appendToConfigurationAction() {
        options.appendTo(
                JAVAC_CLASSES,
                "task1",
                TestTask::class.java,
                object : BuildArtifactTransformBuilder.ConfigurationAction<TestTask> {
                    override fun accept(
                            task: TestTask,
                            input: InputArtifactProvider,
                            output: OutputFileProvider) {
                        task.inputFiles = input.artifact
                        task.outputFile = output.file
                    }
                })
        buildArtifactsActions.runAll()

        project.tasks.getByName("task1Debug") { t ->
            assertThat(t).isInstanceOf(TestTask::class.java)
            val task = t as TestTask
            assertThat(task.inputFiles.single()).hasName("out")
            assertThat(task.outputFile).hasName("${JAVAC_CLASSES.name.toLowerCase(Locale.US)}1")
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            assertThat(task.inputFiles.buildDependencies.getDependencies(null))
                    .containsExactly(task0)
        }
        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly("${JAVAC_CLASSES.name.toLowerCase(Locale.US)}1", "out")
    }

    @Test
    fun replace() {
        options.replace(
                JAVAC_CLASSES,
                "task1",
                TestTask::class.java,
                object : BuildArtifactTransformBuilder.ConfigurationAction<TestTask> {
                    override fun accept(
                            task: TestTask,
                            input: InputArtifactProvider,
                            output: OutputFileProvider) {
                        task.inputFiles = input.artifact
                        task.outputFile = output.file
                    }
                })
        buildArtifactsActions.runAll()

        project.tasks.getByName("task1Debug") { t ->
            assertThat(t).isInstanceOf(TestTask::class.java)
            val task = t as TestTask
            assertThat(task.inputFiles.single()).hasName("out")
            assertThat(task.outputFile).hasName("${JAVAC_CLASSES.name.toLowerCase(Locale.US)}1")
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            assertThat(task.inputFiles.buildDependencies.getDependencies(null))
                    .containsExactly(task0)
        }
        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly("${JAVAC_CLASSES.name.toLowerCase(Locale.US)}1")
    }

    @Test
    fun builder() {
        val builder = options.builder("task1", TestTask::class.java)

        assertThat(builder).isInstanceOf(BuildArtifactTransformBuilder::class.java)
        builder.create { input, output ->
            assertFailsWith<RuntimeException> { input.artifact }
            assertFailsWith<RuntimeException> { output.file }
        }
    }

    @Test
    fun checkSeal() {
        val builder = options.builder("task1", TestTask::class.java)
        (options as BuildArtifactsOptionsImpl).seal()
        assertThat((builder as BuildArtifactTransformBuilderImpl).isSealed()).isTrue()
    }
}