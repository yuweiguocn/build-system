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
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_DEX
import com.android.builder.dexing.DexSplitterTool
import com.android.builder.packaging.JarMerger.MODULE_PATH
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import java.io.File
import java.nio.file.Files
import java.util.jar.JarFile

/**
 * Transform that splits dex files depending on their feature sources
 */
class DexSplitterTransform(
        private val outputDir: File,
        private val featureJars: FileCollection,
        private val baseJars: BuildableArtifact,
        private val mappingFileSrc: BuildableArtifact?
) :
        Transform() {

    override fun getName(): String = "dexSplitter"

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> = CONTENT_DEX

    override fun getOutputTypes(): MutableSet<QualifiedContent.ContentType> = CONTENT_DEX

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> =
            TransformManager.SCOPE_FULL_WITH_IR_AND_FEATURES

    override fun isIncremental(): Boolean = false

    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> {
        val secondaryFiles: MutableCollection<SecondaryFile> = mutableListOf()
        secondaryFiles.add(SecondaryFile.nonIncremental(featureJars))
        secondaryFiles.add(SecondaryFile.nonIncremental(baseJars))
        mappingFileSrc?.let { secondaryFiles.add(SecondaryFile.nonIncremental(it)) }
        return secondaryFiles
    }

    override fun getSecondaryDirectoryOutputs(): MutableCollection<File> {
        return mutableListOf(outputDir)
    }

    override fun transform(transformInvocation: TransformInvocation) {

        try {
            val mappingFile =
                if (mappingFileSrc?.singleFile()?.exists() == true
                    && !mappingFileSrc.singleFile().isDirectory) {
                mappingFileSrc.singleFile()
            } else {
                null
            }

            val outputProvider = requireNotNull(
                transformInvocation.outputProvider,
                { "No output provider set" }
            )
            outputProvider.deleteAll()
            FileUtils.deleteRecursivelyIfExists(outputDir)

            val builder = DexSplitterTool.Builder(outputDir.toPath(), mappingFile?.toPath())

            for (dirInput in TransformInputUtil.getDirectories(transformInvocation.inputs)) {
                dirInput.listFiles()?.toList()?.map { it.toPath() }?.forEach { builder.addInputArchive(it) }
            }

            featureJars.files.forEach { file ->
                builder.addFeatureJar(file.toPath(), file.nameWithoutExtension)
                Files.createDirectories(File(outputDir, file.nameWithoutExtension).toPath())
            }

            baseJars.files.forEach { builder.addBaseJar(it.toPath()) }

            builder.build().run()

            val transformOutputDir =
                outputProvider.getContentLocation(
                    "splitDexFiles", outputTypes, scopes, Format.DIRECTORY
                )
            Files.createDirectories(transformOutputDir.toPath())

            outputDir.listFiles().find { it.name == "base" }?.let {
                FileUtils.copyDirectory(it, transformOutputDir)
                FileUtils.deleteRecursivelyIfExists(it)
            }
        } catch (e: Exception) {
            throw TransformException(e)
        }
    }
}
