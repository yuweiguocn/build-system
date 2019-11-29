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

package com.android.build.api.dsl.options

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildableArtifact
import org.gradle.api.Incubating
import org.gradle.api.Task
import java.io.File

/**
 * Options to create task for a variant.
 * See [BuildArtifactTransformBuilder] for constrained on the configuration actions for those tasks.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface BuildArtifactsOptions {
    /**
     * Create a Task for the specified artifact type and create additional outputs for that type.
     */
    fun <T : Task>appendTo(
            artifactType: ArtifactType,
            taskName : String,
            taskType : Class<T>,
            configurationAction : BuildArtifactTransformBuilder.ConfigurationAction<T>)

    fun <T : Task>appendTo(
            artifactType: ArtifactType,
            taskName : String,
            taskType : Class<T>,
            configurationAction : T.(BuildableArtifact, File) -> Unit)

    /**
     * Create a Task for the specified artifact type and transform the output.
     */
    fun <T : Task>replace(
            artifactType: ArtifactType,
            taskName : String,
            taskType : Class<T>,
            configurationAction : BuildArtifactTransformBuilder.ConfigurationAction<T>)

    fun <T : Task>replace(
            artifactType: ArtifactType,
            taskName : String,
            taskType : Class<T>,
            configurationAction : T.(BuildableArtifact, File) -> Unit)

    /**
     * Create a [BuildArtifactTransformBuilder] for creating tasks to modifies build artifacts.
     */
    fun <T : Task>builder(taskName : String, taskType : Class<T>) : BuildArtifactTransformBuilder<T>
}