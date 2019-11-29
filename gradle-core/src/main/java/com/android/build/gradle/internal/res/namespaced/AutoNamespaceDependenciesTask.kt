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

import android.databinding.tool.util.Preconditions
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.sdklib.IAndroidTarget
import com.android.tools.build.apkzlib.zip.StoredEntryType
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.utils.FileUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject

/**
 * Rewrites the non-namespaced AAR dependencies of this module to be namespaced.
 *
 * 1. Build a model of where the resources of non-namespaced AARs have come from.
 * 2. Rewrites the classes.jar to be namespaced
 * 3. Rewrites the manifest to use namespaced resource references
 * 4. Rewrites the resources themselves to use namespaced references, and compiles
 *    them in to a static library.
 */
@CacheableTask
open class AutoNamespaceDependenciesTask @Inject constructor(workerExecutor: WorkerExecutor) :
    AndroidBuilderTask() {

    lateinit var rFiles: ArtifactCollection private set
    lateinit var nonNamespacedManifests: ArtifactCollection private set
    lateinit var jarFiles: ArtifactCollection private set
    lateinit var dependencies: ResolvableDependencies private set
    lateinit var externalNotNamespacedResources: ArtifactCollection private set
    lateinit var externalResStaticLibraries: ArtifactCollection private set
    lateinit var publicFiles: ArtifactCollection private set

    @InputFiles fun getRDefFiles(): FileCollection = rFiles.artifactFiles
    @InputFiles fun getManifestsFiles(): FileCollection = nonNamespacedManifests.artifactFiles
    @InputFiles fun getClassesJarFiles(): FileCollection = jarFiles.artifactFiles
    @InputFiles fun getPublicFilesArtifactFiles(): FileCollection = publicFiles.artifactFiles
    @InputFiles
    fun getNonNamespacedResourcesFiles(): FileCollection =
        externalNotNamespacedResources.artifactFiles

    @InputFiles
    fun getStaticLibraryDependenciesFiles(): FileCollection =
        externalResStaticLibraries.artifactFiles

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    /**
     * Reading the R files and building symbol tables is costly, and is wasteful to repeat,
     * hence, use a LoadingCache.
     */
    private var symbolTablesCache: LoadingCache<File, SymbolTable> = CacheBuilder.newBuilder()
        .build(
            object : CacheLoader<File, SymbolTable>() {
                override fun load(rDefFile: File): SymbolTable {
                    return SymbolIo.readRDef(rDefFile.toPath())
                }
            }
        )

    @get:OutputDirectory
    lateinit var outputStaticLibraries: File
        private set
    @get:OutputFile lateinit var outputClassesJar: File private set
    @get:OutputFile lateinit var outputRClassesJar: File private set
    @get:OutputDirectory lateinit var outputRewrittenManifests: File private set

    lateinit var intermediateDirectory: File private set

    @TaskAction
    fun taskAction() = autoNamespaceDependencies()

    private fun autoNamespaceDependencies(
        aapt2FromMaven: FileCollection = this.aapt2FromMaven,
        dependencies: ResolvableDependencies = this.dependencies,
        rFiles: ArtifactCollection = this.rFiles,
        jarFiles: ArtifactCollection = this.jarFiles,
        manifests: ArtifactCollection = this.nonNamespacedManifests,
        notNamespacedResources: ArtifactCollection = this.externalNotNamespacedResources,
        staticLibraryDependencies: ArtifactCollection = this.externalResStaticLibraries,
        intermediateDirectory: File = this.intermediateDirectory,
        outputStaticLibraries: File = this.outputStaticLibraries,
        outputClassesJar: File = this.outputClassesJar,
        outputRClassesJar: File = this.outputRClassesJar,
        outputManifests: File = this.outputRewrittenManifests,
        publicFiles: ArtifactCollection = this.publicFiles
    ) {

        try {
            val fileMaps =
                ImmutableMap.builder<ArtifactType, ImmutableMap<String, ImmutableCollection<File>>>()
                    .put(ArtifactType.DEFINED_ONLY_SYMBOL_LIST, rFiles.toMap())
                    .put(ArtifactType.NON_NAMESPACED_CLASSES, jarFiles.toMap())
                    .put(ArtifactType.NON_NAMESPACED_MANIFEST, manifests.toMap())
                    .put(ArtifactType.ANDROID_RES, notNamespacedResources.toMap())
                    .put(ArtifactType.RES_STATIC_LIBRARY, staticLibraryDependencies.toMap())
                    .put(ArtifactType.PUBLIC_RES, publicFiles.toMap())
                    .build()

            val graph = DependenciesGraph.create(
                dependencies,
                fileMaps
            )

            val rewrittenResources = File(intermediateDirectory, "namespaced_res")
            val rewrittenClasses = File(intermediateDirectory, "namespaced_classes")
            val rewrittenRClasses = File(intermediateDirectory, "namespaced_r_classes")

            val rewrittenResourcesMap = namespaceDependencies(
                graph = graph,
                outputRewrittenClasses = rewrittenClasses,
                outputRClasses = rewrittenRClasses,
                outputManifests = outputManifests,
                outputResourcesDir = rewrittenResources
            )

            // Jar all the classes into two JAR files - one for namespaced classes, one for R classes.
            jarOutputs(outputClassesJar, rewrittenClasses)
            jarOutputs(outputRClassesJar, rewrittenRClasses)

            val aapt2ServiceKey = registerAaptService(aapt2FromMaven, logger = iLogger)

            val outputCompiledResources = File(intermediateDirectory, "compiled_namespaced_res")
            // compile the rewritten resources
            val compileMap =
                compile(
                    rewrittenResourcesMap = rewrittenResourcesMap,
                    aapt2ServiceKey = aapt2ServiceKey,
                    outputDirectory = outputCompiledResources
                )

            // then link them in to static libraries.
            val nonNamespacedDependenciesLinker = NonNamespacedDependenciesLinker(
                graph = graph,
                compiled = compileMap,
                outputStaticLibrariesDirectory = outputStaticLibraries,
                intermediateDirectory = intermediateDirectory,
                aapt2ServiceKey = aapt2ServiceKey,
                androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR)
            )
            nonNamespacedDependenciesLinker.link()
        } finally {
            symbolTablesCache.invalidateAll()
        }
    }

    @VisibleForTesting
    internal fun namespaceDependencies(
        graph: DependenciesGraph,
        outputRewrittenClasses: File,
        outputRClasses: File,
        outputManifests: File,
        outputResourcesDir: File
    ): Map<DependenciesGraph.Node, File> {
        FileUtils.cleanOutputDir(outputRewrittenClasses)
        FileUtils.cleanOutputDir(outputRClasses)
        FileUtils.cleanOutputDir(outputManifests)
        FileUtils.cleanOutputDir(outputResourcesDir)


        // The rewriting works per node, since for rewriting a library the only files from its
        // dependencies we need are their R-def.txt files, which were already generated by the
        // [LibraryDefinedSymbolTableTransform].
        // TODO: do this all as one action to interleave work.
        val rewrittenResources = ImmutableMap.builder<DependenciesGraph.Node, File>()

        for (dependency in graph.allNodes) {
            val outputResources = if (dependency.getFile(ArtifactType.ANDROID_RES) != null) {
                File(
                    outputResourcesDir,
                    dependency.sanitizedName
                )
            } else {
                null
            }
            outputResources?.apply { rewrittenResources.put(dependency, outputResources) }

            // Only convert external nodes and non-namespaced libraries. Already namespaced libraries
            // and JAR files can be present in the graph, but they will not contain the
            // NON_NAMESPACED_CLASSES artifacts. Only try to rewrite non-namespaced libraries' classes.
            if (dependency.id !is ProjectComponentIdentifier && dependency.getFiles(ArtifactType.NON_NAMESPACED_CLASSES) != null) {
                workers.submit(
                    NamespaceDependencyRunnable::class.java, NamespaceDependencyParams(
                        dependency,
                        outputRewrittenClasses,
                        outputRClasses,
                        outputManifests,
                        outputResources,
                        getSymbolTables(dependency)
                    )
                )
            }
        }

        workers.await()
        return rewrittenResources.build()
    }

    private fun jarOutputs(outputJar: File, inputDirectory: File) {
        ZFile(outputJar, ZFileOptions(), false).use { jar ->
            Files.walk(inputDirectory.toPath()).use { paths ->
                paths.filter { p -> p.toFile().isFile}.forEach { it ->
                    ZFile(it.toFile(), ZFileOptions(), true).use { classesJar ->
                        classesJar.entries().forEach { entry ->
                            val name = entry.centralDirectoryHeader.name
                            if (entry.type == StoredEntryType.FILE && name.endsWith(".class")) {
                                jar.add(name, entry.open())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun compile(
        rewrittenResourcesMap: Map<DependenciesGraph.Node, File>,
        aapt2ServiceKey: Aapt2ServiceKey,
        outputDirectory: File
    ): Map<DependenciesGraph.Node, File> {
        val compiled = ImmutableMap.builder<DependenciesGraph.Node, File>()

        rewrittenResourcesMap.forEach { node, rewrittenResources ->
            val nodeOutputDirectory = File(outputDirectory, node.sanitizedName)
            compiled.put(node, nodeOutputDirectory)
            Files.createDirectories(nodeOutputDirectory.toPath())
            for (resConfigurationDir in rewrittenResources.listFiles()) {
                for (resourceFile in resConfigurationDir.listFiles()) {
                    val request = CompileResourceRequest(
                        inputFile = resourceFile,
                        outputDirectory = nodeOutputDirectory
                    )
                    workers.submit(
                        Aapt2CompileRunnable::class.java,
                        Aapt2CompileRunnable.Params(aapt2ServiceKey, listOf(request))
                    )
                }
            }
        }
        workers.await()
        return compiled.build()
    }



    private fun getSymbolTables(node: DependenciesGraph.Node): ImmutableList<SymbolTable> {
        val builder = ImmutableList.builder<SymbolTable>()
        for (rDefFile in node.getTransitiveFiles(ArtifactType.DEFINED_ONLY_SYMBOL_LIST)) {
            builder.add(symbolTablesCache.getUnchecked(rDefFile))
        }
        return builder.build()
    }

    private fun ArtifactCollection.toMap(): ImmutableMap<String, ImmutableCollection<File>> =
        HashMap<String, MutableCollection<File>>().apply {
            for (artifact in artifacts) {
                val key = artifact.id.componentIdentifier.displayName
                getOrPut(key) { mutableListOf() }.add(artifact.file)
            }
        }.toImmutableMap { it.toImmutableList() }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<AutoNamespaceDependenciesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("autoNamespace", "Dependencies")
        override val type: Class<AutoNamespaceDependenciesTask>
            get() = AutoNamespaceDependenciesTask::class.java

        private lateinit var outputClassesJar: File
        private lateinit var outputRClassesJar: File
        private lateinit var outputStaticLibraries: File
        private lateinit var outputRewrittenManifests: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outputClassesJar = variantScope.artifacts.appendArtifact(
                InternalArtifactType.NAMESPACED_CLASSES_JAR, taskName, "namespaced-classes.jar")

            outputRClassesJar = variantScope.artifacts.appendArtifact(
                InternalArtifactType.COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JAR,
                taskName,
                "namespaced-R.jar")

            outputStaticLibraries = variantScope.artifacts.appendArtifact(
                InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES,
                taskName
            )

            outputRewrittenManifests = variantScope.artifacts.appendArtifact(
                InternalArtifactType.NAMESPACED_MANIFESTS, taskName)

        }

        override fun configure(task: AutoNamespaceDependenciesTask) {
            super.configure(task)

            task.rFiles = variantScope.getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.EXTERNAL,
                    ArtifactType.DEFINED_ONLY_SYMBOL_LIST
            )

            task.jarFiles = variantScope.getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.EXTERNAL,
                    ArtifactType.NON_NAMESPACED_CLASSES
            )

            task.nonNamespacedManifests = variantScope.getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.EXTERNAL,
                    ArtifactType.NON_NAMESPACED_MANIFEST
            )

            task.publicFiles = variantScope.getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.EXTERNAL,
                    ArtifactType.PUBLIC_RES
            )

            task.outputRewrittenManifests = outputRewrittenManifests
            task.outputClassesJar = outputClassesJar
            task.outputRClassesJar = outputRClassesJar
            task.outputStaticLibraries = outputStaticLibraries

            task.dependencies =
                    variantScope.variantData.variantDependency.runtimeClasspath.incoming

            task.externalNotNamespacedResources = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.ANDROID_RES
            )

            task.externalResStaticLibraries = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.RES_STATIC_LIBRARY
            )

            task.intermediateDirectory = variantScope.getIncrementalDir(name)

            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
            task.setAndroidBuilder(variantScope.globalScope.androidBuilder)
        }
    }

    companion object {
        @VisibleForTesting
        internal var log: Logger? = null
    }
}

private class NamespaceDependencyRunnable @Inject constructor(val params: NamespaceDependencyParams) :
    Runnable {

    override fun run() {
        val logger = Logging.getLogger(AutoNamespaceDependenciesTask::class.java)

        Preconditions.checkNotNull(
            params.manifest,
            "Manifest missing for library ${params.dependencyName}"
        )

        // The rewriting algorithm uses ordered symbol tables, with this library's table at the
        // top of the list. It looks up resources starting from the top of the list, trying to
        // find where the references resource was defined (or overridden), closest to the root
        // (this node) in the dependency graph.
        logger.info("Started rewriting ${params.dependencyName}")
        val rewriter =
            NamespaceRewriter(params.symbolTables, AutoNamespaceDependenciesTask.log ?: logger)

        // Brittle, relies on the AAR expansion logic that makes sure all jars have unique names
        try {
            params.inputClasses!!.forEach {
                val out = File(
                    params.outputClassesDirectory,
                    "namespaced-${params.dependencySanitizedName}-${it.name}"
                )
                rewriter.rewriteJar(it, out)
            }
        } catch (e: Exception) {
            throw IOException(
                "Failed to transform jar + ${params.transitiveFiles}", e
            )
        }
        rewriter.rewriteManifest(
            params.manifest!!.toPath(),
            params.outputManifests.toPath().resolve("${params.dependencySanitizedName}_AndroidManifest.xml")
        )
        if (params.resources != null) {
            rewriter.rewriteAarResources(
                params.resources.toPath(),
                params.outputResourcesDirectory!!.toPath()
            )

            rewriter.generatePublicFile(
                params.publicTxt,
                params.outputResourcesDirectory.toPath()
            )
        }

        logger.info("Finished rewriting ${params.dependencyName}")

        // Also generate fake R classes for compilation.
        rewriter.writeRClass(
            File(
                params.outputRClassesDirectory,
                "namespaced-${params.dependencySanitizedName}-R.jar"
            ).toPath()
        )
    }
}

private class NamespaceDependencyParams(
    dependency: DependenciesGraph.Node,
    val outputClassesDirectory: File,
    val outputRClassesDirectory: File,
    val outputManifests: File,
    val outputResourcesDirectory: File?,
    val symbolTables: ImmutableList<SymbolTable>
) : Serializable {
    val inputClasses: ImmutableCollection<File>? =
        dependency.getFiles(ArtifactType.NON_NAMESPACED_CLASSES)
    val manifest: File? = dependency.getFile(ArtifactType.NON_NAMESPACED_MANIFEST)
    val resources: File? = dependency.getFile(ArtifactType.ANDROID_RES)
    val publicTxt: File? = dependency.getFile(ArtifactType.PUBLIC_RES)
    val dependencyName: String = dependency.toString()
    val dependencySanitizedName: String = dependency.sanitizedName
    val transitiveFiles: ImmutableList<File> =
        dependency.getTransitiveFiles(ArtifactType.DEFINED_ONLY_SYMBOL_LIST)

}