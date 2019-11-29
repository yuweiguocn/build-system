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

package com.android.build.gradle.internal.ide

import com.android.build.FilterData
import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.build.api.artifact.ArtifactType
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.collect.ImmutableList
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.nio.file.Path

/**
 * Temporary class to load enough metadata to populate early model. should be deleted once
 * IDE only relies on minimalistic after build model.
 */
data class EarlySyncBuildOutput(
        val type: ArtifactType,
        val apkType: VariantOutput.OutputType,
        val filtersData: Collection<FilterData>,
        val version: Int,
        val output: File) : java.io.Serializable, OutputFile {

    override fun getOutputFile(): File = output
    override fun getOutputType(): String = apkType.name

    override fun getFilterTypes(): Collection<String> =
            filtersData.asSequence()
                    .map { it.filterType }
                    .toList()

    override fun getFilters(): Collection<FilterData> = filtersData
    override fun getMainOutputFile(): OutputFile = this
    @Suppress("OverridingDeprecatedMember")
    override fun getOutputs(): MutableCollection<out OutputFile> = ImmutableList.of<OutputFile>(this)
    override fun getVersionCode(): Int = version
    fun getFilter(filterType: String): String? =
            filtersData.asSequence().find { it.filterType == filterType }?.identifier

    companion object {
        @JvmStatic
        fun load(folder: File): Collection<EarlySyncBuildOutput> {
            val metadataFile = ExistingBuildElements.getMetadataFileIfPresent(folder)
            if (metadataFile == null || !metadataFile.exists()) {
                return ImmutableList.of<EarlySyncBuildOutput>()
            }

            return try {
                FileReader(metadataFile).use { reader: FileReader ->
                    load(metadataFile.parentFile.toPath(), reader)
                }
            } catch (e: IOException) {
                ImmutableList.of<EarlySyncBuildOutput>()
            }
        }

        private fun load(
                projectPath: Path,
                reader: Reader): Collection<EarlySyncBuildOutput> {
            val gsonBuilder = GsonBuilder()

            // TODO : remove use of ApkInfo and replace with EarlySyncApkInfo.
            gsonBuilder.registerTypeAdapter(ApkData::class.java, ApkDataAdapter())
            gsonBuilder.registerTypeAdapter(
                    ArtifactType::class.java,
                    OutputTypeTypeAdapter())
            val gson = gsonBuilder.create()
            val recordType = object : TypeToken<List<BuildOutput>>() {}.type
            val buildOutputs = gson.fromJson<Collection<BuildOutput>>(reader, recordType)
            // resolve the file path to the current project location.
            return buildOutputs
                    .asSequence()
                    .map { buildOutput ->
                        EarlySyncBuildOutput(
                                buildOutput.type,
                                buildOutput.apkData.type,
                                buildOutput.apkData.filters,
                                buildOutput.apkData.versionCode,
                                projectPath.resolve(buildOutput.outputPath).toFile())
                    }
                    .toList()
        }

        internal class ApkDataAdapter : TypeAdapter<ApkData>() {

            @Throws(IOException::class)
            override fun write(out: JsonWriter, value: ApkData?) {
                throw IOException("Unexpected call to write")
            }

            @Throws(IOException::class)
            override fun read(`in`: JsonReader): ApkData {
                `in`.beginObject()
                var outputType: String? = null
                val filters = ImmutableList.builder<FilterData>()
                var versionCode = 0

                while (`in`.hasNext()) {
                    when (`in`.nextName()) {
                        "type" -> outputType = `in`.nextString()
                        "splits" -> readFilters(`in`, filters)
                        "versionCode" -> versionCode = `in`.nextInt()
                        "enabled" -> `in`.nextBoolean()
                        else -> `in`.nextString()
                    }
                }
                `in`.endObject()

                if (outputType == null) {
                    throw IOException("invalid format ")
                }
                return ApkData.of(
                    VariantOutput.OutputType.valueOf(outputType),
                    filters.build(),
                    versionCode
                )
            }

            @Throws(IOException::class)
            private fun readFilters(`in`: JsonReader,
                    filters: ImmutableList.Builder<FilterData>) {

                `in`.beginArray()
                while (`in`.hasNext()) {
                    `in`.beginObject()
                    var filterType: VariantOutput.FilterType? = null
                    var value: String? = null
                    while (`in`.hasNext()) {
                        when (`in`.nextName()) {
                            "filterType" -> filterType = VariantOutput.FilterType.valueOf(`in`.nextString())
                            "value" -> value = `in`.nextString()
                        }
                    }
                    if (filterType != null && value != null) {
                        filters.add(FilterDataImpl(filterType, value))
                    }
                    `in`.endObject()
                }
                `in`.endArray()
            }
        }

        internal class OutputTypeTypeAdapter : TypeAdapter<ArtifactType>() {
            override fun write(out: JsonWriter?, value: ArtifactType?) {
                throw IOException("Unexpected call to write")
            }

            @Throws(IOException::class)
            override fun read(`in`: JsonReader): ArtifactType {
                `in`.beginObject()
                if (!`in`.nextName().endsWith("type")) {
                    throw IOException("Invalid format")
                }
                val nextString = `in`.nextString()
                val outputType: ArtifactType = try {
                    InternalArtifactType.valueOf(nextString)
                } catch (e: IllegalArgumentException) {
                    AnchorOutputType.valueOf(nextString)
                }
                `in`.endObject()
                return outputType
            }
        }
    }
}
