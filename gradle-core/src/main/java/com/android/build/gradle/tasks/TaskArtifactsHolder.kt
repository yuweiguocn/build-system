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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.tasks.VariantAwareTask
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.jvm.isAccessible

class TaskArtifactsHolder<T>(val artifacts: BuildArtifactsHolder) where T: Task, T: VariantAwareTask {

    private val inputs = mutableListOf<InputFilesInjectPoint<T>>()
    private val outputs = mutableListOf<OutputDirectoryInjectPoint<T>>()

    /**
     * Wires all input and output fields of a task using BuildableArtifacts.
     *
     * All input or output fields must be annotated with an identification annotation. Example of
     * such annotation can be found at [InternalID]. An annotation identification must be annotated
     * with [IDProvider].
     *
     * All input fields will be injected with either the current or the final value of the buildable
     * artifact identified by the [IDProvider] annotated annotation.
     *
     * Automatically allocate Providers of [RegularFile] or [Directory] for output file and folders.
     *
     * Provide automatic checks like requesting a File buildable artifacts to be consumed or
     * produced as a directory
     *
     * @param configAction the configuration object pointing the task with inputs and outputs
     * definitions.
     *
     */
    fun allocateArtifacts(configAction: AnnotationProcessingTaskCreationAction<T>) {

        val injectionPoints: TaskInjectionPointsCache.InjectionPoints =
            TaskInjectionPointsCache.getInjectionPoints(configAction.type)

        injectionPoints.inputs.map {
            val ba =
                if (it.isFinalVersion) {
                    if (it.isOptional) {
                        // TODO: FIX in a separate CL.
                        // this is technically not correct, it assumes the provider of this artifact
                        // has already been configured. we should check this condition at execution time
                        artifacts.getFinalArtifactFilesIfPresent(it.id)
                    } else {
                        artifacts.getFinalArtifactFiles(it.id)
                    }
                } else {
                    artifacts.getArtifactFiles(it.id)
                }
            InputFilesInjectPoint<T>(it, ba)
        }.toCollection(inputs)

        injectionPoints.outputs.map {
            OutputDirectoryInjectPoint<T>(it.injectionPoint, artifacts.createDirectory(
                it.id,
                configAction.name,
                it.out
            ))
        }.toCollection(outputs)
    }

    /**
     * Once the [Task] has been created, transfer all the BuildableArtifacts into the task's
     * input fields and the Provider<> into the task's output fields.
     */
    fun transfer(task: T) {
        inputs.forEach { it.inject(task) }
        outputs.forEach { it.inject(task) }
    }

    private interface InjectionPoint<in T: Task> {
        fun inject(task: T)
    }

    private data class InputFilesInjectPoint<in T: Task>(
        val injectionPoint: TaskInjectionPointsCache.InputInjectionPoint,
        // change this type to Provider<FileSystemLocation>?
        val buildableArtifact: BuildableArtifact?): InjectionPoint<T> {

        override fun inject(task: T) {
            injectionPoint.injectionPoint.setter.isAccessible = true
            if (!injectionPoint.isOptional) {
                // check that buildableArtifact is actually provided.
                requireNotNull(buildableArtifact)
            }
            injectionPoint.injectionPoint.setter.call(task, buildableArtifact)        }
    }

    private data class OutputDirectoryInjectPoint<in T: Task>(
        val injectionPoint: KMutableProperty1<Any, Provider<FileSystemLocation>?>,
        val output: Provider<out FileSystemLocation>): InjectionPoint<T> {

        override fun inject(task: T) {
            injectionPoint.setter.isAccessible = true
            injectionPoint.setter.call(task, output)
        }
    }
}