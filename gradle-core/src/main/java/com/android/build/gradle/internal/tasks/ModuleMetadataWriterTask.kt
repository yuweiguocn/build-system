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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_BASE_MODULE_DECLARATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException

/**
 * Task responsible for publishing this module metadata (like its application ID) for other modules
 * to consume.
 *
 * If the module is an application module, it publishes the value coming from the variant config.
 *
 * If the module is a base feature, it consumes the value coming from the (installed) application
 * module and republishes it.
 *
 * Both dynamic-feature and feature modules consumes it, from the application module and the base
 * feature module respectively.
 */
open class ModuleMetadataWriterTask : AndroidVariantTask() {

    @get:Input lateinit var applicationId: Provider<String> private set

    private lateinit var outputScope: OutputScope

    @get:Input
    val versionCode
        get() = outputScope.mainSplit.versionCode

    @get:Input
    @get:Optional
    val versionName
        get() = outputScope.mainSplit.versionName

    @get:Input
    var debuggable: Boolean = false
    private set

    @get:InputFiles
    @get:Optional
    var metadataFromInstalledModule: FileCollection? = null
        private set

    @get:OutputFile
    lateinit var outputFile: File
        private set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        val declaration =
            if (metadataFromInstalledModule != null && !metadataFromInstalledModule!!.isEmpty) {
                ModuleMetadata.load(metadataFromInstalledModule!!.singleFile)
            } else {
                ModuleMetadata(
                    applicationId = applicationId.get(),
                    versionCode = versionCode.toString(),
                    versionName = versionName,
                    debuggable = debuggable
                )
            }

        declaration.save(outputFile)
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ModuleMetadataWriterTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("write", "ModuleMetadata")
        override val type: Class<ModuleMetadataWriterTask>
            get() = ModuleMetadataWriterTask::class.java

        private lateinit var outputFile: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outputFile = variantScope.artifacts.appendArtifact(
                InternalArtifactType.METADATA_BASE_MODULE_DECLARATION,
                taskName,
                ModuleMetadata.PERSISTED_FILE_NAME
            )

            if (!variantScope.type.isHybrid) {
                //if this is the base application, publish the feature to the metadata config
                variantScope.artifacts.appendArtifact(
                    InternalArtifactType.METADATA_INSTALLED_BASE_DECLARATION,
                    listOf(outputFile),
                    taskName
                )
            }
        }

        override fun configure(task: ModuleMetadataWriterTask) {
            super.configure(task)

            // default value of the app ID to publish. This may get overwritten by something
            // coming from an application module.
            task.applicationId = TaskInputHelper.memoizeToProvider(task.project) {
                variantScope.variantConfiguration.applicationId
            }

            task.outputScope = variantScope.variantData.outputScope

            task.debuggable = variantScope.variantConfiguration.buildType.isDebuggable

            // publish the ID for the dynamic features (whether it's hybrid or not) to consume.
            task.outputFile = outputFile

            if (variantScope.type.isHybrid) {
                //if this is a feature, get the Application ID from the metadata config
                task.metadataFromInstalledModule = variantScope.getArtifactFileCollection(
                    METADATA_VALUES, MODULE, METADATA_BASE_MODULE_DECLARATION
                )
            }
        }
    }
}
