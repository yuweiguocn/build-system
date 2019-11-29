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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.DataBindingBuilder
import android.databinding.tool.FeaturePackageInfo
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.function.Supplier

import javax.inject.Inject

/**
 * This task collects necessary information for the data binding annotation processor to generate
 * the correct code.
 * <p>
 * It has 2 main functionality:
 * a) copy the package id resource offset for the feature so that data binding can properly offset
 * BR class ids.
 *
 * b) copy the BR-bin files from dependencies FOR WHICH a BR file needs to be generated.
 * These are basically dependencies which need to be packaged by this feature. (e.g. if a library
 * dependency is already a dependency of another feature, its BR class will already have been
 * generated)
 */
open class DataBindingExportFeatureInfoTask @Inject constructor(workerExecutor: WorkerExecutor)
    : AndroidVariantTask() {
    @get:OutputDirectory lateinit var outFolder: File
        private set

    private lateinit var resOffsetSupplier: Supplier<Int>

    @Suppress("MemberVisibilityCanBePrivate")
    @get:Input
    val resOffset: Int
        get() = resOffsetSupplier.get()

    private val workers = Workers.getWorker(workerExecutor)

    /**
     * In a feature, we only need to generate code for its Runtime dependencies as compile
     * dependencies are already available via other dependencies (base feature or another feature)
     */
    @get:InputFiles lateinit var directDependencies: FileCollection
        private set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        workers.use {
            it.submit(ExportFeatureInfoRunnable::class.java,
                ExportFeatureInfoParams(
                    outFolder = outFolder,
                    resOffset = resOffset,
                    directDependencies = directDependencies.asFileTree.files
                )
            )
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<DataBindingExportFeatureInfoTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("dataBindingExportFeatureInfo")
        override val type: Class<DataBindingExportFeatureInfoTask>
            get() = DataBindingExportFeatureInfoTask::class.java

        private lateinit var outFolder: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outFolder = variantScope.artifacts
                .appendArtifact(InternalArtifactType.FEATURE_DATA_BINDING_FEATURE_INFO,
                    taskName)
        }

        override fun configure(task: DataBindingExportFeatureInfoTask) {
            super.configure(task)

            task.outFolder = outFolder
            task.directDependencies = variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.DATA_BINDING_ARTIFACT)
            task.resOffsetSupplier = FeatureSetMetadata.getInstance().getResOffsetSupplierForTask(variantScope, task)
        }
    }
}

data class ExportFeatureInfoParams(
    val outFolder: File,
    val resOffset: Int,
    val directDependencies: Set<File>
) : Serializable

class ExportFeatureInfoRunnable @Inject constructor(
    val params: ExportFeatureInfoParams
) : Runnable {
    override fun run() {
        FileUtils.cleanOutputDir(params.outFolder)
        params.outFolder.mkdirs()
        params.directDependencies.filter {
            it.name.endsWith(DataBindingBuilder.BR_FILE_EXT)
        }.forEach {
            FileUtils.copyFile(it, File(params.outFolder, it.name))
        }
        // save the package id offset
        FeaturePackageInfo(packageId = params.resOffset).serialize(
                File(params.outFolder, DataBindingBuilder.FEATURE_BR_OFFSET_FILE_NAME)
        )
    }
}