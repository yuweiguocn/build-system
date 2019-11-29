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

package com.android.build.gradle.tasks.ir

import com.android.annotations.VisibleForTesting
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.scope.BuildElementsTransformParams
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder
import com.android.build.gradle.internal.transforms.InstantRunSplitApkBuilder
import com.android.builder.internal.aapt.BlockingResourceLinker
import com.android.ide.common.process.ProcessException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.sdklib.IAndroidTarget
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Task to create the main APK resources.ap_ file. This file will only contain the merged
 * manifest that must be packaged in the main apk, all the resources are packaged in a separate
 * APK.
 *
 * This task should only run when targeting an Android platform 26 and above.
 *
 */
open class InstantRunMainApkResourcesBuilder @Inject constructor(workerExecutor: WorkerExecutor) :
    AndroidBuilderTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var resourceFiles: BuildableArtifact private set

    @get:OutputDirectory
    lateinit var outputDirectory: File private set

    @get:InputFiles
    lateinit var manifestFiles: Provider<Directory>
        private set

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var aapt2FromMaven: FileCollection? = null
        private set

    @TaskAction
    @Throws(IOException::class)
    fun doFullTaskAction() {

        // at this point, there should only be one instant-run merged manifest, but this may
        // change in the future.
        ExistingBuildElements.from(INSTANT_RUN_MERGED_MANIFESTS, manifestFiles)
            .transform(
                workers,
                SplitProcessorRunnable::class.java
            ) { _, processedResources ->
                SplitProcessorParams(
                    processedResources,
                    this
                )
            }
            .into(INSTANT_RUN_MAIN_APK_RESOURCES, outputDirectory)
    }

    private class SplitProcessorParams(
        val manifestFile: File,
        task: InstantRunMainApkResourcesBuilder
    ) : BuildElementsTransformParams() {
        val androidTarget: IAndroidTarget = task.builder.target
        val resourceFiles: Set<File> = task.resourceFiles.get().asFileTree.files

        override val output: File?
        lateinit var key: Aapt2ServiceKey

        init {
            if (doProcessSplit) {
                key = InstantRunSplitApkBuilder.getAapt2ServiceKey(
                    task.aapt2FromMaven,
                    task.builder
                )
            }

            val apkSupportDir = File(task.outputDirectory, "main_resources")
            if (!apkSupportDir.exists() && !apkSupportDir.mkdirs()) {
                task.logger.error(
                    "Cannot create apk support dir {}",
                    apkSupportDir.absoluteFile
                )
            }
            output = File(apkSupportDir, "resources_ap")
        }
    }

    private class SplitProcessorRunnable @Inject constructor(params: SplitProcessorParams) :
        BuildElementsTransformRunnable(params) {
        override fun run() {
            if (doProcessSplit) {
                try {
                    InstantRunSplitApkBuilder.getLinker((params as SplitProcessorParams).key)
                        .use { aapt ->
                            processSplit(params, aapt)
                        }
                } catch (e: InterruptedException) {
                    Thread.interrupted()
                    throw IOException("Exception while generating InstantRun main resources APK", e)
                } catch (e: ProcessException) {
                    throw IOException("Exception while generating InstantRun main resources APK", e)
                }
            }
        }

        // use default values for aaptOptions since we don't package any resources.
        private fun processSplit(
            params: SplitProcessorParams,
            aapt: BlockingResourceLinker
        ) {
            InstantRunSliceSplitApkBuilder.generateSplitApkResourcesAp(
                Logging.getLogger(InstantRunMainApkResourcesBuilder::class.java),
                aapt,
                params.manifestFile,
                params.output!!,
                com.android.builder.internal.aapt.AaptOptions(
                    ImmutableList.of(), false, ImmutableList.of()
                ),
                params.androidTarget,
                params.resourceFiles
            )
        }
    }

    class CreationAction(
        variantScope: VariantScope,
        private val taskInputType: ArtifactType
    ) :
        VariantTaskCreationAction<InstantRunMainApkResourcesBuilder>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("instantRunMainApkResources")
        override val type: Class<InstantRunMainApkResourcesBuilder>
            get() = InstantRunMainApkResourcesBuilder::class.java

        private lateinit var outputDirectory: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outputDirectory =
                    variantScope.artifacts.appendArtifact(INSTANT_RUN_MAIN_APK_RESOURCES, taskName, "out")
        }

        override fun configure(task: InstantRunMainApkResourcesBuilder) {
            super.configure(task)

            val artifacts = variantScope.artifacts
            task.resourceFiles = artifacts.getFinalArtifactFiles(taskInputType)
            task.manifestFiles = artifacts
                .getFinalProduct(INSTANT_RUN_MERGED_MANIFESTS)
            task.outputDirectory = outputDirectory
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
        }

    }

    companion object {
        /** Used to stop the worker item from actually doing the splitting in unit tests  */
        @VisibleForTesting
        var doProcessSplit = true
    }
}
