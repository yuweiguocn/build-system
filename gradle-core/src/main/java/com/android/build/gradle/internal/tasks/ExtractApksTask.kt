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

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.bundle.Devices.DeviceSpec
import com.android.tools.build.bundletool.commands.ExtractApksCommand
import com.android.utils.FileUtils
import com.google.protobuf.util.JsonFormat
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject

/**
 * Task that extract APKs from the apk zip (created with [BundleToApkTask] into a folder. a Device
 * info file indicate which APKs to extract. Only APKs for that particular device are extracted.
 */
open class ExtractApksTask @Inject constructor(workerExecutor: WorkerExecutor) : AndroidVariantTask() {

    companion object {
        fun getTaskName(scope: VariantScope) = scope.getTaskName("extractApksFor")
    }

    private val workers = Workers.getWorker(workerExecutor)

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var apkSetArchive: BuildableArtifact
        private set

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    var deviceConfig: File? = null
        private set

    @get:OutputDirectory
    lateinit var outputDir: File
        private set

    @get:Input
    var extractInstant = false
        private set

    @TaskAction
    fun generateApk() {

        workers.use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    apkSetArchive.singleFile(),
                    deviceConfig
                            ?: throw RuntimeException("Calling ExtractApk with no device config"),
                    outputDir,
                    extractInstant
                )
            )
        }
    }

    private data class Params(
        val apkSetArchive: File,
        val deviceConfig: File,
        val outputDir: File,
        val extractInstant: Boolean
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.outputDir)

            val builder: DeviceSpec.Builder = DeviceSpec.newBuilder()

            Files.newBufferedReader(params.deviceConfig.toPath(), Charsets.UTF_8).use {
                JsonFormat.parser().merge(it, builder)
            }

            val command = ExtractApksCommand
                .builder()
                .setApksArchivePath(params.apkSetArchive.toPath())
                .setDeviceSpec(builder.build())
                .setOutputDirectory(params.outputDir.toPath())
                .setInstant(params.extractInstant)

            command.build().execute()
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ExtractApksTask>(variantScope) {

        override val name: String
            get() = getTaskName(variantScope)
        override val type: Class<ExtractApksTask>
            get() = ExtractApksTask::class.java

        private lateinit var outputDir: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outputDir = variantScope.artifacts.appendArtifact(InternalArtifactType.EXTRACTED_APKS, taskName)
        }

        override fun configure(task: ExtractApksTask) {
            super.configure(task)

            task.outputDir = outputDir
            task.apkSetArchive = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.APKS_FROM_BUNDLE)

            val devicePath = variantScope.globalScope.projectOptions.get(StringOption.IDE_APK_SELECT_CONFIG)
            if (devicePath != null) {
                task.deviceConfig = File(devicePath)
            }

            task.extractInstant = variantScope.globalScope.projectOptions.get(BooleanOption.IDE_EXTRACT_INSTANT)
        }
    }
}
