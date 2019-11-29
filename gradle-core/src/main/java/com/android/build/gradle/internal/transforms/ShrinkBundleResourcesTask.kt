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

package com.android.build.gradle.internal.transforms

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import com.android.build.gradle.internal.scope.ApkData
import com.android.utils.FileUtils
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * Task to shrink resources for the android app bundle
 */
open class ShrinkBundleResourcesTask : AndroidVariantTask() {

    @get:OutputFile
    lateinit var compressedResourceFile: File
        private set


    @get:InputFiles
    lateinit var uncompressedResources: BuildableArtifact
        private set

    @get:InputFiles
    lateinit var dex: FileCollection
        private set

    @get:InputFiles
    lateinit var sourceDir: BuildableArtifact
        private set

    @get:InputFiles
    lateinit var resourceDir: BuildableArtifact
        private set

    @get:InputFiles
    @get:Optional
    var mappingFileSrc: BuildableArtifact? = null
        private set

    @get:InputFiles
    lateinit var mergedManifests: BuildableArtifact
        private set

    private lateinit var mainSplit: ApkData

    @TaskAction
    fun shrink() {
        val uncompressedResourceFile = uncompressedResources.singleFile()

        val classes = dex.files

        var reportFile: File? = null
        val mappingFile = mappingFileSrc?.singleFile()
        if (mappingFile != null) {
            val logDir = mappingFile.parentFile
            if (logDir != null) {
                reportFile = File(logDir, "resources.txt")
            }
        }

        FileUtils.mkdirs(compressedResourceFile.parentFile)

        val manifestFile = ExistingBuildElements.from(InternalArtifactType.BUNDLE_MANIFEST, mergedManifests)
            .element(mainSplit)
            ?.outputFile
                ?: throw RuntimeException("Cannot find merged manifest file")


        // Analyze resources and usages and strip out unused
        val analyzer = ResourceUsageAnalyzer(
            Iterables.getOnlyElement(sourceDir.files),
            classes,
            manifestFile,
            mappingFile,
            resourceDir.singleFile(),
            reportFile,
            ResourceUsageAnalyzer.ApkFormat.PROTO
        )
        try {
            analyzer.isVerbose = logger.isEnabled(LogLevel.INFO)
            analyzer.isDebug = logger.isEnabled(LogLevel.DEBUG)
            try {
                analyzer.analyze()
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: ParserConfigurationException) {
                throw RuntimeException(e)
            } catch (e: SAXException) {
                throw RuntimeException(e)
            }

            // Just rewrite the .ap_ file to strip out the res/ files for unused resources
            analyzer.rewriteResourceZip(uncompressedResourceFile, compressedResourceFile)

            // Dump some stats
            val unused = analyzer.unusedResourceCount
            if (unused > 0) {
                val sb = StringBuilder(200)
                sb.append("Removed unused resources")

                // This is a bit misleading until we can strip out all resource types:
                //int total = analyzer.getTotalResourceCount()
                //sb.append("(" + unused + "/" + total + ")")

                val before = uncompressedResourceFile.length()
                val after = compressedResourceFile.length()
                val percent = ((before - after) * 100 / before).toInt().toLong()
                sb.append(": Binary resource data reduced from ").append(toKbString(before))
                    .append("KB to ").append(toKbString(after)).append("KB: Removed ")
                    .append(percent).append("%")

                println(sb.toString())
            }
        } finally {
            analyzer.dispose()
        }
    }

    companion object {
        private fun toKbString(size: Long): String {
            return Integer.toString(size.toInt() / 1024)
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ShrinkBundleResourcesTask>(variantScope) {

        override val name: String = variantScope.getTaskName("shrink", "Resources")
        override val type: Class<ShrinkBundleResourcesTask>
            get() = ShrinkBundleResourcesTask::class.java

        private lateinit var outputLocation: File

        override fun preConfigure(taskName: String) {
            outputLocation =
                    variantScope.artifacts
                        .appendArtifact(
                            InternalArtifactType.SHRUNK_LINKED_RES_FOR_BUNDLE,
                            taskName,
                            "shrunk-bundled-res.ap_"
                        )
        }

        override fun configure(task: ShrinkBundleResourcesTask) {
            super.configure(task)

            task.compressedResourceFile = outputLocation
            task.uncompressedResources = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.LINKED_RES_FOR_BUNDLE
            )
            task.mainSplit = variantScope.variantData.outputScope.mainSplit

            task.dex = variantScope.transformManager.getPipelineOutputAsFileCollection(StreamFilter.DEX)

            task.sourceDir = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES
            )
            task.resourceDir = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.MERGED_NOT_COMPILED_RES
            )
            task.mappingFileSrc =
                    if (variantScope.artifacts.hasArtifact(InternalArtifactType.APK_MAPPING))
                        variantScope
                            .artifacts
                            .getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)
                    else
                        null
            task.mergedManifests = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.BUNDLE_MANIFEST)
        }
    }
}
