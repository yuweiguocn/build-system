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
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library
import com.android.tools.build.libraries.metadata.LibraryDependencies
import com.android.tools.build.libraries.metadata.ModuleDependencies
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.LinkedList

/**
 * Task that generates the final bundle dependencies, combining all the module dependencies.
 */
open class BundleReportDependenciesTask :
    AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var baseDeps: BuildableArtifact
        internal set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var featureDeps: FileCollection
        internal set

    @get:OutputFile
    lateinit var dependenciesList: File
        internal set

    @TaskAction
    fun writeFile() {

        val baseAppDeps =  BufferedInputStream(FileInputStream(baseDeps.singleFile())).use {
            AppDependencies.parseDelimitedFrom(it)
        }

        val featureAppDeps = LinkedList<AppDependencies>()

        featureDeps.files.forEach {
            featureAppDeps.add(AppDependencies.parseDelimitedFrom(FileInputStream(it)))
        }

        val libraryToIndexMap = HashMap<Library, Integer>()
        val libraryList = LinkedList(baseAppDeps.libraryList)
        for ((index, lib) in libraryList.withIndex()) {
            libraryToIndexMap.put(lib, Integer(index))
        }
        val libraryDeps = LinkedList(baseAppDeps.libraryDependenciesList)
        val moduleDeps = LinkedList(baseAppDeps.moduleDependenciesList)

        for (featureAppDep in featureAppDeps) {
            val libIndexDict = HashMap<Integer, Integer>()
            val featureLibraryDeps = featureAppDep.libraryList

            // update the library list indices
            for ((origIndex, lib) in featureLibraryDeps.withIndex()) {
                var newIndex = libraryToIndexMap.get(lib)
                if (newIndex == null) {
                    newIndex = Integer(libraryList.size)
                    libraryToIndexMap.put(lib, newIndex)
                    libraryList.add(lib)
                }
                libIndexDict.put(Integer(origIndex), newIndex)
            }
            // update the library dependencies list
            for(libraryDep in featureAppDep.libraryDependenciesList) {
                val transformedDepBuilder = LibraryDependencies.newBuilder()
                    .setLibraryIndex(libIndexDict[Integer(libraryDep.libraryIndex)]!!.toInt())
                for(depIndex in libraryDep.libraryDepIndexList) {
                    transformedDepBuilder
                        .addLibraryDepIndex(libIndexDict[Integer(depIndex)]!!.toInt())
                }
                val transformedDep = transformedDepBuilder.build()
                if (!libraryDeps.contains(transformedDep)) {
                    libraryDeps.add(transformedDep)
                }
            }
            // update the indices for the module dependencies
            for(moduleDep in featureAppDep.moduleDependenciesList) {
                val moduleDepBuilder = ModuleDependencies.newBuilder()
                    .setModuleName(moduleDep.moduleName)
                for(depIndex in moduleDep.dependencyIndexList) {
                    moduleDepBuilder
                        .addDependencyIndex(libIndexDict[Integer(depIndex)]!!.toInt())
                }
                moduleDeps.add(moduleDepBuilder.build())
            }

        }
        val appDeps = AppDependencies.newBuilder()
            .addAllLibrary(libraryList)
            .addAllLibraryDependencies(libraryDeps)
            .addAllModuleDependencies(moduleDeps)
            .build()

        appDeps.writeDelimitedTo(FileOutputStream(dependenciesList))
    }


    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<BundleReportDependenciesTask>(variantScope) {
        override val name: String = variantScope.getTaskName("configure", "Dependencies")
        override val type: Class<BundleReportDependenciesTask> = BundleReportDependenciesTask::class.java

        private lateinit var dependenciesList : Provider<RegularFile>
        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            dependenciesList = variantScope
                .artifacts
                .createArtifactFile(
                    InternalArtifactType.BUNDLE_DEPENDENCY_REPORT,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskName,
                    "dependencies.pb"
                )
        }

        override fun configure(task: BundleReportDependenciesTask) {
            super.configure(task)
            task.baseDeps = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.METADATA_LIBRARY_DEPENDENCIES_REPORT)
            task.featureDeps = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.LIB_DEPENDENCIES
            )
            task.dependenciesList = dependenciesList.get().asFile
        }
    }
}
