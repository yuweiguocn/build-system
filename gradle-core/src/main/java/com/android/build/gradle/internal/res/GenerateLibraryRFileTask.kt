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
package com.android.build.gradle.internal.res

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.symbols.processLibraryMainSymbolTable
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.parseResourceSourceSetDirectory
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException

@CacheableTask
open class GenerateLibraryRFileTask : ProcessAndroidResources() {

    @get:OutputDirectory @get:Optional var sourceOutputDirectory: File? = null; private set
    @Input fun outputSources() = sourceOutputDirectory != null

    @get:OutputFile @get:Optional var rClassOutputJar: File? = null; private set
    @Input fun outputRClassJar() = rClassOutputJar != null

    override fun getSourceOutputDir() = sourceOutputDirectory ?: rClassOutputJar

    @get:OutputFile lateinit var textSymbolOutputFile: File
        private set

    @get:OutputFile lateinit var symbolsWithPackageNameOutputFile: File
        private set

    @get:OutputFile
    @get:Optional
    var proguardOutputFile: File? = null
        private set

    @Suppress("unused")
    // Needed to trigger rebuild if proguard file is requested (https://issuetracker.google.com/67418335)
    @Input fun hasProguardOutputFile() = proguardOutputFile != null

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE) lateinit var dependencies: FileCollection
        private set

    @get:Input lateinit var packageForR: Provider<String> private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY) lateinit var platformAttrRTxt: FileCollection
        private set

    @get:Input lateinit var applicationId: Provider<String> private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var inputResourcesDir: BuildableArtifact
        private set

    @get:Input
    var namespacedRClass: Boolean = false
        private set

    @Throws(IOException::class)
    override fun doFullTaskAction() {
        val manifest = Iterables.getOnlyElement(
                ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, manifestFiles))
                .outputFile

        val androidAttrSymbol = getAndroidAttrSymbols(platformAttrRTxt.singleFile)

        val symbolTable = parseResourceSourceSetDirectory(
                inputResourcesDir.single(),
                IdProvider.sequential(),
                androidAttrSymbol)

        processLibraryMainSymbolTable(
                librarySymbols = symbolTable,
                libraries = this.dependencies.files,
                mainPackageName = packageForR.get(),
                manifestFile = manifest,
                sourceOut = sourceOutputDirectory,
                rClassOutputJar = rClassOutputJar,
                symbolFileOut = textSymbolOutputFile,
                proguardOut = proguardOutputFile,
                mergedResources = inputResourcesDir.single(),
                platformSymbols = androidAttrSymbol,
                namespacedRClass = namespacedRClass)

        SymbolIo.writeSymbolListWithPackageName(
                textSymbolOutputFile.toPath(),
                manifest.toPath(),
                symbolsWithPackageNameOutputFile.toPath())
    }

    private fun getAndroidAttrSymbols(androidJar: File) =
            if (androidJar.exists())
                SymbolIo.readFromAapt(androidJar, "android")
            else
                SymbolTable.builder().tablePackage("android").build()



    class CreationAction(
            variantScope: VariantScope,
            private val symbolFile: File,
            private val symbolsWithPackageNameOutputFile: File
    ) : VariantTaskCreationAction<GenerateLibraryRFileTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("generate", "RFile")
        override val type: Class<GenerateLibraryRFileTask>
            get() = GenerateLibraryRFileTask::class.java

        private lateinit var rClassOutputJar: File
        private lateinit var sourceOutputDirectory: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            if (variantScope.globalScope.projectOptions.get(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION)) {
                rClassOutputJar = variantScope.artifacts
                    .appendArtifact(InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR,
                        taskName,
                        "R.jar")
            } else {
                sourceOutputDirectory = variantScope.artifacts
                    .appendArtifact(InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES, taskName)
            }

            if (generatesProguardOutputFile(variantScope)) {
                variantScope
                    .artifacts
                    .appendArtifact(
                        InternalArtifactType.AAPT_PROGUARD_FILE,
                        listOf(variantScope.processAndroidResourcesProguardOutputFile),
                        taskName)
            }

        }

        override fun handleProvider(taskProvider: TaskProvider<out GenerateLibraryRFileTask>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.processAndroidResTask = taskProvider
        }

        override fun configure(task: GenerateLibraryRFileTask) {
            super.configure(task)

            task.platformAttrRTxt = variantScope.globalScope.platformAttrs

            task.applicationId = TaskInputHelper.memoizeToProvider(task.project) {
                variantScope.variantData.variantConfiguration.applicationId
            }

            task.dependencies = variantScope.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
            if (variantScope.globalScope.projectOptions.get(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION)) {
                task.rClassOutputJar = rClassOutputJar
            } else {
                task.sourceOutputDirectory = sourceOutputDirectory
            }
            task.textSymbolOutputFile = symbolFile
            task.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile

            if (generatesProguardOutputFile(variantScope)) {
                task.proguardOutputFile = variantScope.processAndroidResourcesProguardOutputFile
            }

            task.packageForR = TaskInputHelper.memoizeToProvider(task.project) {
                Strings.nullToEmpty(variantScope.variantConfiguration.originalApplicationId)
            }

            task.manifestFiles = variantScope.artifacts.getFinalProduct(
                InternalArtifactType.MERGED_MANIFESTS)

            task.inputResourcesDir = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.PACKAGED_RES)

            task.namespacedRClass = variantScope.globalScope.projectOptions[BooleanOption.NAMESPACED_R_CLASS]

            task.outputScope = variantScope.outputScope
        }
    }
}
