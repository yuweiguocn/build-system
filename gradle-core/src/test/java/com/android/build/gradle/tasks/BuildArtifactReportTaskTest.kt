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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.scope.VariantBuildArtifactsHolder
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Test for [BuildArtifactReportTask].
 */
class BuildArtifactReportTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private lateinit var project : Project
    private lateinit var artifactsHolder: BuildArtifactsHolder
    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())
    private lateinit var task0: Task
    private lateinit var task1: Task
    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        artifactsHolder =
                VariantBuildArtifactsHolder(
                    project,
                    "debug",
                    project.file("root"),
                    dslScope)
        task0 = project.tasks.create("task0")
        task1 = project.tasks.create("task1")
        artifactsHolder.createDirectory(JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.INITIAL,
            task0.name,
            "javac_classes")
        artifactsHolder.createDirectory(JAVA_COMPILE_CLASSPATH,
            BuildArtifactsHolder.OperationType.INITIAL,
            task1.name,
            "java_compile_classpath")
    }

    @Test
    fun report() {
        val task = project.tasks.create("report", BuildArtifactReportTask::class.java)
        task.init(artifactsHolder::createReport)
        task.report()
    }

    @Test
    fun reportToFile() {
        val task = project.tasks.create("report", BuildArtifactReportTask::class.java)
        val outputFile = project.file("report.txt")
        task.init(artifactsHolder::createReport, outputFile)

        artifactsHolder.createBuildableArtifact(
            JAVAC_CLASSES,
            BuildArtifactsHolder.OperationType.TRANSFORM,
            project.files("classes").files,
            task1.name)

        task.report()

        val report = BuildArtifactsHolder.parseReport(outputFile)
        val javacArtifacts = report[JAVAC_CLASSES] ?: throw NullPointerException()
        assertThat(javacArtifacts).hasSize(2)
        assertThat(javacArtifacts[0].files.map(File::getName)).containsExactly("javac_classes")
        assertThat(javacArtifacts[0].builtBy).containsExactly(":task0")
        assertThat(javacArtifacts[1].files.map(File::getName)).containsExactly("classes")
        assertThat(javacArtifacts[1].builtBy).containsExactly(":task1")
    }
}