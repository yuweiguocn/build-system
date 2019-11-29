/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.truth.GradleOutputFileSubject
import com.android.build.gradle.integration.common.truth.GradleOutputFileSubjectFactory
import com.android.build.gradle.integration.common.truth.TaskStateList
import com.android.builder.model.ProjectBuildOutput
import com.google.api.client.repackaged.com.google.common.base.Preconditions
import com.google.api.client.repackaged.com.google.common.base.Throwables
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assert_
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.internal.serialize.ContextualPlaceholderException
import org.gradle.internal.serialize.PlaceholderException
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.events.ProgressEvent
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The result from running a build.
 * See [GradleTestProject.executor] and [GradleTaskExecutor].
 *
 * @property exception The exception from the build, null if the build succeeded.
 */
class GradleBuildResult @JvmOverloads constructor(
    stdout: ByteArrayOutputStream,
    stderr: ByteArrayOutputStream,
    private val taskEvents: ImmutableList<ProgressEvent>,
    val exception: GradleConnectionException?,
    buildOutputContainer: ModelContainer<ProjectBuildOutput>? = null
) {

    val stdout: String = stdout.toString()
    val stderr: String = stderr.toString()

    private val _buildOutputContainer = buildOutputContainer
    val buildOutputContainer: ModelContainer<ProjectBuildOutput>
        get() = _buildOutputContainer
                ?: throw IllegalStateException("ProjectBuildOutput models were not fetched by the "
                    + "build. Make sure to use GradleTaskExecutor::withOutputModelQuery.")

    /**
     * Most tests don't examine the state of the build's tasks and [TaskStateList] is relatively
     * expensive to initialize, so this is done lazily.
     */
    private val taskStates: TaskStateList by lazy {
        TaskStateList(taskEvents, this.stdout)
    }

    /**
     * Returns the short (single-line) message that Gradle would print out in the console, without
     * `--stacktrace`. If the build succeeded, returns null.
     */
    val failureMessage: String?
        get() = exception?.let {
            val causalChain = Throwables.getCausalChain(exception)
            // Try the common scenarios: configuration or task failure.
            for (throwable in causalChain) {
                // Because of different class loaders involved, we are forced to do stringly-typed
                // programming.
                val throwableType = throwable.javaClass.name
                if (throwableType == ProjectConfigurationException::class.java.name) {
                    return throwable.cause?.message ?: throw AssertionError(
                        "Exception had unexpected structure.",
                        exception
                    )
                } else if (isPlaceholderEx(throwableType)) {
                    if (throwable.toString().startsWith(TaskExecutionException::class.java.name)) {
                        var cause = throwable
                        // there can be several levels of PlaceholderException when dealing with
                        // Worker API failures.
                        while (isPlaceholderEx(throwableType) && cause.cause != null) {
                            cause = cause.cause
                        }
                        return cause.message
                    }
                }
            }

            // Look for any BuildException, for other cases.
            for (throwable in causalChain) {
                val throwableType = throwable.javaClass.name
                if (throwableType == BuildException::class.java.name) {
                    return throwable.cause?.message ?: throw AssertionError(
                        "Exception had unexpected structure.",
                        exception
                    )
                }
            }

            throw AssertionError("Failed to determine the failure message.", exception)
        }

    val stdoutAsLines: List<String>
        get() = Splitter.on(System.lineSeparator()).omitEmptyStrings().split(stdout).toList()

    val stderrAsLines: List<String>
        get() = Splitter.on(System.lineSeparator()).omitEmptyStrings().split(stderr).toList()

    val tasks: List<String>
        get() = taskStates.tasks

    val upToDateTasks: Set<String>
        get() = taskStates.upToDateTasks

    val fromCacheTasks: Set<String>
        get() = taskStates.fromCacheTasks

    val didWorkTasks: Set<String>
        get() = taskStates.didWorkTasks

    val skippedTasks: Set<String>
        get() = taskStates.skippedTasks

    val failedTasks: Set<String>
        get() = taskStates.failedTasks

    /**
     * Truth style assert to check changes to a file.
     */
    fun assertThatFile(subject: File): GradleOutputFileSubject {
        return assert_().about(GradleOutputFileSubjectFactory.factory(stdout)).that(subject)
    }

    /**
     * Returns the task info given the task name, or null if the task is not found (if it is not in
     * the task execution plan).
     *
     * @see getTask
     */
    fun findTask(name: String): TaskStateList.TaskInfo? {
        return taskStates.findTask(name)
    }

    /**
     * Returns the task info given the task name. The task must exist (it must be in the task
     * execution plan).
     *
     * @see findTask
     */
    fun getTask(name: String): TaskStateList.TaskInfo {
        Preconditions.checkArgument(name.startsWith(":"), "Task name must start with :")
        return taskStates.getTask(name)
    }

    private fun isPlaceholderEx(throwableType: String) =
        throwableType == PlaceholderException::class.java.name
                || throwableType == ContextualPlaceholderException::class.java.name
}
