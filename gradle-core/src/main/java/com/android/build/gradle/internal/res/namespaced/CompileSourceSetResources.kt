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
package com.android.build.gradle.internal.res.namespaced

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * Task to compile a single sourceset's resources in to AAPT intermediate format.
 *
 * The link step handles resource overlays.
 */
open class CompileSourceSetResources
@Inject constructor(workerExecutor: WorkerExecutor) : IncrementalTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    @get:InputFiles
    @get:SkipWhenEmpty
    lateinit var inputDirectories: BuildableArtifact
        private set
    @get:Input
    var isPngCrunching: Boolean = false
        private set
    @get:Input
    var isPseudoLocalize: Boolean = false
        private set
    @get:OutputDirectory
    lateinit var outputDirectory: File
        private set
    @get:OutputDirectory
    lateinit var partialRDirectory: File
        private set

    private val workers = Workers.getWorker(workerExecutor)

    override fun isIncremental() = true

    override fun doFullTaskAction() {
        FileUtils.cleanOutputDir(outputDirectory)
        val requests = mutableListOf<CompileResourceRequest>()
        val addedFiles = mutableMapOf<Path, Path>()
        for (inputDirectory in inputDirectories) {
            if (!inputDirectory.isDirectory) {
                continue
            }

            /** Only look at files in first level subdirectories of the input directory */
            Files.list(inputDirectory.toPath()).use { fstLevel ->
                fstLevel.forEach { subDir ->
                    if (Files.isDirectory(subDir)) {
                        Files.list(subDir).use {
                            it.forEach { resFile ->
                                if (Files.isRegularFile(resFile)) {
                                    val relativePath = inputDirectory.toPath().relativize(resFile)
                                    if (addedFiles.contains(relativePath)) {
                                        throw RuntimeException(
                                                "Duplicated resource '$relativePath' found in a " +
                                                        "source set:\n" +
                                                        "    - ${addedFiles[relativePath]}\n" +
                                                        "    - $resFile"
                                        )
                                    }
                                    requests.add(compileRequest(resFile.toFile()))
                                    addedFiles[relativePath] = resFile
                                }
                            }
                        }
                    }
                }
            }
        }

        workers.use {
            submit(requests)
        }
    }

    override fun doIncrementalTaskAction(changedInputs: MutableMap<File, FileStatus>) {
        val requests = mutableListOf<CompileResourceRequest>()
        val deletes = mutableListOf<File>()
        /** Only consider at files in first level subdirectories of the input directory */
        changedInputs.forEach { file, status ->
            if (willCompile(file) && (inputDirectories.any { it == file.parentFile.parentFile })) {
                when (status) {
                    FileStatus.NEW, FileStatus.CHANGED -> {
                        requests.add(compileRequest(file))
                    }
                    FileStatus.REMOVED -> {
                        deletes.add(file)
                    }
                }
            }
        }
        workers.use {
            if (!deletes.isEmpty()) {
                workers.submit(Aapt2CompileDeleteRunnable::class.java,
                    Aapt2CompileDeleteRunnable.Params(
                        outputDirectory = outputDirectory,
                        deletedInputs = deletes,
                        partialRDirectory = partialRDirectory
                    )
                )
            }
            submit(requests)
        }
    }

    private fun compileRequest(file: File, inputDirectoryName: String = file.parentFile.name) =
            CompileResourceRequest(
                    inputFile = file,
                    outputDirectory = outputDirectory,
                    inputDirectoryName = inputDirectoryName,
                    isPseudoLocalize = isPseudoLocalize,
                    isPngCrunching = isPngCrunching,
                    partialRFile = getPartialR(file))

    private fun getPartialR(file: File) =
        File(partialRDirectory, "${Aapt2RenamingConventions.compilationRename(file)}-R.txt")

    private fun submit(requests: List<CompileResourceRequest>) {
        if (requests.isEmpty()) {
            return
        }
        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven,
            logger = iLogger
        )
        for (request in requests) {
            workers.submit(Aapt2CompileRunnable::class.java,
                Aapt2CompileRunnable.Params(
                    aapt2ServiceKey = aapt2ServiceKey,
                    requests = listOf(request)
                )
            )
        }
    }

    // TODO: filtering using same logic as DataSet.isIgnored.
    private fun willCompile(file: File) = !file.name.startsWith(".") && !file.isDirectory

    class CreationAction(
        override val name: String,
        private val inputDirectories: BuildableArtifact,
        variantScope: VariantScope
    ) : VariantTaskCreationAction<CompileSourceSetResources>(variantScope) {

        override val type: Class<CompileSourceSetResources>
            get() = CompileSourceSetResources::class.java

        private lateinit var outputDirectory: File

        private lateinit var partialRDirectory: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outputDirectory = variantScope.artifacts
                .appendArtifact(InternalArtifactType.RES_COMPILED_FLAT_FILES, taskName)
            partialRDirectory = variantScope.artifacts
                .appendArtifact(InternalArtifactType.PARTIAL_R_FILES, taskName)
        }

        override fun configure(task: CompileSourceSetResources) {
            super.configure(task)

            task.inputDirectories = inputDirectories
            task.outputDirectory = outputDirectory
            task.partialRDirectory = partialRDirectory
            task.isPngCrunching = variantScope.isCrunchPngs
            task.isPseudoLocalize =
                    variantScope.variantData.variantConfiguration.buildType.isPseudoLocalesEnabled
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)

            task.dependsOn(variantScope.taskContainer.resourceGenTask)
        }
    }
}
