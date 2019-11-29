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

import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantBuildArtifactsHolder
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Test for [OutputFileProviderImpl]
 */
class OutputFileProviderImplTest {

    private val project = ProjectBuilder.builder().build()
    private val task = project.task("task")

    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())

    @Test
    fun replaceOutput() {
        val holder = newTaskOutputHolder()
        val output =
                OutputFileProviderImpl(
                        holder,
                        listOf(JAVAC_CLASSES),
                        listOf(),
                        task)
        val outputFile = output.getFile("foo")
        holder.createBuildableArtifact(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.TRANSFORM,
            listOf(outputFile),
            task.name)
        assertThat(outputFile).hasName("foo")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES).single()).hasName("foo")
    }

    @Test
    fun appendOutput() {
        val holder = newTaskOutputHolder()
        val output =
                OutputFileProviderImpl(
                        holder,
                        listOf(),
                        listOf(JAVAC_CLASSES),
                        task)
        val fooFile = output.getFile("foo")
        holder.createBuildableArtifact(
            JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL,
            listOf(fooFile),
            task.name)
        holder.createDirectory(
            JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.APPEND,
            task.name,
            "bar")
        assertThat(fooFile).hasName("foo")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES).map(File::getName)).containsExactly("foo", "bar")
    }

    @Test
    fun multipleFiles() {
        val holder = newTaskOutputHolder()
        val output =
                OutputFileProviderImpl(
                        holder,
                        listOf(JAVAC_CLASSES),
                        listOf(JAVA_COMPILE_CLASSPATH),
                        task)
        val fooFile = output.getFile("foo", JAVAC_CLASSES, JAVA_COMPILE_CLASSPATH)
        val barFile = output.getFile("bar", JAVA_COMPILE_CLASSPATH)
        holder.createBuildableArtifact(
            JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL,
            listOf(fooFile),
            task.name)
        holder.createDirectory(JAVA_COMPILE_CLASSPATH,
            BuildArtifactsHolder.OperationType.INITIAL,
            task.name,
            "classpath")
        holder.createBuildableArtifact(
            JAVA_COMPILE_CLASSPATH,
            BuildArtifactsHolder.OperationType.APPEND,
            listOf(barFile, fooFile),
            task.name)

        assertThat(output.getFile("foo")).hasName("foo")
        assertThat(output.getFile("bar")).hasName("bar")
        assertFailsWith<RuntimeException> { output.getFile("baz", InternalArtifactType.JAVAC) }

        assertThat(holder.getArtifactFiles(JAVAC_CLASSES).map(File::getName)).containsExactly("foo")
        assertThat(holder.getArtifactFiles(JAVA_COMPILE_CLASSPATH).map(File::getName))
                .containsExactly("foo", "bar", "classpath")
    }

    private fun newTaskOutputHolder() =
            VariantBuildArtifactsHolder(
                project,
                "debug",
                project.file("root"),
                dslScope)
}
