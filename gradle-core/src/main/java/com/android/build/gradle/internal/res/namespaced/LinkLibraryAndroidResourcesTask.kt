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

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Task to link the resources in a library project into an AAPT2 static library.
 */
@CacheableTask
open class LinkLibraryAndroidResourcesTask @Inject constructor(workerExecutor: WorkerExecutor) :
        AndroidBuilderTask() {

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var manifestFile: BuildableArtifact private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var inputResourcesDirectories: BuildableArtifact private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var libraryDependencies: FileCollection private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    var convertedLibraryDependencies: BuildableArtifact? = null
        private set

    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var sharedLibraryDependencies: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional var tested: BuildableArtifact? = null; private set

    @get:Internal lateinit var packageForRSupplier: Supplier<String> private set
    @Input fun getPackageForR() = packageForRSupplier.get()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection private set

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:OutputFile lateinit var staticLibApk: File private set

    private val workers = Workers.getWorker(workerExecutor)

    @TaskAction
    fun taskAction() {

        val imports = ImmutableList.builder<File>()
        // Link against library dependencies
        imports.addAll(libraryDependencies.files)
        convertedLibraryDependencies?.let {
            it.singleFile().listFiles().forEach { imports.add(it) }
        }
        imports.addAll(sharedLibraryDependencies.files)

        val request = AaptPackageConfig(
                androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR),
                manifestFile = manifestFile.single(),
                options = AaptOptions(null, false, null),
                resourceDirs = ImmutableList.copyOf(inputResourcesDirectories.asIterable()),
                staticLibrary = true,
                imports = imports.build(),
                resourceOutputApk = staticLibApk,
                variantType = VariantTypeImpl.LIBRARY,
                customPackageForR = getPackageForR(),
                intermediateDir = aaptIntermediateDir)

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven,
            logger = iLogger
        )
        workers.use {
            it.submit(Aapt2LinkRunnable::class.java,
                Aapt2LinkRunnable.Params(aapt2ServiceKey, request))
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<LinkLibraryAndroidResourcesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("link", "Resources")
        override val type: Class<LinkLibraryAndroidResourcesTask>
            get() = LinkLibraryAndroidResourcesTask::class.java

        private lateinit var staticLibApk: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            staticLibApk = variantScope.artifacts.appendArtifact(
                InternalArtifactType.RES_STATIC_LIBRARY,
                taskName,
                "res.apk")

        }

        override fun configure(task: LinkLibraryAndroidResourcesTask) {
            super.configure(task)

            task.manifestFile = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.STATIC_LIBRARY_MANIFEST)
            task.inputResourcesDirectories = variantScope.artifacts
                .getFinalArtifactFiles(InternalArtifactType.RES_COMPILED_FLAT_FILES)
            task.libraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            if (variantScope.artifacts.hasArtifact(InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES)) {
                task.convertedLibraryDependencies = variantScope.artifacts.getArtifactFiles(
                    InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES
                )
            }
            task.sharedLibraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            val testedScope = variantScope.testedVariantData?.scope
            if (testedScope != null) {
                task.tested = testedScope.artifacts.getFinalArtifactFiles(
                    InternalArtifactType.RES_STATIC_LIBRARY)
            }

            task.aaptIntermediateDir =
                    FileUtils.join(
                            variantScope.globalScope.intermediatesDir, "res-link-intermediate", variantScope.variantConfiguration.dirName)
            task.staticLibApk = staticLibApk
            task.setAndroidBuilder(variantScope.globalScope.androidBuilder)
            task.packageForRSupplier = Suppliers.memoize(variantScope.variantConfiguration::getOriginalApplicationId)
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
        }
    }

}
