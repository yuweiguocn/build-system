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
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode
import com.android.tools.build.bundletool.model.Aapt2Command
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ForkJoinPool
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Task that generates the standalone from a bundle.
 */
open class BundleToStandaloneApkTask @Inject constructor(workerExecutor: WorkerExecutor) : AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var bundle: BuildableArtifact
        private set

    @get:InputFiles
    @get:org.gradle.api.tasks.Optional
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    private lateinit var outputFile: Provider<RegularFile>

    @get:OutputDirectory
    val outputDirectory: File
       get() = outputFile.get().asFile.parentFile!!

    @get:Input
    val fileName: String
        get() = outputFile.get().asFile.name

    private lateinit var tempDirectory: File

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    lateinit var signingConfig: FileCollection
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
                    outputFile.get().asFile,
                    tempDirectory,
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
        val temporaryDir: File,
        val keystoreFile: File?,
        val keystorePassword: String?,
        val keyAlias: String?,
        val keyPassword: String?
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.outputFile.parentFile)
            FileUtils.cleanOutputDir(params.temporaryDir)

            val outputApksBundle =
                params.temporaryDir.toPath().resolve("universal_bundle.apks")

            generateUniversalApkBundle(outputApksBundle)
            extractUniversalApk(outputApksBundle)
        }

        private fun generateUniversalApkBundle(outputApksBundle: Path) {
            val command = BuildApksCommand
                .builder()
                .setExecutorService(
                    MoreExecutors.listeningDecorator(
                        ForkJoinPool.commonPool()))
                .setBundlePath(params.bundleFile.toPath())
                .setOutputFile(outputApksBundle)
                .setAapt2Command(Aapt2Command.createFromExecutablePath(params.aapt2File.toPath()))
                .setSigningConfiguration(
                    keystoreFile = params.keystoreFile,
                    keystorePassword = params.keystorePassword,
                    keyAlias = params.keyAlias,
                    keyPassword = params.keyPassword
                )

            command.setApkBuildMode(ApkBuildMode.UNIVERSAL)

            command.build().execute()
        }

        private fun extractUniversalApk(outputApksBundle: Path) {
            ZipInputStream(Files.newInputStream(outputApksBundle).buffered()).use { zipInputStream ->
                var found = false
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    if (entry.name.endsWith(".apk")) {
                        if (found) {
                            throw IOException("Expected bundle to contain the single universal apk, but contained multiple: $outputApksBundle")
                        }
                        Files.copy(
                            zipInputStream,
                            params.outputFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        found = true
                    }
                }
                if (!found) {
                    throw IOException("Expected bundle to contain the single universal apk, but contained none: $outputApksBundle")
                }
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<BundleToStandaloneApkTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("package", "UniversalApk")
        override val type: Class<BundleToStandaloneApkTask>
            get() = BundleToStandaloneApkTask::class.java

        private lateinit var outputFile: Provider<RegularFile>

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            // Mirrors logic in OutputFactory.getOutputFileName, but without splits.
            val suffix = if (variantScope.variantConfiguration.isSigningReady) SdkConstants.DOT_ANDROID_PACKAGE else "-unsigned.apk"
            outputFile = variantScope.artifacts.createArtifactFile(
                InternalArtifactType.UNIVERSAL_APK,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskName,
                "${variantScope.globalScope.projectBaseName}-${variantScope.variantConfiguration.baseName}-universal$suffix"
            )
        }

        override fun configure(task: BundleToStandaloneApkTask) {
            super.configure(task)

            task.outputFile = outputFile
            task.bundle = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.INTERMEDIARY_BUNDLE)
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
            task.tempDirectory = variantScope.getIncrementalDir(name)
            task.signingConfig = variantScope.signingConfigFileCollection
        }
    }
}
