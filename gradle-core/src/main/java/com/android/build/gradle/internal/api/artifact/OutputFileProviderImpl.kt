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

import com.android.build.api.artifact.ArtifactConfigurationException
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.google.common.base.Joiner
import com.google.common.collect.HashMultimap
import com.google.common.collect.Iterables
import com.google.common.collect.Multimap
import java.io.File
import org.gradle.api.Task

/**
 * Implementation for [OutputFileProvider]
 *
 * @param artifactsHolder the [BuildArtifactsHolder] for the variant
 * @param replacedArtifacts artifacts which the output of the task will replace
 * @param appendedArtifacts artifacts which the output of the task append to
 * @param task the task owning this output file provider.
 */
class OutputFileProviderImpl(
        private val artifactsHolder: BuildArtifactsHolder,
        private val replacedArtifacts: Collection<ArtifactType>,
        private val appendedArtifacts: Collection<ArtifactType>,
        private val task : Task) : OutputFileProvider {

    private val filesMap : Multimap<ArtifactType, File> = HashMultimap.create()

    override fun getFile(filename: String, vararg artifactTypes: ArtifactType): File {
        val artifactsTypesForFile = getArtifactTypesForFile(artifactTypes.asList())
        // check that artifactTypes are part of our replaced or appended artifacts.
        artifactsTypesForFile.forEach {
            if (!replacedArtifacts.contains(it) && !appendedArtifacts.contains(it)) {
                throw ArtifactConfigurationException("$it is not configured to be appended or " +
                        "replaced by this task, declare intent with append() or replace() APIs")
            }
        }

        val newFile = if (artifactsTypesForFile.size == 1) {
            artifactsHolder.createFile(task.name, Iterables.getOnlyElement(artifactsTypesForFile), filename)
        } else {
            artifactsHolder.createFile(task, filename)
        }

        // associate new File with all relevant artifacts types it applies to.
        artifactsTypesForFile.forEach {
            filesMap.put(it, newFile)
        }
        return newFile
    }

    override val file: File
        get() {
            val artifactTypes = getArtifactTypesForFile(listOf())
            if (artifactTypes.size != 1) {
                throw ArtifactConfigurationException(
                    """file() API cannot be used when task is configured to replace or append more than one type.\n
                       This task is configured to output : """ + Joiner.on(",").join(artifactTypes))
            }
            return getFile(artifactsHolder.getArtifactFilename(artifactTypes.elementAt(0)))
        }


    fun commit() {
        replacedArtifacts.forEach { artifactType ->
            artifactsHolder.createBuildableArtifact(
                artifactType,
                BuildArtifactsHolder.OperationType.TRANSFORM,
                filesMap.get(artifactType),
                task.name)
        }

        appendedArtifacts.forEach { artifactType ->
            artifactsHolder.createBuildableArtifact(
                artifactType,
                BuildArtifactsHolder.OperationType.APPEND,
                filesMap.get(artifactType),
                task.name)
        }
    }

    private fun getArtifactTypesForFile(artifactTypes: List<ArtifactType>): Collection<ArtifactType>{
        val calculatedArtifactTypes = if (artifactTypes.isEmpty()) {
            replacedArtifacts.union(appendedArtifacts)
        } else {
            artifactTypes
        }
        if (calculatedArtifactTypes.isEmpty()) {
            throw ArtifactConfigurationException("""Task cannot be configured to output nothing,
                |please use append() or replace() to declare output""".trimMargin())
        }
        return calculatedArtifactTypes
    }
}
