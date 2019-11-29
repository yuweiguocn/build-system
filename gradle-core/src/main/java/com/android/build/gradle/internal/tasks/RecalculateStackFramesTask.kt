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

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.builder.utils.FileCache
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

open class RecalculateStackFramesTask @Inject
constructor(workerExecutor: WorkerExecutor) : IncrementalTask() {

    @get:OutputDirectory
    var outFolder: Provider<Directory>? = null
        private set

    @get:InputFiles
    lateinit var bootClasspath: FileCollection
        private set

    @get:InputFiles
    lateinit var classesToFix: FileCollection
        private set

    @get:InputFiles
    lateinit var referencedClasses: FileCollection
        private set

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    private var userCache: FileCache? = null

    private fun createDelegate() = FixStackFramesDelegate(
        bootClasspath.files, classesToFix.files, referencedClasses.files, outFolder!!.get().asFile, userCache
    )

    override fun isIncremental(): Boolean {
        return true
    }

    override fun doFullTaskAction() {
        createDelegate().doFullRun(workers)
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        createDelegate().doIncrementalRun(workers, changedInputs)
    }

    class CreationAction(
        variantScope: VariantScope,
        private val userCache: FileCache?,
        private val isTestCoverageEnabled: Boolean) :
        VariantTaskCreationAction<RecalculateStackFramesTask>(variantScope) {

        override val name = variantScope.getTaskName("fixStackFrames")
        override val type = RecalculateStackFramesTask::class.java

        private lateinit var outFolder: Provider<Directory>

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outFolder = variantScope
                .artifacts
                .createDirectory(InternalArtifactType.FIXED_STACK_FRAMES, taskName)
        }

        override fun configure(task: RecalculateStackFramesTask) {
            super.configure(task)

            task.bootClasspath = variantScope.bootClasspath

            val globalScope = variantScope.globalScope

            val classesToFix = globalScope.project.files(
                variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                    AndroidArtifacts.ArtifactType.CLASSES))

            if (globalScope.extension.aaptOptions.namespaced
                && globalScope.projectOptions[BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES]) {
                classesToFix.from(
                    variantScope
                        .artifacts
                        .getFinalArtifactFiles(InternalArtifactType.NAMESPACED_CLASSES_JAR))
            }


            val referencedClasses = globalScope.project.files(variantScope.providedOnlyClasspath)

            referencedClasses.from(variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.MODULE,
                AndroidArtifacts.ArtifactType.CLASSES))

            if (isTestCoverageEnabled) {
                referencedClasses.from(
                    variantScope.artifacts.getFinalArtifactFiles(
                        InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES))
            } else {
                referencedClasses.from(
                    variantScope.artifacts.getFinalArtifactFiles(AnchorOutputType.ALL_CLASSES))
            }

            variantScope.testedVariantData?.let {
                val testedVariantScope = it.scope

                referencedClasses.from(
                    variantScope.artifacts.getFinalArtifactFiles(
                        InternalArtifactType.TESTED_CODE_CLASSES))

                referencedClasses.from(testedVariantScope.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.CLASSES))
            }

            task.classesToFix = classesToFix

            task.referencedClasses = referencedClasses

            task.outFolder = outFolder

            task.userCache = userCache
        }
    }
}