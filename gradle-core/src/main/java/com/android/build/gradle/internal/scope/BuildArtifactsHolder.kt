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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.api.artifact.toArtifactType
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

typealias Report = Map<ArtifactType, List<BuildArtifactsHolder.BuildableArtifactData>>

/**
 * Buildable artifact holder.
 *
 * This class manages buildable artifacts, allowing users to transform [ArtifactType].
 *
 * [BuildArtifactTransformBuilder] can then use these [BuildableArtifact] to
 * allow users to create transform task.
 *
 * @param project the Gradle [Project]
 * @param rootOutputDir a supplier for the intermediate directories to place output files.
 * @parma dslScope the scope for dsl parsing issue raising.
 */
abstract class BuildArtifactsHolder(
    private val project: Project,
    private val rootOutputDir: () -> File,
    private val dslScope: DslScope) {

    // delete those 2 maps once use of BuildableArtifact has been eradicated.
    private val artifactRecordMap = ConcurrentHashMap<ArtifactType, ArtifactRecord>()
    private val finalArtifactsMap = ConcurrentHashMap<ArtifactType, BuildableArtifact>()

    private val producersMap = ProducersMap(project.layout.buildDirectory, this::getIdentifier)

    /**
     * Types of operation on [BuildableArtifact] as indicated by tasks producing the artifact.
     */
    enum class OperationType {
        /**
         * Initial producer of the artifact
         */
        INITIAL,
        /**
         * Producer appends more files/directories to artifact
         */
        APPEND,
        /**
         * Producer transforms (replace) existing artifact files/directories with new ones.
         */
        TRANSFORM
    }

    // ArtifactRecord and BuildableProducer structures are deprecated and will be removed once
    // usage of BuildableArtifacts had been removed from Tasks implementation.

    /**
     * Internal private class for storing [BuildableArtifact] created for a [ArtifactType]
     */
    @Deprecated("use Producers instead")
    private class ArtifactRecord {

        // A list of all BuildableFiles for the artifact.  Technically, only the last
        // BuildableFiles are needed.  Storing the all BuildableFiles allows better error
        // messages to be generated in the future.
        val history: MutableList<BuildableArtifact> = mutableListOf()

        // last registered task that produced a [BuildableArtifact] for this artifact type record.
        var lastProducer: BuildableProducer? = null

        /** The latest [BuildableArtifact] created for this artifact */
        var last : BuildableArtifact
            get() = history.last()
            set(value) {
                add(value, null)
            }

        // adds a new [BuildableArtifact] with the producer information
        fun add(buildableArtifact: BuildableArtifact, producer: BuildableProducer?) {
            history.add(buildableArtifact)
            this.lastProducer = producer
        }

        val size : Int
            get() = history.size
    }

    // unless a [BuildableArtifact] or [FileCollection] was used to initialize a new instance of
    // [BuildableArtifact], the task as well as the Provider<> producing the file or folder
    // associated with the new BuildableArtifact will be recorded here.
    private data class BuildableProducer(
        val fileOrDirProperty: Property<in FileSystemLocation>?,
        val name: String,
        val fileName: String)

    private class FinalBuildableArtifact(
        val artifactType: ArtifactType,
        val artifacts: BuildArtifactsHolder) : BuildableArtifact {
        override val files: Set<File>
            get() = final().files
        override fun isEmpty(): Boolean = final().isEmpty()
        override fun iterator(): Iterator<File> = final().iterator()
        override fun getBuildDependencies(): TaskDependency = final().buildDependencies
        override fun get(): FileCollection = final().get()
        private fun final() : BuildableArtifact = artifacts.getArtifactRecord(artifactType).last
        override fun toString(): String = "FinalBuildableArtifact($artifactType, $artifacts, $files)"
    }

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param from the original [ArtifactType] for the built artifacts.
     * @param to the supplemental [ArtifactType] the same built artifacts will also be published
     * under.
     */
    fun republish(from: ArtifactType, to: ArtifactType) {
        producersMap.republish(from, to)
    }

    /**
     * Registers a new producer for a particular [ArtifactType]. The producer is identified
     * by a [TaskProvider] to avoid configuring the task until the produced [FileSystemLocation]
     * is required by another [Task].
     *
     * The produced files are provided as a [Provider] of a [Provider] of either a [Directory] or
     * [RegularFile]. The actual instance passed should be a [Provider] of either a
     * [DirectoryProperty] or a [RegularFileProperty] so we can set the expected location of the
     * built artifact when the produced files are required by another task.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val outputFile = objectFactory.newFileProperty()
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java)
     *
     *     scope.artifacts.registerProducer(InternalArtifactType.SOME_ID,
     *            OperationType.INITIAL,
     *            myTaskProvider,
     *            myTaskProvider.map { it -> it.outputFile }
     *            "some_file_name")
     *
     * </pre>
     *
     * Consumers should use [getFinalProduct] or [getFinalProducts] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param operationType the expected type of production, there can be only one
     * [OperationType.INITIAL] but many [OperationType.APPEND] or [OperationType.TRANSFORM]
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param product the [Provider] of the artifact file (either a [RegularFile] or [Directory]
     * @param fileName the produced file name.
     */
    fun registerProducer(artifactType: ArtifactType,
        operationType: OperationType,
        taskProvider: TaskProvider<*>,
        product: Provider<Provider<out FileSystemLocation>>,
        fileName: String) {

        val producers = producersMap.getProducers(artifactType)
        taskProvider.configure {
            // add a new configuration action to make sure the producers are configured even
            // if no one injects the result. The task is being configured so it will be executed
            // and output folders must be set correctly.
            // this can happen when users request an intermediary task execution (instead of
            // assemble for instance).
            producers.resolveAllAndReturnLast()
        }
        when(operationType) {
            OperationType.INITIAL -> {
                if (!producers.isEmpty()) {
                    val plural = producers.hasMultipleProducers()
                    throw RuntimeException(
                        """|Task ${taskProvider.name} is expecting to be the initial producer of
                                |$artifactType, but the following ${if (plural) "tasks" else "task"} : ${Joiner.on(',').join(producers.map { it.taskName})}
                                |${if (plural) "are" else "is"} already registered as ${if (plural) "producers" else "producer"}"""
                            .trimMargin()
                    )
                }
            }
            OperationType.APPEND -> {
            }
            OperationType.TRANSFORM -> {
                producers.clear()
            }
        }
        producers.add(product, taskProvider.name, fileName)
    }

    /**
     * Returns the current produced version of an [ArtifactType]
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun getCurrentProduct(artifactType: ArtifactType) =
        producersMap.getProducers(artifactType).getCurrent()

    /**
     * Returns true if there is at least one producer for the passed [ArtifactType]
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun hasFinalProduct(artifactType: ArtifactType) = producersMap.hasProducers(artifactType)

    /**
     * Returns a [Provider] of either a [Directory] or a [RegularFile] depending on the passed
     * [ArtifactType.Kind]. The [Provider] will represent the final value of the built artifact
     * irrespective of when this call is made.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error and [getFinalProducts] should be used instead.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val inputFile: Provider<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          it.inputFile = scope.artifacts.getFinalProduct(InternalArtifactTYpe.SOME_ID)
     *     }
     * </pre>
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation> getFinalProduct(artifactType: ArtifactType): Provider<T> {
        val producers = producersMap.getProducers(artifactType)
        if (producers.hasMultipleProducers()) {
            throw java.lang.RuntimeException(
                """A single producer of $artifactType was requested, but the following tasks
                    |produce it: ${Joiner.on(',').join(
                    producers.map { it.taskName})}""".trimMargin())
        }
        return producers.injectable as Provider<T>
    }

    /**
     * See [getFinalProducts] API.
     *
     * On top of returning the [Provider] of [Directory] or [RegularFile], also returns a
     * [Provider] of [String] which represents the task name of the final producer task for the
     * passed artifact type.
     *
     * @param artifactType the identifier for the built artifact.
     * @return a [Pair] of [Provider] for the [FileSystemLocation] and task name producing it.
     */
    fun <T: FileSystemLocation> getFinalProductWithTaskName(artifactType: ArtifactType): Pair<Provider<String>, Provider<T>> {
        return Pair(producersMap.getProducers(artifactType).lastProducerTaskName,
            getFinalProduct(artifactType))
    }

    /**
     * Returns a [List] of [Provider]s of either a [Directory] or a [RegularFile] depending on the
     * passed [ArtifactType.Kind]. This possibly empty list of [Provider] will contain the final
     * values of the built artifacts irrespective of when this call is made.
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun getFinalProducts(artifactType: ArtifactType): List<Provider<out FileSystemLocation>> {
        val producers = producersMap.getProducers(artifactType)
        return producers.map { producer ->
            project.layout.buildDirectory.flatMap {
                producers.resolve(producer)
            }
        }
    }

    /**
     * Returns an appropriate task name for the variant with the given prefix.
     */
    fun getTaskName(prefix : String) : String {
        return prefix + getIdentifier().capitalize()
    }

    /**
     * Returns a identifier that will uniquely identify this artifacts holder against other holders.
     * This can be used to create unique folder or provide unique task name for this context.
     *
     * @return a unique [String]
     */
    abstract fun getIdentifier(): String

    // MOST OF THE APIs below should be removed once BuildableArtifact are not directly used by
    // tasks any longer.

    /**
     * Returns the current [BuildableArtifact] associated with the artifactType.
     * When a chain of tasks are registered to append or replace the artifact, this API will return
     * the current (possibly intermediary) version of the BuildableArtifact at the time of this
     * call.
     *
     * @param artifactType the requested artifact type.
     * @return the possibly empty [BuildableArtifact] for this artifact type.
     */
    fun getArtifactFiles(artifactType: ArtifactType): BuildableArtifact {
        return getArtifactRecord(artifactType).last
    }

    /**
     * Returns the final [BuildableArtifact] associated with the artifactType.
     * Irrespective of the timing of this method call, it will always return the final version of
     * the [BuildableArtifact] for the passed artifact type.
     *
     * This should not be used to transform further the artifact type.
     *
     * @param artifactType the requested [BuildableArtifact] artifact type.
     * @return the possibly empty final [BuildableArtifact] for this artifact type.
     */
    fun getFinalArtifactFiles(artifactType: ArtifactType) : BuildableArtifact {
        val artifact = artifactRecordMap.get(artifactType)
        artifact?.lastProducer?.let {
            if (it.fileOrDirProperty == null) {
                Logger.getLogger(javaClass.name).log(Level.WARNING,
                    "Wrong API use for artifact type $artifactType")
            }
        }
        return finalArtifactsMap.computeIfAbsent(artifactType) {
            FinalBuildableArtifact(artifactType, this)
        }
    }

    /**
     * Returns the final [BuildableArtifact] associated with the artifactType or an empty
     * BuildableArtifact if no [BuildableArtifact] has been registered for this artifact type.
     *
     * Irrespective of the timing of this method call, it will always return the final version of
     * the [BuildableArtifact] for the passed artifact type or an empty one.
     *
     * This should not be used to transform further the artifact type.
     *
     * @param artifactType the requested [BuildableArtifact] artifact type.
     * @return the possibly empty final [BuildableArtifact] for this artifact type.
     */

    fun getOptionalFinalArtifactFiles(artifactType: ArtifactType): BuildableArtifact {
        return if (hasArtifact(artifactType)) {
            getFinalArtifactFiles(artifactType)
        } else {
            BuildableArtifactImpl(project.files())
        }
    }

    /**
     * Returns the final [BuildableArtifact] associated with the `artifactType` or `null` if no
     * [BuildableArtifact] has been registered for this artifact type.
     *
     * See [getFinalArtifactFiles] for more details.
     */
    fun getFinalArtifactFilesIfPresent(artifactType: ArtifactType): BuildableArtifact? {
        return if (hasArtifact(artifactType)) {
            getFinalArtifactFiles(artifactType)
        } else {
            null
        }
    }

    /**
     * Returns whether the artifactType exists in the holder.
     */
    fun hasArtifact(artifactType: ArtifactType) : Boolean {
        return artifactRecordMap.containsKey(artifactType)
    }

    /**
     * Replaces the output of the specified artifactType.
     *
     * This method allows files associated with the artifactType to be replaced such that subsequent
     * call to [getArtifactFiles] will return the newly created files.
     * The path of File are created from the supplied filenames and the name of the Task that will
     * generate these files.
     *
     * @param artifactType artifactType to be replaced.
     * @param newFiles names of the new files.
     * @param taskName [Task] name that will create the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    @JvmOverloads
    fun createBuildableArtifact(
            artifactType: ArtifactType,
            operationType: OperationType,
            newFiles : Any,
            taskName : String? = null) {
        val collection = createFileCollection(artifactType, operationType, newFiles, taskName)
        val files = BuildableArtifactImpl(collection)
        createOutput(artifactType, files)
    }

    /**
     * Append output to the specified artifactType. The [newFiles] will be added after any
     * existing content.
     *
     * After invoking this method, [getArtifactFiles] will return a [BuildableArtifact] that
     * contains both the new files and the original files.
     * The path of File are created from the supplied filenames and the name of the Task that will
     * generate these files.
     *
     * @param artifactType [ArtifactType] the new files will be classified under.
     * @param newFiles names of the new files.
     * @param taskName the name of the task responsible for producing or updating the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    @Deprecated("Use createBuildableArtifact/createDirectory/createArtifactFile APIs")
    fun appendArtifact(
        artifactType: ArtifactType,
        newFiles : Collection<Any>,
        taskName: String) : BuildableArtifact {
        return doAppendArtifact(artifactType,
            createFileCollection(
                artifactType,
                OperationType.APPEND,
                newFiles,
                taskName))
    }

    /**
     * Append existing files to a specified artifact type. The [existingFiles] will be added after
     * any existing content.
     *
     * This should only be called when files already exists during configuration time (which usually
     * is the case with source directory files) or when dependency information is embedded inside
     * the file collection.
     *
     * @param artifactType [ArtifactType] for the existing files
     * @param existingFiles existing files' [FileCollection] with
     */
    @Deprecated("Use createBuildableArtifact/createDirectory/createArtifactFile APIs")
    fun appendArtifact(artifactType: ArtifactType, existingFiles: FileCollection) {
        doAppendArtifact(artifactType,
            createFileCollection(
                artifactType,
                OperationType.APPEND,
                existingFiles))
    }

    /**
     * Append a new file or folder to a specified artifact type. The new content will be added
     * after any existing content.
     *
     * @param artifactType [ArtifactType] the new file or folder will be classified under.
     * @param task [Task] producing the file or folder.
     * @param fileName expected file name for the file (location is determined by the build)
     * @return [File] handle to use to create the file or folder (potentially with subfolders
     * or multiple files)
     */
    @Deprecated("Use createBuildableArtifact/createDirectory/createArtifactFile APIs")
    fun appendArtifact(
        artifactType: ArtifactType,
        task : Task,
        fileName: String = "out") : File {
        val output = createFile(task.name, artifactType, fileName)
        doAppendArtifact(artifactType,
            createFileCollection(
                artifactType,
                OperationType.APPEND,
                listOf(output),
                task.name))
        return output
    }

    /**
     * Append a new file or folder to a specified artifact type. The new content will be added
     * after any existing content.
     *
     * @param artifactType [ArtifactType] the new file or folder will be classified under.
     * @param task [Task] producing the file or folder.
     * @param fileName expected file name for the file (location is determined by the build)
     * @return [File] handle to use to create the file or folder (potentially with subfolders
     * or multiple files)
     */
    @Deprecated("Use createBuildableArtifact/createDirectory/createArtifactFile APIs")
    fun appendArtifact(
        artifactType: ArtifactType,
        taskName: String,
        fileName: String = "out") : File {
        val output = createFile(taskName, artifactType, fileName)
        appendArtifact(artifactType, listOf(output), taskName)
        return output
    }

    /**
     * Create a [Provider] of [RegularFile] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param fileName fileName for the file.
     */
    fun createArtifactFile(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        fileName: String) : Provider<RegularFile> = createArtifactFile(
            artifactType,
            operationType,
            taskName,
            File(
                FileUtils.join(
                    artifactType.getOutputPath(),
                    artifactType.name().toLowerCase(),
                    getIdentifier(),
                    fileName
                )
            )
        )

    /**
     * Create a [Provider] of [RegularFile] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param file file location to use, relative to the project build output.
     */
    fun createArtifactFile(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        requestedFileLocation: File) : Provider<RegularFile> {

        if (artifactType.kind() != ArtifactType.Kind.FILE) {
            throw RuntimeException(
                "appendArtifactFile called with $artifactType which is an ArtifactType" +
                        " with kind set to DIRECTORY."
            )
        }

        return createFileOrDirectory(artifactType,
            operationType,
            taskName,
            requestedFileLocation) {
            createFileProperty<RegularFileProperty>(artifactType, it)
        }
    }

    /**
     * Create a [Provider] of [Directory] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param taskName name of the producer task.
     * @param fileName name of the directory or "out" by default.
     */
    fun createDirectory(artifactType: ArtifactType,
        taskName: String,
        fileName: String = "out"): Provider<Directory> =

        createDirectory(artifactType,
            OperationType.APPEND,
            taskName,
            File(FileUtils.join(
                artifactType.getOutputPath(),
                artifactType.name().toLowerCase(),
                getIdentifier(),
                fileName)))
    /**
     * Create a [Provider] of [Directory] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param fileName name of the directory or "out" by default.
     */
    fun createDirectory(artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        fileName: String = "out"): Provider<Directory> =

        createDirectory(artifactType, operationType, taskName, File(FileUtils.join(
            artifactType.getOutputPath(),
            artifactType.name().toLowerCase(),
            getIdentifier(),
            fileName)))

    /**
     * Create a [Provider] of [Directory] that can be used as a task output.
     *
     * @param artifactType the intended artifact type stored in the directory.
     * @param operationType type of output (appending, replacing or initial version)
     * @param taskName name of the producer task.
     * @param requestFileLocation location of the output file, relative the project build directory.
     * @param requestFileLocation location of the output file, relative the project build directory.
     */
    fun createDirectory(artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        requestFileLocation: File): Provider<Directory> {

        if (artifactType.kind() != ArtifactType.Kind.DIRECTORY) {
            throw RuntimeException("createDirectory called with $artifactType which is an " +
                    "ArtifactType with kind set to File.")
        }

        return createFileOrDirectory(
            artifactType,
            operationType,
            taskName,
            requestFileLocation) {
            createFileProperty<DirectoryProperty>(artifactType, it)
        }
    }

    private fun <T: FileSystemLocation> createFileOrDirectory(
        artifactType: ArtifactType,
        operationType: OperationType,
        taskName: String,
        requestedFileLocation: File,
        propertyCreator: (path: String)->Property<T> ) : Provider<T> {

        val artifactRecord = artifactRecordMap[artifactType]
        val intermediatesOutput = InternalArtifactType.Category.INTERMEDIATES.outputPath

        val output = propertyCreator(
            if (artifactRecord == null || artifactType.getOutputPath() != intermediatesOutput) {
                requestedFileLocation.path
            } else {
                FileUtils.join(
                    intermediatesOutput,
                    artifactType.name().toLowerCase(),
                    getIdentifier(),
                    taskName,
                    requestedFileLocation.name)
            })

        val fileCollection =
            createFileCollection(
                artifactType,
                operationType,
                output,
                taskName)

        doAppendArtifact(
            artifactType,
            fileCollection,
            @Suppress("UNCHECKED_CAST")
            BuildableProducer(
                output as Property<in FileSystemLocation>,
                taskName,
                requestedFileLocation.name))
        return output
    }

    private inline fun <reified T> createFileProperty(
        artifactType: ArtifactType,
        path: String): T {

        val artifactRecord = artifactRecordMap[artifactType]
        val intermediatesOutput = InternalArtifactType.Category.INTERMEDIATES.outputPath

        // reset lastProducer's output location if present.
        val lastProducer = artifactRecord?.lastProducer
        if (lastProducer != null) {
            val lastProducerNewLocation = FileUtils.join(
                intermediatesOutput,
                artifactType.name().toLowerCase(),
                getIdentifier(),
                lastProducer.name,
                lastProducer.fileName
            )

            lastProducer.fileOrDirProperty!!.set(
                when (T::class) {
                    RegularFileProperty::class -> project.layout.buildDirectory.file(
                        lastProducerNewLocation
                    )
                    DirectoryProperty::class -> project.layout.buildDirectory.dir(
                        lastProducerNewLocation
                    )
                    else -> throw RuntimeException("setLastProducer called with unsupported type ${T::class}")
                }
            )
        }

        when(T::class) {
            RegularFileProperty::class -> {
                var prop = project.objects.fileProperty()
                prop.set(project.layout.buildDirectory.file(path))
                return prop as T
            }
            DirectoryProperty::class -> {
                var prop = project.objects.directoryProperty()
                prop.set(project.layout.buildDirectory.dir(path))
                return prop as T
            }
            else -> throw RuntimeException("createFileOrDirectory called with unsupported type ${T::class}")
        }
    }

    private fun createFileCollection(
        artifactType: ArtifactType,
        operationType: OperationType,
        newFiles: Any,
        builtBy: String?= null): FileCollection {

        val artifactRecord = artifactRecordMap[artifactType]
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        val fileCollection = when(operationType) {
            OperationType.INITIAL -> {
                if (artifactRecord!= null && artifactRecord.size > 0) {
                    val lastProducer = artifactRecord.lastProducer
                    throw RuntimeException("Task $builtBy is expecting to be the initial producer of $artifactType, but "
                        + "${lastProducer?.name} already registered itself as a producer at these locations : "
                        + Joiner.on('\n').join(artifactRecord.last.files.map { it.absolutePath }))
                }
                project.files(newFiles)
            }
            OperationType.TRANSFORM -> {
                project.files(newFiles)
            }
            OperationType.APPEND -> {
                if (artifactRecord != null) {
                    project.files(artifactRecord.last, newFiles)
                } else {
                    project.files(newFiles)
                }
            }
            else -> {
                throw RuntimeException("Unhandled OperationType $operationType")
            }
        }

        if (builtBy != null) {
            fileCollection.builtBy(builtBy)
        }
        return fileCollection
    }

    private fun doAppendArtifact(type: ArtifactType,
        files: FileCollection,
        producer: BuildableProducer? = null) : BuildableArtifact {

        val newBuildableArtifact = BuildableArtifactImpl(files)
        createOutput(type, newBuildableArtifact, producer)
        return newBuildableArtifact
    }

    private fun createOutput(
        type: ArtifactType,
        artifact: BuildableArtifact,
        producer: BuildableProducer? = null) : ArtifactRecord {

        synchronized(artifactRecordMap) {
            val output = artifactRecordMap.computeIfAbsent(type) { ArtifactRecord() }
            output.add(artifact, producer)
            return output
        }
    }

    private fun getArtifactRecord(artifactType : ArtifactType) : ArtifactRecord {
        return artifactRecordMap[artifactType] ?:
        createOutput(artifactType, BuildableArtifactImpl(project.files()))
    }

    fun createReport() : Report =
            artifactRecordMap.entries.associate {artifactRecordMap
                it.key to it.value.history.map(this::newArtifact)
            }

    /**
     * Create a File for a task and artifact type.
     * @param task the task the file should be created for.
     * @param artifactType artifact type that will be associated with the file.
     * @param filename desired file name.
     */
    internal fun createFile(taskName: String, artifactType: ArtifactType, filename: String) =
            FileUtils.join(artifactType.getOutputDir(rootOutputDir()),
                getIdentifier(),
                taskName,
                filename)


    /**
     * Creates a File for a task. Prefer [createFile] when artifact type is known and unique for
     * the output file. This will stuff all the tasks directly under "multi-types" leading to
     * potentially confusing directory structure.
     *
     * @param task the task creating the output file.
     * @param filename name of the file.
     */
    internal fun createFile(task: Task, filename : String) =
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.getOutputDir(rootOutputDir()),
                MULTI_TYPES,
                task.name,
                filename)

    internal fun getArtifactFilename(artifactType: ArtifactType) : String {
        val record = getArtifactRecord(artifactType)
        return artifactType.name().toLowerCase(Locale.US) + record.size.toString()
    }

    /**
     * Return history of all [BuildableArtifact] for an [ArtifactType].
     */
    internal fun getHistory(artifactType: ArtifactType) : List<BuildableArtifact> {
        val record = getArtifactRecord(artifactType)
        return record.history
    }

    /** A data class for use with GSON. */
    data class BuildableArtifactData(
        @SerializedName("files") var files : Collection<File>,
        @SerializedName("builtBy") var builtBy : List<String>)

    /** Create [BuildableArtifactData] from [BuildableArtifact]. */
    private fun newArtifact(artifact : BuildableArtifact) =
            // getDependencies accepts null.
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        BuildableArtifactData(
                artifact.files,
                artifact.buildDependencies.getDependencies(null).map(Task::getPath))

    companion object {
        const val MULTI_TYPES = "multi-types"

        fun parseReport(file : File) : Report {
            val result = mutableMapOf<ArtifactType, List<BuildableArtifactData>>()
            val parser = JsonParser()
            FileReader(file).use { reader ->
                for ((key, value) in parser.parse(reader).asJsonObject.entrySet()) {
                    val history =
                            value.asJsonArray.map {
                                val obj = it.asJsonObject
                                BuildableArtifactData(
                                        obj.getAsJsonArray("files").map {
                                            File(it.asJsonObject.get("path").asString)
                                        },
                                        obj.getAsJsonArray("builtBy").map(
                                                JsonElement::getAsString))
                            }
                    result[key.toArtifactType()] = history
                }
            }
            return result
        }
    }
}
