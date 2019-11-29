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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Map of all the producers registered in this context.
 *
 * @param buildDirectory the project buildDirectory [DirectoryProperty]
 * @param identifier a function to uniquely indentify this context when creating files and folders.
 */
class ProducersMap(
    val buildDirectory: DirectoryProperty,
    val identifier: ()->String) {

    private val producersMap = ConcurrentHashMap<ArtifactType, Producers>()

    /**
     * Returns true if there is at least one [Producer] registered for the passed [ArtifactType]
     *
     * @param artifactType the artifact type for looked up producers.
     */
    fun hasProducers(artifactType: ArtifactType) = producersMap.containsKey(artifactType)

    /**
     * Returns a [Producers] instance (possibly empty of any [Producer]) for a passed
     * [ArtifactType]
     *
     * @param artifactType the artifact type for looked up producers.
     * @return a [Producer] instance for that [ArtifactType]
     */
    fun getProducers(artifactType: ArtifactType)=
        producersMap.getOrPut(artifactType) {
            Producers(
                artifactType,
                identifier,
                buildDirectory
            )
        }!!

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param from the original [ArtifactType] for the built artifacts.
     * @param to the supplemental [ArtifactType] the same built artifacts will also be published
     * under.
     */
    fun republish(from: ArtifactType, to: ArtifactType) {
        producersMap[to] = getProducers(from)
    }

    /**
     * possibly empty list of all the [Task]s (and decoraction) producing this artifact type.
     */
    class Producers(
        val artifactType: ArtifactType,
        val identifier: () -> String,
        buildDirectory: DirectoryProperty) : ArrayList<Producer>() {

        val buildDir:File = buildDirectory.get().asFile

        // create a unique injectable value for this artifact type. This injectable value will be
        // used for consuming the artifact. When the provider is resolved, which mean that the
        // built artifact will be used, we must resolve all file locations which will in turn
        // configure all the tasks producing this artifact type.
        val injectable: Provider<out FileSystemLocation> =
            buildDirectory.flatMap {
                resolveAllAndReturnLast()
            }

        val lastProducerTaskName: Provider<String> =
            injectable.map { _ -> get(size - 1).taskName }

        private fun resolveAll(): List<Provider<out FileSystemLocation>> {
            return synchronized(this) {
                val multipleProducers = hasMultipleProducers()
                map {
                    it.resolve(buildDir, identifier, artifactType, multipleProducers)
                }
            }
        }

        fun resolveAllAndReturnLast(): Provider<out FileSystemLocation>? = resolveAll().lastOrNull()

        fun add(product: Provider<Provider<out FileSystemLocation>>,
            taskName: String,
            fileName: String) {
            add(Producer(product, taskName, fileName))
        }

        fun getCurrent(): Provider<out Provider<out FileSystemLocation>>? {
            val currentProduct = lastOrNull() ?: return null
            return currentProduct.outputProvider.map { _ ->
                currentProduct.resolve(buildDir, identifier, artifactType, hasMultipleProducers())
            }
        }

        fun resolve(producer: Producer) =
            producer.resolve(buildDir, identifier, artifactType, hasMultipleProducers())

        fun hasMultipleProducers() = size > 1
    }

    /**
     * A registered producer of an artifact. The artifact is produced by a Task identified by its
     * name and a requested file name.
     */
    class Producer(
        val outputProvider: Provider<out Provider<out FileSystemLocation>>,
        val taskName: String,
        val fileName: String) {
        fun resolve(buildDir: File,
            identifier: () -> String,
            artifactType: ArtifactType,
            multipleProducers: Boolean): Provider<out FileSystemLocation> {

            val resolved = outputProvider.get()
            val fileLocation = FileUtils.join(
                artifactType.getOutputDir(buildDir),
                identifier(),
                if (multipleProducers) taskName else "",
                fileName)
            when(resolved) {
                is DirectoryProperty->
                    resolved.set(fileLocation)
                is RegularFileProperty ->
                    resolved.set(fileLocation)
                else -> throw RuntimeException(
                    "Property.get() is not a correct instance type : ${resolved.javaClass.name}")
            }
            return resolved
        }
    }
}