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

package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Task to jar the compilation-only library R class in to the AAR.
 *
 * Not used for inter-project dependencies, where the classes directory is used directly.
 */
open class JarRClassTask @Inject constructor(workerExecutor: WorkerExecutor) : DefaultTask() {

    @get:InputFiles lateinit var rClassClasses: FileCollection private set
    @get:OutputFile lateinit var rClassJar: File private set
    private val workers = Workers.getWorker(workerExecutor)

    @TaskAction
    fun jar() {
        workers.use {
            it.submit(JarWorkerRunnable::class.java,
                JarRequest(
                    toFile = rClassJar,
                    fromDirectories = listOf(rClassClasses.singleFile)
                )
            )
        }
    }

    class CreationAction(
                override val name: String,
                private val rClassClasses: FileCollection,
                private val rClassJar: File) : TaskCreationAction<JarRClassTask>() {
        override val type: Class<JarRClassTask>
            get() = JarRClassTask::class.java

        override fun configure(task: JarRClassTask) {
            task.rClassClasses = rClassClasses
            task.rClassJar = rClassJar
        }
    }
}