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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.getAaptDaemon
import com.android.build.gradle.internal.res.namespaced.getAaptPoolSize
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform
import com.android.build.gradle.internal.transforms.InstantRunSplitApkBuilder
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.CloseableBlockingResourceLinker
import com.android.build.gradle.internal.scope.ApkData
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.base.Stopwatch
import com.google.common.base.Suppliers
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import javax.inject.Inject

/**
 * Task to generate resources.ap_ files for each split APK for Instant Run.
 */
open class InstantRunSplitApkResourcesBuilder
@Inject constructor(workerExecutor: WorkerExecutor): AndroidBuilderTask() {

    private val workers = Workers.getWorker(workerExecutor)

    lateinit var buildContext: InstantRunBuildContext
        private set
    lateinit var aaptOptions: AaptOptions
        private set
    lateinit var mainApkData: ApkData
        private set

    @get:Input @get:Optional
    var aapt2FromMaven: FileCollection? = null
        private set

    // there is no need to make the resources a dependency of this task as we
    // only use it to successfully compile the split manifest file. We only depends on it being
    // available when this task run.
    lateinit var resources: BuildableArtifact
        private set

    // the resources containing the main manifest, which could be the
    // same as [resources] depending if a separate APK for resources is used or not. We are only
    // interested in manifest binary changes, therefore, it is not needed as an Input.
    // Instead we use the [InstantRunVerifierStatus.MANIFEST_FILE_CHANGE] event to trigger
    // rebuilding all the split apk resources.
    lateinit var resourcesWithMainManifest: BuildableArtifact
        private set

    @get:Input
    lateinit var applicationId: String
        private set

    @Suppress("unused")
    @get:Input
    val aaptVersion: String
        get() = builder.buildToolInfo.revision.toString()

    @get:Input
    private val maxSlices = DexArchiveBuilderTransform.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES - 1

    @get:OutputDirectory
    lateinit var outputDir: File
        private set

    @TaskAction
    @Throws(IOException::class)
    fun taskAction() {

        FileUtils.cleanOutputDir(outputDir)

        // if resourcesWithMainManifest has changed, we need to rebuild all slices resources.
        // they might end up become the same as the previous version and we will save the
        // split apk packaging.
        val stopwatch = Stopwatch.createStarted()


        val suggestedNbOfProcesses = getNumberOfBuckets()

        val aapt2ServiceKey = getAaptService()

        // if we have slaves around, by all means, use them !
        val nbOfProcesses = maxOf(getAaptServicePoolsSize(aapt2ServiceKey),
            suggestedNbOfProcesses)

        for (bucket in 0..(nbOfProcesses-1)) {
            val params =
                GenerateSplitApkResource.Params(
                    aapt2ServiceKey,
                    builder.target.getPath(IAndroidTarget.ANDROID_JAR),
                    maxSlices,
                    bucket,
                    nbOfProcesses,
                    applicationId,
                    mainApkData,
                    aaptOptions,
                    resources.get().asFileTree.files,
                    outputDir
                )

            workers.submit(getWorkerItemClass(), params)
        }
        workers.close()
        logger.quiet("Time to process all the split resources " +
                "${stopwatch.elapsed()} with $nbOfProcesses slaves")
    }

    open fun getNumberOfBuckets() =
        // On Windows, spawning aapt2 takes about 8x to generate a single resource bundle so
        // it only make sense to use more than one slave process if we are going to process more
        // than 16 files. These number are somehow empirical based on observation, and could be
        // further tweaked
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
            maxOf(1, maxSlices / 8)
        // On other operating systems, just use half of the available processors or
        // slicing requirement.
        else maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), maxSlices) / 2)

    @VisibleForTesting
    protected open fun getAaptService() = registerAaptService(
        aapt2FromMaven,
        builder.buildToolInfo,
        iLogger)

    @VisibleForTesting
    protected open fun getAaptServicePoolsSize(aapt2ServiceKey: Aapt2ServiceKey) =
        getAaptPoolSize(aapt2ServiceKey)

    @VisibleForTesting
    protected open fun getWorkerItemClass(): Class<out Runnable>
            = GenerateSplitApkResource::class.java

    open class GenerateSplitApkResource @Inject constructor(
        private val params: Params
    ) : Runnable {

        companion object {
            val logger = LoggerWrapper(Logging.getLogger(this::class.java))
        }

        override fun run() {
            requestAaptDaemon(params.aapt2ServiceKey).use {
                for (sliceNumber in params.bucketNumber..params.maxSlices step params.stepSize) {
                    val processingStart = System.currentTimeMillis()
                    generateSplitApkResource(it, sliceNumber)
                    logger.info("processing $sliceNumber in bucket ${params.bucketNumber}" +
                            " took ${System.currentTimeMillis() - processingStart}")
                }
            }
        }

        protected open fun requestAaptDaemon(aapt2ServiceKey: Aapt2ServiceKey)
                : CloseableBlockingResourceLinker =
            getAaptDaemon(params.aapt2ServiceKey)

        protected open fun generateSplitApkResource(linker: CloseableBlockingResourceLinker, sliceNumber: Int) {

            val uniqueName = DexArchiveBuilderTransform.getSliceName(sliceNumber)
            val apkSupportDir = File(params.outputDirectory, uniqueName)
            FileUtils.cleanOutputDir(apkSupportDir)

            // generate the manifest file.
            val androidManifest =
                InstantRunSplitApkBuilder.generateSplitApkManifest(
                    apkSupportDir,
                    uniqueName,
                    Suppliers.ofInstance(params.applicationId),
                    params.mainApkData.versionName,
                    params.mainApkData.versionCode,
                    null
                )

            val resFilePackageFile = File(apkSupportDir, "resources_ap")
            val importedAPKs = params.resources.filter {
                    file -> file.name.endsWith(SdkConstants.EXT_RES) }

            val aaptConfig = AaptPackageConfig.Builder()
                .setAndroidJarPath(params.androidJarPath)
                .setManifestFile(androidManifest)
                .setOptions(params.aaptOptions)
                .setDebuggable(true)
                .setVariantType(VariantTypeImpl.BASE_APK)
                .setImports(importedAPKs)
                .setResourceOutputApk(resFilePackageFile)

            AndroidBuilder.processResources(linker, aaptConfig.build(),
                logger
            )
        }

        class Params(val aapt2ServiceKey: Aapt2ServiceKey,
            val androidJarPath: String,
            val maxSlices: Int,
            val bucketNumber: Int,
            val stepSize: Int,
            val applicationId : String,
            val mainApkData: ApkData,
            val aaptOptions: AaptOptions,
            val resources: Collection<File>,
            val outputDirectory: File): Serializable
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<InstantRunSplitApkResourcesBuilder>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("instantRunSplitApkResources")
        override val type: Class<InstantRunSplitApkResourcesBuilder>
            get() = InstantRunSplitApkResourcesBuilder::class.java

        private lateinit var outputDir: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outputDir = variantScope.artifacts.appendArtifact(
                InternalArtifactType.INSTANT_RUN_SPLIT_APK_RESOURCES,
                taskName)
        }

        override fun configure(task: InstantRunSplitApkResourcesBuilder) {
            super.configure(task)

            val artifacts = variantScope.artifacts
            val globalScope = variantScope.globalScope
            task.resources = artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES)
            val resourcesWithMainManifest =
                if (variantScope.instantRunBuildContext.useSeparateApkForResources())
                    InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES
                else
                    InternalArtifactType.PROCESSED_RES
            task.resourcesWithMainManifest =
                    artifacts.getFinalArtifactFiles(resourcesWithMainManifest)

            task.dependsOn(task.resources, task.resourcesWithMainManifest)

            task.outputDir = outputDir

            task.aapt2FromMaven = getAapt2FromMaven(globalScope)
            task.applicationId = variantScope.variantConfiguration.applicationId

            task.buildContext = variantScope.instantRunBuildContext
            task.aaptOptions = globalScope.extension.aaptOptions.convert()
            task.mainApkData = variantScope.outputScope.mainSplit

            // This task theoretically depends on the resources bundle as the split manifest file
            // generated can contain resource references: android:versionName="@string/version_name"
            // However, we don't want to rebuild all the split APKs when only android resources
            // change. Therefore, we do want to rebuild all the split APKs only when the main
            // manifest file changed.
            task.outputs.upToDateWhen {
                !variantScope.instantRunBuildContext.hasVerifierStatusBeenSet(
                    InstantRunVerifierStatus.MANIFEST_FILE_CHANGE)
            }
        }
    }
}