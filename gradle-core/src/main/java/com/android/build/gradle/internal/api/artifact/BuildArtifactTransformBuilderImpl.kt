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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

/**
 * Implementation of VariantTaskBuilder.
 */
class BuildArtifactTransformBuilderImpl<out T : Task>(
        private val project: Project,
        private val artifactsHolder: BuildArtifactsHolder,
        private val artifactsActionsExecutor: DelayedActionsExecutor,
        private val taskNamePrefix: String,
        private val taskType: Class<T>,
        dslScope: DslScope)
    : SealableObject(dslScope), BuildArtifactTransformBuilder<T> {

    private val finalInputs = mutableListOf<ArtifactType>()
    private val replacedOutput = mutableListOf<ArtifactType>()
    private val appendedOutput = mutableListOf<ArtifactType>()

    override fun append(artifactType: ArtifactType): BuildArtifactTransformBuilderImpl<T> {
        if (!checkSeal()) {
            return this
        }

        if (replacedOutput.contains(artifactType)) {
            dslScope.issueReporter.reportError(
                EvalIssueReporter.Type.GENERIC,
                EvalIssueException("""Output type '$artifactType' has already been specified to be
                    replaced with the replace() API"""))
            return this
        }
        val spec = BuildArtifactSpec.get(artifactType)
        if (!spec.appendable) {
            dslScope.issueReporter.reportError(
                EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Appending to ArtifactType '$artifactType' is not allowed."))
            return this
        }
        appendedOutput.add(artifactType)
        return this
    }

    override fun replace(artifactType: ArtifactType): BuildArtifactTransformBuilderImpl<T> {
        if (!checkSeal()) {
            return this
        }

        if (appendedOutput.contains(artifactType)) {
            dslScope.issueReporter.reportError(
                EvalIssueReporter.Type.GENERIC,
                EvalIssueException(
                """Output type '$artifactType' has already been specified to be
                    appended with the append() API"""))
            return this
        }
        val spec = BuildArtifactSpec.get(artifactType)
        if (!spec.replaceable) {
            dslScope.issueReporter.reportError(
                EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Replacing ArtifactType '$artifactType' is not allowed."))
            return this
        }
        replacedOutput.add(artifactType)
        return this
    }

    override fun input(artifactType: ArtifactType)  : BuildArtifactTransformBuilderImpl<T> {
        if (!checkSeal()) {
            return this
        }
        if (finalInputs.contains(artifactType)) {
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    EvalIssueException("Output type '$artifactType' was already specified as an input."))
            return this
        }
        finalInputs.add(artifactType)
        return this
    }

    override fun create(action : BuildArtifactTransformBuilder.ConfigurationAction<T>) {
        create(action, null)
    }

    override fun create(function: T.(InputArtifactProvider, OutputFileProvider) -> Unit) {
        create(null, function)
    }

    private fun create(
            action : BuildArtifactTransformBuilder.ConfigurationAction<T>?,
            function : ((T, InputArtifactProvider, OutputFileProvider) -> Unit)?) : T {
        val taskName = artifactsHolder.getTaskName(taskNamePrefix)
        val task = project.tasks.create(taskName, taskType)
        if (!checkSeal()) {
            return task
        }
        val chainInputs = appendedOutput.plus(replacedOutput)
        val inputProvider = InputArtifactProviderImpl(
            artifactsHolder,
            finalInputs,
            chainInputs,
            project.files(),
            dslScope)
        val outputProvider =
                OutputFileProviderImpl(
                        artifactsHolder,
                        replacedOutput,
                        appendedOutput,
                        task)

        artifactsActionsExecutor.addAction {
            try {
                when {
                    action != null -> action.accept(task, inputProvider, outputProvider)
                    function != null -> function(task, inputProvider, outputProvider)
                }
                // once the config action has run, the outputProvider should have been used
                // to declare the output files, it's time to use this information to
                // append or replace the current BuildableArtifact with it.
                outputProvider.commit()
            } catch (e: Exception) {
                dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    EvalIssueException(
                        """Exception thrown while configuring task '$taskName'.
                            |Type: ${e.javaClass.name}
                            |Message: ${e.message}""".trimMargin(),
                        null,
                        e
                    )
                )
            }
        }
        return task
    }

    /**
     * Convert function that is used in the simple transform case to function that is used in the
     * generic case.
     */
    private inline fun <T>convertFunction(
            crossinline function : T.(input: BuildableArtifact, output: File) -> Unit) :
            T.(InputArtifactProvider, OutputFileProvider) -> Unit {
        return { input, output -> function(this, input.artifact, output.file) }
    }

    @JvmName("simpleCreate")
    fun create(function : T.(BuildableArtifact, File) -> Unit) {
        create(convertFunction(function))
    }
}