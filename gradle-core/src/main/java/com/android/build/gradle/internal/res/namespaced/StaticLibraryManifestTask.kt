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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.google.common.base.Suppliers
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Task to write an android manifest for the res.apk static library
 */
@CacheableTask
open class StaticLibraryManifestTask @Inject constructor(workerExecutor: WorkerExecutor)
    : AndroidVariantTask() {

    @get:Internal lateinit var packageNameSupplier: Supplier<String> private set
    @get:Input val packageName get() = packageNameSupplier.get()
    @get:OutputFile lateinit var manifestFile: File private set

    private val workers = Workers.getWorker(workerExecutor)

    @TaskAction
    fun createManifest() {
        workers.use {
            it.submit(StaticLibraryManifestRunnable::class.java,
                StaticLibraryManifestRequest(manifestFile, packageName))
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<StaticLibraryManifestTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("create", "StaticLibraryManifest")
        override val type: Class<StaticLibraryManifestTask>
            get() = StaticLibraryManifestTask::class.java

        private lateinit var manifestFile: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            manifestFile = variantScope.artifacts.appendArtifact(InternalArtifactType.STATIC_LIBRARY_MANIFEST,
                taskName,
                SdkConstants.ANDROID_MANIFEST_XML)
        }

        override fun configure(task: StaticLibraryManifestTask) {
            super.configure(task)
            task.manifestFile = manifestFile
            task.packageNameSupplier =
                    Suppliers.memoize(variantScope.variantConfiguration::getOriginalApplicationId)
        }
    }
}