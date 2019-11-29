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
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.model.Aapt2Command
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import java.util.concurrent.ForkJoinPool
import javax.inject.Inject

/**
 * Task that generates APKs from a bundle. All the APKs are bundled into a single zip file.
 */
open class BundleToApkTask @Inject constructor(workerExecutor: WorkerExecutor) : AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var bundle: BuildableArtifact
        private set

    @get:InputFiles
    @get:org.gradle.api.tasks.Optional
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    lateinit var signingConfig: FileCollection
        private set

    @get:OutputFile
    lateinit var outputFile: File
        private set

    private val workers = Workers.getWorker(workerExecutor)

    @TaskAction
    fun generateApk() {
        val config = SigningConfigMetadata.load(signingConfig)
        workers.use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    bundle.singleFile(),
                    File(aapt2FromMaven.singleFile, SdkConstants.FN_AAPT2),
                    outputFile,
                    config?.storeFile,
                    config?.storePassword,
                    config?.keyAlias,
                    config?.keyPassword
                )
            )
        }
    }

    private data class Params(
        val bundleFile: File,
        val aapt2File: File,
        val outputFile: File,
        val keystoreFile: File?,
        val keystorePassword: String?,
        val keyAlias: String?,
        val keyPassword: String?
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.deleteIfExists(params.outputFile)

            val command = BuildApksCommand
                .builder()
                .setExecutorService(MoreExecutors.listeningDecorator(ForkJoinPool.commonPool()))
                .setBundlePath(params.bundleFile.toPath())
                .setOutputFile(params.outputFile.toPath())
                .setAapt2Command(Aapt2Command.createFromExecutablePath(params.aapt2File.toPath()))
                .setSigningConfiguration(
                    keystoreFile = params.keystoreFile,
                    keystorePassword = params.keystorePassword,
                    keyAlias = params.keyAlias,
                    keyPassword = params.keyPassword
                )

            command.build().execute()
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<BundleToApkTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("makeApkFromBundleFor")
        override val type: Class<BundleToApkTask>
            get() = BundleToApkTask::class.java

        private lateinit var outputFile: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outputFile = variantScope.artifacts.appendArtifact(
                InternalArtifactType.APKS_FROM_BUNDLE,
                taskName,
                "bundle.apks"
            )
        }

        override fun configure(task: BundleToApkTask) {
            super.configure(task)

            task.outputFile = outputFile
            task.bundle = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.INTERMEDIARY_BUNDLE)
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
            task.signingConfig = variantScope.signingConfigFileCollection
        }
    }
}
