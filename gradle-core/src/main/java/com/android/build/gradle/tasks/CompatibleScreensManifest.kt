/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.scope.ApkData
import com.android.resources.Density
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildException
import java.io.File
import java.io.IOException

/**
 * Task to generate a manifest snippet that just contains a compatible-screens node with the given
 * density and the given list of screen sizes.
 */
@CacheableTask
open class CompatibleScreensManifest : AndroidVariantTask() {

    @get:Input
    lateinit var screenSizes: Set<String>
        internal set

    @get:OutputDirectory
    lateinit var outputFolder: File
        internal set

    lateinit var outputScope: OutputScope
        private set

    @get:Input
    val splits: List<ApkData>
        get() = outputScope.apkDatas

    @get:Input
    @get:Optional
    lateinit var minSdkVersion: Provider<String?> internal set

    @TaskAction
    @Throws(IOException::class)
    fun generateAll() {

        BuildElements(
                outputScope.apkDatas.mapNotNull { apkInfo ->
                        val generatedManifest = generate(apkInfo)
                        if (generatedManifest != null)
                            BuildOutput(COMPATIBLE_SCREEN_MANIFEST, apkInfo, generatedManifest)
                        else
                            null }
                        .toList())
            .save(outputFolder)
    }

    fun generate(apkData: ApkData): File? {
        val densityFilter = apkData.getFilter(VariantOutput.FilterType.DENSITY)
                ?: return null

        val content = StringBuilder()
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
            .append("    package=\"\${packageName}\">\n")
            .append("\n")
        if (minSdkVersion.isPresent) {
            content.append("    <uses-sdk android:minSdkVersion=\"")
                .append(minSdkVersion.get())
                .append("\"/>\n")
        }
        content.append("    <compatible-screens>\n")

        // convert unsupported values to numbers.
        val density = convert(densityFilter.identifier, Density.XXHIGH, Density.XXXHIGH)

        for (size in screenSizes) {
            content.append("        <screen android:screenSize=\"")
                .append(size)
                .append("\" " + "android:screenDensity=\"")
                .append(density).append("\" />\n")
        }

        content.append(
                "    </compatible-screens>\n" + "</manifest>"
        )

        val splitFolder = File(outputFolder, apkData.dirName)
        FileUtils.mkdirs(splitFolder)
        val manifestFile = File(splitFolder, SdkConstants.ANDROID_MANIFEST_XML)

        try {
            Files.asCharSink(manifestFile, Charsets.UTF_8).write(content.toString())
        } catch (e: IOException) {
            throw BuildException(e.message, e)
        }

        return manifestFile
    }

    private fun convert(density: String, vararg densitiesToConvert: Density): String {
        for (densityToConvert in densitiesToConvert) {
            if (densityToConvert.resourceValue == density) {
                return Integer.toString(densityToConvert.dpiValue)
            }
        }
        return density
    }

    class CreationAction(variantScope: VariantScope, private val screenSizes: Set<String>) :
        VariantTaskCreationAction<CompatibleScreensManifest>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("create", "CompatibleScreenManifests")
        override val type: Class<CompatibleScreensManifest>
            get() = CompatibleScreensManifest::class.java

        private lateinit var outputFolder: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outputFolder = variantScope.artifacts
                .appendArtifact(COMPATIBLE_SCREEN_MANIFEST, taskName)
        }

        override fun configure(task: CompatibleScreensManifest) {
            super.configure(task)

            task.outputScope = variantScope.outputScope
            task.screenSizes = screenSizes
            task.outputFolder = outputFolder

            val config = variantScope.variantConfiguration
            task.minSdkVersion = TaskInputHelper.memoizeToProvider(task.project) {
                val minSdk = config.mergedFlavor.minSdkVersion
                minSdk?.apiString
            }
        }
    }
}
