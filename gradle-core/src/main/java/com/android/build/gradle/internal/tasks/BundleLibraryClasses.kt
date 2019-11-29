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

import com.android.SdkConstants
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.transforms.LibraryAarJarsTransform
import com.android.builder.packaging.JarMerger
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.inject.Inject

private val CLASS_PATTERN = Pattern.compile(".*\\.class$")
private val META_INF_PATTERN = Pattern.compile("^META-INF/.*$")

/**
 * Bundle all library classes in a jar. Additional filters can be specified, in addition to ones
 * defined in [LibraryAarJarsTransform.getDefaultExcludes].
 */
open class BundleLibraryClasses @Inject constructor(workerExecutor: WorkerExecutor) :
    AndroidVariantTask() {

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)
    private lateinit var toIgnoreRegExps: Supplier<List<String>>

    @get:OutputFile
    var output: Provider<RegularFile>? = null
        private set

    @get:Internal
    lateinit var packageName: Lazy<String>
        private set

    @get:InputFiles
    lateinit var classes: FileCollection
        private set

    @get:Input
    var packageBuildConfig: Boolean = false
        private set

    @Input
    fun getToIgnore() = toIgnoreRegExps.get()

    @TaskAction
    fun bundleClasses() {
        workers.use {
            it.submit(
                BundleLibraryClassesRunnable::class.java,
                BundleLibraryClassesRunnable.Params(
                    packageName.value,
                    toIgnoreRegExps.get(),
                    output!!.get().asFile,
                    classes.files,
                    packageBuildConfig
                )
            )
        }
    }

    class CreationAction(
        scope: VariantScope,
        private val publishedType: PublishedConfigType,
        private val toIgnoreRegExps: Supplier<List<String>> = Supplier { emptyList<String>() }
    ) :
        VariantTaskCreationAction<BundleLibraryClasses>(scope) {

        private val inputs: FileCollection

        init {
            check(
                publishedType == PublishedConfigType.API_ELEMENTS
                        || publishedType == PublishedConfigType.RUNTIME_ELEMENTS
            ) { "Library classes bundling is supported only for api and runtime." }

            // Because ordering matters for TransformAPI, we need to fetch classes from the
            // transform pipeline as soon as this creation action is instantiated.
            inputs = if (publishedType == PublishedConfigType.RUNTIME_ELEMENTS) {
                scope.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                    types.contains(QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.size == 1 && scopes.contains(QualifiedContent.Scope.PROJECT)
                }
            } else {
                variantScope.artifacts.getFinalArtifactFiles(AnchorOutputType.ALL_CLASSES).get()
            }
        }

        private lateinit var output: Provider<RegularFile>

        override val name: String =
            scope.getTaskName(
                if (publishedType == PublishedConfigType.API_ELEMENTS)
                    "bundleLibCompile"
                else
                    "bundleLibRuntime"
            )

        override val type: Class<BundleLibraryClasses> = BundleLibraryClasses::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            output = if (publishedType == PublishedConfigType.API_ELEMENTS) {
                variantScope.artifacts.createArtifactFile(
                    InternalArtifactType.COMPILE_LIBRARY_CLASSES,
                    BuildArtifactsHolder.OperationType.APPEND,
                    taskName,
                    FN_CLASSES_JAR
                )
            } else {
                variantScope.artifacts.createArtifactFile(
                    InternalArtifactType.RUNTIME_LIBRARY_CLASSES,
                    BuildArtifactsHolder.OperationType.APPEND,
                    taskName,
                    FN_CLASSES_JAR
                )
            }
        }

        override fun configure(task: BundleLibraryClasses) {
            super.configure(task)

            task.output = output
            task.packageName = lazy { variantScope.variantConfiguration.packageFromManifest }
            task.classes = inputs
            // FIXME pass this as List<TextResources>
            task.toIgnoreRegExps = TaskInputHelper.memoize(toIgnoreRegExps)
            task.packageBuildConfig = variantScope.globalScope.extension.packageBuildConfig
        }
    }
}

/** Packages files to jar using the provided filter. */
class BundleLibraryClassesRunnable @Inject constructor(private val params: Params) : Runnable {
    data class Params(
        val packageName: String,
        val toIgnore: List<String>,
        val output: File,
        val input: Set<File>,
        val packageBuildConfig: Boolean
    ) :
        Serializable

    override fun run() {
        Files.deleteIfExists(params.output.toPath())
        params.output.parentFile.mkdirs()

        val ignorePatterns =
            (LibraryAarJarsTransform.getDefaultExcludes(
                params.packageName,
                params.packageBuildConfig
            ) + params.toIgnore)
                .map { Pattern.compile(it) }

        val predicate = Predicate<String> { entry ->
            (CLASS_PATTERN.matcher(entry).matches() || META_INF_PATTERN.matcher(entry).matches())
                    && !ignorePatterns.any { it.matcher(entry).matches() }
        }

        JarMerger(params.output.toPath(), predicate).use { out ->
            params.input.forEach { base ->
                if (base.isDirectory) {
                    out.addDirectory(base.toPath())
                } else if (base.toString().endsWith(SdkConstants.DOT_JAR)) {
                    out.addJar(base.toPath())
                }
            }
        }
    }
}