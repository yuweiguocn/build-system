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

package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Task to strip resource namespaces from the library's android manifest. This stripped manifest
 * needs to be bundled in the AAR as the AndroidManifest.xml artifact, so that it's consumable by
 * non-namespaced projects.
 */
@CacheableTask
open class CreateNonNamespacedLibraryManifestTask @Inject constructor(workerExecutor: WorkerExecutor)
    : AndroidVariantTask() {

    @get:OutputFile
    lateinit var outputStrippedManifestFile: File
    private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var libraryManifest: Provider<RegularFile>
    private set

    private val workers = Workers.getWorker(workerExecutor)

    @TaskAction
    fun createManifest() {
        workers.use {
            it.submit(CreateNonNamespacedLibraryManifestRunnable::class.java,
                CreateNonNamespacedLibraryManifestRequest(
                    libraryManifest.get().asFile, outputStrippedManifestFile))
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<CreateNonNamespacedLibraryManifestTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("create", "NonNamespacedLibraryManifest")
        override val type: Class<CreateNonNamespacedLibraryManifestTask>
            get() = CreateNonNamespacedLibraryManifestTask::class.java

        private lateinit var outputStrippedManifestFile: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outputStrippedManifestFile = variantScope.artifacts.appendArtifact(
                InternalArtifactType.NON_NAMESPACED_LIBRARY_MANIFEST,
                taskName,
                SdkConstants.ANDROID_MANIFEST_XML)
        }

        override fun configure(task: CreateNonNamespacedLibraryManifestTask) {
            super.configure(task)
            task.outputStrippedManifestFile = outputStrippedManifestFile
            task.libraryManifest =
                    variantScope.artifacts.getFinalProduct(InternalArtifactType.LIBRARY_MANIFEST)
        }
    }
}