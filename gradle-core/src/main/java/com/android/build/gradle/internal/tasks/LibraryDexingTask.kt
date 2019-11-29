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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.Workers.getWorker
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.SerializableMessageReceiver
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.ide.common.blame.MessageReceiver
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * This is implementation of dexing artifact transform as a task. It is used when building
 * android test variant for library projects. Once http://b/115334911 is fixed, this can be removed.
 */
@CacheableTask
open class LibraryDexingTask @Inject constructor(
    objectFactory: ObjectFactory,
    executor: WorkerExecutor) : AndroidVariantTask() {

    private val workers: WorkerExecutorFacade =
        getWorker(executor, MoreExecutors.newDirectExecutorService())

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var classes: BuildableArtifact
        private set

    @get:OutputDirectory
    var output: Provider<Directory> = objectFactory.directoryProperty()
        private set

    @get:Input
    var minSdkVersion = 1
        private set

    @get:Internal
    lateinit var messageReceiver: MessageReceiver
        private set

    @TaskAction
    fun doAction() {
        workers.use {
            it.submit(
                DexingRunnable::class.java,
                DexParams(
                    minSdkVersion,
                    SerializableMessageReceiver(messageReceiver),
                    classes.single(),
                    output.get().asFile
                )
            )
        }
    }

    class CreationAction(val scope: VariantScope) :
        VariantTaskCreationAction<LibraryDexingTask>(scope) {
        override val name = scope.getTaskName("dex")
        override val type = LibraryDexingTask::class.java

        private lateinit var output: Provider<Directory>

        override fun preConfigure(taskName: String) {
            output = scope.artifacts.createDirectory(InternalArtifactType.DEX, taskName)
        }

        override fun configure(task: LibraryDexingTask) {
            super.configure(task)
            task.classes =
                    scope.artifacts.getFinalArtifactFiles(InternalArtifactType.RUNTIME_LIBRARY_CLASSES)
            task.minSdkVersion = scope.minSdkVersion.featureLevel
            task.output = output
            task.messageReceiver = scope.globalScope.messageReceiver
        }
    }
}

private class DexParams(
    val minSdkVersion: Int,
    val messageReceiver: MessageReceiver,
    val input: File,
    val output: File
) : Serializable

private class DexingRunnable @Inject constructor(val params: DexParams) : Runnable {
    override fun run() {
        val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
            params.minSdkVersion,
            true,
            ClassFileProviderFactory(listOf()),
            ClassFileProviderFactory(listOf()),
            false,
            params.messageReceiver
        )

        ClassFileInputs.fromPath(params.input.toPath()).use { classFileInput ->
            classFileInput.entries { _ -> true }.use { classesInput ->
                d8DexBuilder.convert(
                    classesInput,
                    params.output.toPath(),
                    false
                )
            }
        }
    }
}