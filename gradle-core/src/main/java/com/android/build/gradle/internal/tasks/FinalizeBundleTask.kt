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
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.process.JarSigner
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.StringOption
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task that copies the bundle file (.aab) to it's final destination and do final touches like:
 * <ul>
 *     <li>Signing the bundle if credentials are available and it's not a debuggable variant.
 * </ul>
 */
open class FinalizeBundleTask @Inject constructor(workerExecutor: WorkerExecutor) :
    AndroidVariantTask() {

    private val workers = Workers.getWorker(workerExecutor)

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    lateinit var intermediaryBundleFile: BuildableArtifact
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:Optional
    var signingConfig: FileCollection? = null
        private set

    @get:OutputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    val finalBundleLocation: File
        get() = finalBundleFile.get().asFile.parentFile

    @get:Input
    val finalBundleFileName: String
        get() = finalBundleFile.get().asFile.name

    private lateinit var finalBundleFile: Provider<RegularFile>

    @TaskAction
    fun copyAndFinalizeBundle() {
        workers.use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    intermediaryBundleFile = intermediaryBundleFile.singleFile(),
                    finalBundleFile = finalBundleFile.get().asFile,
                    signingConfig = SigningConfigMetadata.getOutputFile(signingConfig)
                )
            )
        }
    }

    private data class Params(
        val intermediaryBundleFile: File,
        val finalBundleFile: File,
        val signingConfig: File?) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.finalBundleFile.parentFile)
            FileUtils.copyFile(params.intermediaryBundleFile, params.finalBundleFile)

            SigningConfigMetadata.load(params.signingConfig)?.getSignature()?.let {
                JarSigner().sign(params.finalBundleFile, it)
            }
        }

        private fun SigningConfig.getSignature(): JarSigner.Signature? {
            return storeFile?.run {
                JarSigner.Signature(storeFile!!, storePassword, keyAlias, keyPassword) }
        }
    }

    /**
     * CreateAction for a task that will sign the bundle artifact.
     */
    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<FinalizeBundleTask>(variantScope) {
        override val name: String
            get() = variantScope.getTaskName("sign", "Bundle")

        override val type: Class<FinalizeBundleTask>
            get() = FinalizeBundleTask::class.java

        private lateinit var finalBundleFile: Provider<RegularFile>

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            val bundleName = "${variantScope.globalScope.projectBaseName}.aab"
            val apkLocationOverride = variantScope.globalScope.projectOptions.get(StringOption.IDE_APK_LOCATION)

            finalBundleFile = if (apkLocationOverride == null)
                variantScope.artifacts.createArtifactFile(
                    InternalArtifactType.BUNDLE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskName,
                    bundleName)
            else
                variantScope.artifacts.createArtifactFile(
                    InternalArtifactType.BUNDLE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskName,
                    FileUtils.join(
                        variantScope.globalScope.project.file(apkLocationOverride),
                        variantScope.variantConfiguration.dirName,
                        bundleName))
        }

        override fun configure(task: FinalizeBundleTask) {
            super.configure(task)

            task.intermediaryBundleFile = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.INTERMEDIARY_BUNDLE)
            task.finalBundleFile = finalBundleFile

            // Don't sign debuggable bundles.
            if (!variantScope.variantConfiguration.buildType.isDebuggable) {
                task.signingConfig = variantScope.signingConfigFileCollection
            }
        }

    }

}
