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

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

open class DataBindingMergeBaseClassLogTask @Inject
constructor(workerExecutor: WorkerExecutor, objectFactory: ObjectFactory): IncrementalTask() {

    @get:OutputDirectory
    var outFolder: Provider<Directory> = objectFactory.directoryProperty()
        private set

    @get:InputFiles
    lateinit var moduleClassLog: FileCollection
        private set

    @get:InputFiles
    lateinit var externalClassLog: FileCollection
        private set



    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    private lateinit var delegate: DataBindingMergeBaseClassLogDelegate

    override fun isIncremental(): Boolean {
        return true
    }

    override fun doFullTaskAction() {
        delegate.doFullRun(workers)
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        delegate.doIncrementalRun(workers, changedInputs)
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<DataBindingMergeBaseClassLogTask>(variantScope) {

        override val name = variantScope.getTaskName("dataBindingMergeGenClasses")
        override val type = DataBindingMergeBaseClassLogTask::class.java

        private lateinit var outFolder: Provider<Directory>

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outFolder = variantScope
                .artifacts
                .createDirectory(
                    InternalArtifactType.DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS,
                    taskName)
        }

        override fun configure(task: DataBindingMergeBaseClassLogTask) {
            super.configure(task)

            task.outFolder = outFolder

            // data binding related artifacts for external libs
            task.moduleClassLog = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH,
                MODULE,
                ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
            )

            task.externalClassLog = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH,
                EXTERNAL,
                ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
            )

            task.delegate = DataBindingMergeBaseClassLogDelegate(
                task.moduleClassLog,
                task.externalClassLog,
                task.outFolder)
        }
    }
}
