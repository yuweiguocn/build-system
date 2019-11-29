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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.Task

/**
 * Builder to create a task for a specific variant that consumes or transforms a build artifact.
 *
 * The builder allows the task's input and output to be configured.  This modifies the
 * [InputArtifactProvider], and [OutputFileProvider] that will be constructed of for the [create]
 * method.  The [create] method accept [ConfigurationAction] that will be used to configure the
 * task. This action will not run synchronously but will be invoked later when the DSL objects are
 * sealed and the Android Plugin is creating all tasks from these final values.
 *
 * For example, to create a task that uses compile classpath and javac output and create additional
 * .class files for both compile classpath and javac output:
 *
 * Simple case :
 *
 *  * buildArtifactTransformBuilder
 *     .append(BuildableArtifactType.JAVAC_CLASSES)
 *     .create() { task, input, output ->
 *         task.javaClasses = input.getArtifact(BuildableArtifactType.JAVAC_CLASSES)
 *         task.outputDir = output.getFile()
 *
 * More elaborate case :
 *
 * buildArtifactTransformBuilder
 *     .input(BuildableArtifactType.JAVA_RES)
 *     .append(BuildableArtifactType.JAVAC_CLASSES)
 *     .replace(BuildableArtifactType.JAVA_COMPILE_CLASSPATH)
 *     .create() { task, input, output ->
 *         task.javaRes = input.getArtifact(BuildableArtifactType.JAVA_RES)
 *         task.classpath = input.getArtifact(BuildableArtifactType.JAVA_COMPILE_CLASSPATH)
 *         task.javaClasses = input.getArtifact(BuildableArtifactType.JAVAC_CLASSES)
 *         task.outputDir = output.getFile("classes",
 *             BuildableArtifactType.JAVAC_CLASSES,
 *             BuildableArtifactType.JAVA_COMPILE_CLASSPATH)
 *     }
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface BuildArtifactTransformBuilder<out T : Task> {

    /**
     * Action to configure the task.
     */
    @Incubating
    interface ConfigurationAction<in T : Task> {
        fun accept(task: T, input: InputArtifactProvider, output: OutputFileProvider)
    }

    /**
     * Specifies a [BuildableArtifact] that will be available to the task as an input.
     * The input will be the final version of the files associated with the passed artifact type.
     *
     * As many tasks can be registered to transform this artifact type through addition or
     * replacement, the resulting input will contain the end product of all these transform tasks.
     *
     * The passed artifact type cannot be used in the [append] or [replace] methods since this
     * task is requesting the final version of the artifact type.
     *
     * @param artifactType the ArtifactType the task will modify.
     * @throws [ArtifactConfigurationException] if the artifact type is also registered for addition
     * or replacement through the [append] or [replace] methods.
     */
    @Throws(ArtifactConfigurationException::class)
    fun input(artifactType: ArtifactType) : BuildArtifactTransformBuilder<T>

    /**
     * Specifies the type of [BuildableArtifact] that the task will append to.
     *
     * The type of [BuildableArtifact] is automatically passed as an input to the task and will be
     * available through the [InputArtifactProvider.getArtifact] method.
     *
     * @param artifactType the ArtifactType the task will append.
     * @throws [ArtifactConfigurationException] is the artifact type is also registered through
     * the [input] or [replace] methods.
     */
    @Throws(ArtifactConfigurationException::class)
    fun append(artifactType: ArtifactType): BuildArtifactTransformBuilder<T>

    /**
     * Specifies the type of [BuildableArtifact] that the task will replace.
     *
     * The type of [BuildableArtifact] is automatically passed as an input to the task and will be
     * available through the [InputArtifactProvider.getArtifact] method.
     *
     * @param artifactType the ArtifactType the task will replace.
     * @throws [ArtifactConfigurationException] is the artifact type is also registered through
     * the [input] or [append] methods.
     */
    @Throws(ArtifactConfigurationException::class)
    fun replace(artifactType: ArtifactType): BuildArtifactTransformBuilder<T>

    /**
     * Creates the task and uses the supplied action to configure it.
     *
     * @param action action that configures the resulting Task. This action will not run when
     * this function return but will be run at a later stage when the DSL objects are sealed.
     */
    fun create(action : ConfigurationAction<T>)

    /**
     * Creates the task and uses the supplied function to configure it.
     *
     * Accepts a function instead of a functional interface.
     *
     * @param function function that configures the resulting Task. his action will not run when
     * this function return but will be run at a later stage when the DSL objects are sealed.
     */
    fun create(function : T.(InputArtifactProvider, OutputFileProvider) -> Unit)
}
