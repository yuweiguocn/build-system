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

package com.android.build.gradle.internal.feature

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.res.namespaced.JarRequest
import com.android.build.gradle.internal.res.namespaced.JarWorkerRunnable
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Task to jar all classes in a project. This includes pre/post java classes, and compiled
 * namespaced R class (if it exists).
 *
 * It is used for e.g.:
 * - dependent features to compile against these classes without bundling them.
 * - unit tests to compile and run them against these classes.
 */
open class BundleAllClasses @Inject constructor(workerExecutor: WorkerExecutor) : AndroidVariantTask() {

    val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    @get:OutputFile
    lateinit var outputJar: File
        private set

    @get:InputFiles
    lateinit var javacClasses: BuildableArtifact
        private set

    @get:InputFiles
    lateinit var preJavacClasses: FileCollection
        private set

    @get:InputFiles
    lateinit var postJavacClasses: FileCollection
        private set

    @get:InputFiles
    @get:Optional
    var thisRClassClasses: BuildableArtifact? = null
        private set

    @get:InputFiles
    @get:Optional
    var dependencyRClassClasses: FileCollection? = null
        private set

    @get:Input
    lateinit var modulePath: String
        private set

    @TaskAction
    fun merge() {
        val files = HashMap<String, File>()
        val collector = object: ReproducibleFileVisitor {
            override fun isReproducibleFileOrder() = true
            override fun visitFile(fileVisitDetails: FileVisitDetails) {
                files[fileVisitDetails.relativePath.pathString] = fileVisitDetails.file
            }
            override fun visitDir(fileVisitDetails: FileVisitDetails) {
            }
        }
        (javacClasses as BuildableArtifactImpl).asFileTree.visit(collector)
        preJavacClasses.asFileTree.visit(collector)
        postJavacClasses.asFileTree.visit(collector)
        thisRClassClasses?.get()?.asFileTree?.visit(collector)

        workers.use {
            it.submit(JarWorkerRunnable::class.java,
                JarRequest(toFile = outputJar,
                    fromJars = dependencyRClassClasses?.files?.toList() ?: listOf(),
                    fromFiles = files))
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<BundleAllClasses>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("bundle", "Classes")
        override val type: Class<BundleAllClasses>
            get() = BundleAllClasses::class.java

        private lateinit var outputJar: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outputJar = variantScope.artifacts.appendArtifact(
                InternalArtifactType.APP_CLASSES, taskName, "classes.jar")
        }

        override fun configure(task: BundleAllClasses) {
            super.configure(task)
            task.outputJar = outputJar
            task.javacClasses =
                    variantScope.artifacts.getArtifactFiles(InternalArtifactType.JAVAC)
            task.preJavacClasses = variantScope.variantData.allPreJavacGeneratedBytecode
            task.postJavacClasses = variantScope.variantData.allPostJavacGeneratedBytecode
            val globalScope = variantScope.globalScope
            task.modulePath = globalScope.project.path
            if (globalScope.extension.aaptOptions.namespaced) {
                task.thisRClassClasses = variantScope.artifacts
                    .getFinalArtifactFiles(InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
                task.dependencyRClassClasses = variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        ALL,
                        COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
            }
        }
    }
}
