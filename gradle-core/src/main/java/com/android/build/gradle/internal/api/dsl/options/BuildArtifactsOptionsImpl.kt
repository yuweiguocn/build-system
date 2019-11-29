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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.dsl.options.BuildArtifactsOptions
import com.android.build.gradle.internal.api.artifact.BuildArtifactTransformBuilderImpl
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.NestedSealable
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

/**
 * Implementation of [BuildArtifactsOptions]
 */
class BuildArtifactsOptionsImpl(
        private val project: Project,
        private val artifactsHolder: BuildArtifactsHolder,
        private val artifactsActionsExecutor: DelayedActionsExecutor,
        dslScope: DslScope)
    : BuildArtifactsOptions, NestedSealable(dslScope) {

    override fun <T : Task> appendTo(artifactType: ArtifactType,
            taskName: String,
            taskType: Class<T>,
            configurationAction: BuildArtifactTransformBuilder.ConfigurationAction<T>) {
        appendTo(artifactType, taskName, taskType, configurationAction, null)
    }

    override fun <T : Task>appendTo(
            artifactType: ArtifactType,
            taskName : String,
            taskType : Class<T>,
            configurationAction : T.(BuildableArtifact, File) -> Unit) {
        appendTo(artifactType, taskName, taskType, null, configurationAction)
    }

    private fun <T : Task> appendTo(artifactType: ArtifactType,
            taskName : String,
            taskType : Class<T>,
            action : BuildArtifactTransformBuilder.ConfigurationAction<T>?,
            function : (T.(BuildableArtifact, File) -> Unit)?) {
        val builder =
                BuildArtifactTransformBuilderImpl(
                        project,
                        artifactsHolder,
                        artifactsActionsExecutor,
                        taskName,
                        taskType,
                        dslScope)
                        .append(artifactType)
        when {
            action != null -> builder.create(action)
            function != null -> builder.create(function)
            else -> throw RuntimeException("unreachable")
        }
    }

    override fun <T : Task> replace(
            artifactType: ArtifactType,
            taskName: String,
            taskType: Class<T>,
            configurationAction: BuildArtifactTransformBuilder.ConfigurationAction<T>) {
        replace(artifactType, taskName, taskType, configurationAction, null)
    }

    override fun <T : Task>replace(
            artifactType: ArtifactType,
            taskName : String,
            taskType : Class<T>,
            configurationAction : T.(BuildableArtifact, File) -> Unit) {
        replace(artifactType, taskName, taskType, null, configurationAction)
    }

    fun <T : Task> replace(artifactType: ArtifactType,
            taskName: String,
            taskType: Class<T>,
            action: BuildArtifactTransformBuilder.ConfigurationAction<T>?,
            function : (T.(BuildableArtifact, File) -> Unit)?) {
        val builder =
                BuildArtifactTransformBuilderImpl(
                        project,
                        artifactsHolder,
                        artifactsActionsExecutor,
                        taskName,
                        taskType,
                        dslScope)
                        .replace(artifactType)
        when {
            action != null -> builder.create(action)
            function != null -> builder.create(function)
            else -> throw RuntimeException("unreachable")
        }
    }

    override fun <T : Task> builder(taskName: String, taskType: Class<T>)
            : BuildArtifactTransformBuilder<T> =
            handleSealableSubItem(
                    BuildArtifactTransformBuilderImpl(
                            project,
                            artifactsHolder,
                            artifactsActionsExecutor,
                            taskName,
                            taskType,
                            dslScope))
}