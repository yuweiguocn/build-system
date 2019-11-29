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

package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildArtifactTransformBuilder.ConfigurationAction
import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantBuildArtifactsHolder
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Test for [BuildArtifactTransformBuilder].
 */
class BuildArtifactTransformBuilderImplTest {

    private val project = ProjectBuilder().build()!!
    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())
    private val buildableArtifactsActions = DelayedActionsExecutor()
    private lateinit var builder : BuildArtifactTransformBuilder<TestClassWithExpectations>
    private lateinit var taskHolder : BuildArtifactsHolder

    @Before
    fun setUp() {
        taskHolder =
                VariantBuildArtifactsHolder(
                    project,
                    "debug",
                    project.file("root"),
                    dslScope)
        builder = BuildArtifactTransformBuilderImpl(
                project,
                taskHolder,
                buildableArtifactsActions,
                "test",
                TestClassWithExpectations::class.java,
                dslScope)
    }

    @Test
    fun default() {
        var configuredTask : Task? = null
        builder.create { input ,output ->
                    configuredTask = this
                    assertThat(this).isInstanceOf(TestClassWithExpectations::class.java)
                    assertThat(name).isEqualTo("testDebug")
                    assertFailsWith<RuntimeException> { input.artifact }
                    assertFailsWith<RuntimeException> { input.getArtifact(JAVAC_CLASSES) }
                    assertFailsWith<RuntimeException> { output.file }
                    assertFailsWith<RuntimeException> { output.getFile("output.txt") }
                }
        val task = project.tasks.single()
        buildableArtifactsActions.runAll()

        assertThat(task).isSameAs(configuredTask)
    }

    @Test
    fun defaultWithConfigAction() {
        var configuredTask : Task? = null
        builder.create(
                object : ConfigurationAction<TestClassWithExpectations> {
                    override fun accept(
                            task: TestClassWithExpectations,
                            input: InputArtifactProvider,
                            output: OutputFileProvider) {
                        configuredTask = task
                        assertThat(task).isInstanceOf(TestClassWithExpectations::class.java)
                        assertThat(task.name).isEqualTo("testDebug")
                        assertFailsWith<RuntimeException> { input.artifact }
                        assertFailsWith<RuntimeException> { input.getArtifact(JAVAC_CLASSES) }
                        assertFailsWith<RuntimeException> { output.file }
                        assertFailsWith<RuntimeException> { output.getFile("output.txt") }
                    }
                })
        val task = project.tasks.single()
        buildableArtifactsActions.runAll()

        assertThat(task).isSameAs(configuredTask)
    }

    @Test
    fun singleInput() {
        var input : InputArtifactProvider? = null
        taskHolder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL,
            project.files("javac"))
        builder
                .append(JAVAC_CLASSES)
                .create { i, _ ->
                    input = i
                    assertThat(this).isInstanceOf(TestClassWithExpectations::class.java)
                    assertThat(i.getArtifact(JAVAC_CLASSES)).isSameAs(i.artifact)
                }
        buildableArtifactsActions.runAll()
        assertThat(input!!.artifact.map(File::getName)).containsExactly("javac")
    }

    @Test
    fun multiInput() {
        builder
            .append(JAVAC_CLASSES)
            .append(JAVA_COMPILE_CLASSPATH)
            .create { i, o ->
                assertThat(this).isInstanceOf(TestClassWithExpectations::class.java)
                assertFailsWith<RuntimeException> { i.artifact }
                assertFailsWith<RuntimeException> { o.file }
            }
        val task = project.tasks.single()
        taskHolder.createDirectory(
            JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL,
            task.name,
            "javac")
        taskHolder.createDirectory(
            JAVA_COMPILE_CLASSPATH,
            BuildArtifactsHolder.OperationType.INITIAL,
            task.name,
            "classpath")
        buildableArtifactsActions.runAll()

        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).map(File::getName))
                .containsExactly("javac")
        assertThat(taskHolder.getArtifactFiles(JAVA_COMPILE_CLASSPATH).map(File::getName))
                .containsExactly("classpath")
    }

    @Test
    fun output() {
        var output : OutputFileProvider? = null
        builder
                .replace(JAVAC_CLASSES)
                .append(JAVA_COMPILE_CLASSPATH)
                .create { i ,o ->
                    output = o
                    o.getFile("foo", JAVAC_CLASSES)
                    o.getFile("bar", JAVA_COMPILE_CLASSPATH)
                    o.getFile("baz")
                    assertThat(this).isInstanceOf(TestClassWithExpectations::class.java)
                    assertFailsWith<RuntimeException> { i.artifact }
                    assertFailsWith<RuntimeException> { i.getArtifact(InternalArtifactType.AAR) }
                    assertFailsWith<RuntimeException> { o.file }
                }
        val task = project.tasks.single()
        taskHolder.createDirectory(
            JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL,
            task.name,
            "javac")
        taskHolder.createDirectory(
            JAVA_COMPILE_CLASSPATH,
            BuildArtifactsHolder.OperationType.INITIAL,
            task.name,
            "classpath")
        buildableArtifactsActions.runAll()

        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly("foo", "baz")
        assertThat(taskHolder.getArtifactFiles(JAVA_COMPILE_CLASSPATH).files.map(File::getName))
                .containsExactly("bar", "baz", "classpath")
        assertThat(output!!.getFile("foo")).hasName("foo")
        assertThat(output!!.getFile("bar")).hasName("bar")
        assertThat(output!!.getFile("baz")).hasName("baz")
    }

    @Test
    fun checkSeal() {
        (builder as BuildArtifactTransformBuilderImpl).seal()
        assertFailsWith<RuntimeException> { builder.input(JAVAC_CLASSES) }
        assertFailsWith<RuntimeException> { builder.replace(JAVAC_CLASSES) }
        assertFailsWith<RuntimeException> { builder.create { _ , _ -> } }
    }

    open class TestClassWithExpectations : DefaultTask() {

        var input: BuildableArtifact? = null
        var output: File? = null
        var expectations: Map<com.android.build.api.artifact.ArtifactType, Collection<File>>? = null

        fun verify() {
            if (expectations == null || expectations!!.isEmpty()) {
                return
            }
            expectations!!.forEach { _, value ->
                assertThat(input!!.files).containsAllIn(value)
            }
        }
    }

    @Test
    fun chainedAppend() {
        taskHolder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL,
            project.files("initial_file"))
        taskHolder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.APPEND,
            project.files("updated_file"))
        taskHolder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.APPEND,
            project.files("final_file"))
        builder.append(JAVAC_CLASSES)
            .create{ i, o ->
                this.input = i.artifact
                this.output = o.file
                this.expectations = mapOf(JAVAC_CLASSES to
                        project.files("initial_file")
                            .plus(project.files("updated_file")
                                .plus(project.files("final_file").files)))
        }
        val initialTask = project.tasks.withType(TestClassWithExpectations::class.java).single()
        buildableArtifactsActions.runAll()
        initialTask.verify()
    }

    @Test
    fun chainedReplace() {
        taskHolder.createBuildableArtifact(JAVAC_CLASSES, BuildArtifactsHolder.OperationType.INITIAL, project.files("initial_file"))
        taskHolder.createBuildableArtifact(JAVAC_CLASSES, BuildArtifactsHolder.OperationType.TRANSFORM, project.files("replaced_file").files,
            project.tasks.create("replaceTask").name)
        taskHolder.createBuildableArtifact(JAVAC_CLASSES, BuildArtifactsHolder.OperationType.APPEND, project.files("final_file"))
        builder.append(JAVAC_CLASSES)
            .create{ i, o ->
                this.input = i.artifact
                this.output = o.file
                this.expectations = mapOf(JAVAC_CLASSES to
                        project.files("replaced_file").plus(project.files("final_file").files))
            }
        val initialTask = project.tasks.withType(TestClassWithExpectations::class.java).single()
        buildableArtifactsActions.runAll()
        initialTask.verify()
   }

    @Test fun finalInput() {
        taskHolder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL, project.files("initial_file"))
        builder.append(JAVAC_CLASSES)
            .create{ i, o ->
                this.input = i.artifact
                this.output = o.getFile("updated_file")
                // since we requested the version of JAVAC_CLASSES as of now, only 1 file.
                this.expectations = mapOf(JAVAC_CLASSES to project.files("initial_file").files)
            }
        val initialTask = project.tasks.withType(TestClassWithExpectations::class.java).single()
        val anotherBuilder:BuildArtifactTransformBuilder<TestClassWithExpectations> =
            BuildArtifactTransformBuilderImpl(
                project,
                taskHolder,
                buildableArtifactsActions,
                "anotherTest",
                TestClassWithExpectations::class.java,
                dslScope)
        anotherBuilder
            .input(JAVAC_CLASSES)
            .append(JAVA_COMPILE_CLASSPATH)
            .create{ i, o ->
                this.input = i.getArtifact(JAVAC_CLASSES)
                this.output = o.getFile("updated_File")

                // since we requested the final version of JAVAC_CLASSES, we should see all.
                this.expectations = mapOf(JAVAC_CLASSES to
                        project.files("initial_file")
                            .plus(project.files("yet_another_file"))
                            .plus(project.files("final_file")).files)
            }
        val anotherTask = project.tasks.withType(TestClassWithExpectations::class.java).matching { it != initialTask } .single()

        // add some more files to the artifact
        taskHolder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.APPEND,
            project.files("yet_another_file"))
        taskHolder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.APPEND,
            project.files("final_file"))

        buildableArtifactsActions.runAll()
        initialTask.verify()
        anotherTask.verify()
    }
}