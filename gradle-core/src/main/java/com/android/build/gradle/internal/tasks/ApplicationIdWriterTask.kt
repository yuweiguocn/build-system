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

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_BASE_MODULE_DECLARATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.apache.commons.io.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException

/**
 * Task responsible for publishing the application Id.
 *
 * If the module is an application module, it publishes the value coming from the variant config.
 *
 * If the module is a feature module, it consumes the value coming from the (installed) application
 * module and republishes it.
 *
 * This task is currently used to publish the output as a text resource for others to consume.
 */
open class ApplicationIdWriterTask : AndroidVariantTask() {

    @get:Internal
    private var applicationIdSupplier: () -> String? = { null }

    @get:Input
    @get:Optional
    val applicationId get() = applicationIdSupplier()

    @get:InputFiles
    @get:Optional
    var appMetadata: FileCollection? = null
        private set

    @get:OutputFile
    lateinit var outputFile: File
        private set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        val resolvedApplicationId = appMetadata?.let {
            ModuleMetadata.load(it.singleFile).applicationId
        } ?: applicationId

        if (resolvedApplicationId != null) {
            FileUtils.write(outputFile, resolvedApplicationId)
        } else {
            logger.error("ApplicationId could not be resolved for $variantName")
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ApplicationIdWriterTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("write", "ApplicationId")
        override val type: Class<ApplicationIdWriterTask>
            get() = ApplicationIdWriterTask::class.java

        private lateinit var outputFile: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outputFile = variantScope.artifacts.appendArtifact(
                InternalArtifactType.METADATA_APPLICATION_ID,
                taskName,
                "application-id.txt"
            )
        }

        override fun configure(task: ApplicationIdWriterTask) {
            super.configure(task)

            task.outputFile = outputFile
            // if BASE_FEATURE get the app ID from the app module
            if (variantScope.type.isBaseModule && variantScope.type.isHybrid) {
                task.appMetadata = variantScope.getArtifactFileCollection(
                    METADATA_VALUES, MODULE, METADATA_BASE_MODULE_DECLARATION
                )
            } else if (variantScope.type.isFeatureSplit) {
                // if feature split, get it from the base module
                task.appMetadata = variantScope.getArtifactFileCollection(
                    COMPILE_CLASSPATH, MODULE, FEATURE_APPLICATION_ID_DECLARATION
                )
            } else {
                task.applicationIdSupplier = { variantScope.variantConfiguration.applicationId }
            }
        }
    }
}
