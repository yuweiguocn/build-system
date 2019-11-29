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

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * A task that checks that project external dependencies do not contain duplicate classes. Without
 * this task in case duplicate classes exist the failure happens during dexing stage and the error
 * is not especially user friendly. Moreover, we would like to fail fast.
 */
@CacheableTask
open class CheckDuplicateClassesTask @Inject constructor(workerExecutor: WorkerExecutor) :
    AndroidVariantTask() {

    private lateinit var classesArtifacts: ArtifactCollection

    @get:OutputDirectory
    var dummyOutputDirectory: Provider<Directory>? = null
        private set

    @InputFiles
    @Classpath
    fun getClassesFiles(): FileCollection = classesArtifacts.artifactFiles

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    @TaskAction
    fun taskAction() {
        CheckDuplicateClassesDelegate(classesArtifacts).run(workers)
    }

    class CreationAction(scope: VariantScope)
        : VariantTaskCreationAction<CheckDuplicateClassesTask>(scope) {

        private lateinit var output: Provider<Directory>

        override val type = CheckDuplicateClassesTask::class.java

        override val name = variantScope.getTaskName("check", "DuplicateClasses")

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            output = variantScope.artifacts.createDirectory(
                InternalArtifactType.DUPLICATE_CLASSES_CHECK, taskName)
        }

        override fun configure(task: CheckDuplicateClassesTask) {
            super.configure(task)

            task.classesArtifacts =
                    variantScope.getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES)

            task.dummyOutputDirectory = output
        }
    }
}