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

package com.android.build.gradle.internal.scope

import com.android.build.FilterData
import com.android.build.VariantOutput
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.forName
import com.android.build.gradle.internal.ide.FilterDataImpl
import com.google.common.collect.ImmutableList
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.nio.file.Path

/**
 * Factory for {@link BuildElements} that can load its content from save metadata file (.json)
 */
class ExistingBuildElements {

    companion object {

        private const val METADATA_FILE_NAME = "output.json"

        /**
         * create a [BuildElements] from an existing [Directory].
         * @param artifactType the expected element type of the BuildElements.
         * @param directoryProvider the directory containing the metadata file.
         */
        @JvmStatic
        fun from(artifactType: ArtifactType, directoryProvider: Provider<Directory>): BuildElements {
            return from(artifactType, directoryProvider.get().asFile)
        }


        @JvmStatic
        fun from(artifactType: ArtifactType, buildableArtifact : BuildableArtifact) : BuildElements {
            val metadataFile = buildableArtifact.forName(METADATA_FILE_NAME)
            return loadFrom(artifactType, metadataFile)
        }

        /**
         * create a {@link BuildElement} from a previous task execution metadata file collection.
         * @param elementType the expected element type of the BuildElements.
         * @param from the file collection containing the metadata file.
         */
        @JvmStatic
        fun from(elementType: ArtifactType, from: FileCollection): BuildElements {
            val metadataFile = getMetadataFileIfPresent(from)
            return loadFrom(elementType, metadataFile)
        }

        /**
         * create a {@link BuildElement} from a previous task execution metadata file.
         * @param elementType the expected element type of the BuildElements.
         * @param from the folder containing the metadata file.
         */
        @JvmStatic
        fun from(elementType: ArtifactType, from: File): BuildElements {

            val metadataFile = getMetadataFileIfPresent(from)
            return loadFrom(elementType, metadataFile)
        }

        /**
         * create a {@link BuildElement} containing all artifact types from a previous task
         * execution metadata file.
         * @param from the folder containing the metadata file.
         */
        @JvmStatic
        fun from(from: File): BuildElements {

            val metadataFile = getMetadataFileIfPresent(from)
            return loadFrom(null, metadataFile)
        }

        private fun loadFrom(
            elementType: ArtifactType?,
                metadataFile: File?): BuildElements {
            if (metadataFile == null || !metadataFile.exists()) {
                return BuildElements(ImmutableList.of())
            }
            try {
                FileReader(metadataFile).use { reader ->
                    return BuildElements(load(metadataFile.parentFile.toPath(),
                        elementType,
                        reader))
                }
            } catch (e: IOException) {
                return BuildElements(ImmutableList.of<BuildOutput>())
            }
        }

        private fun getMetadataFileIfPresent(fileCollection: FileCollection): File? {
            return fileCollection.asFileTree.files.find { it.name == METADATA_FILE_NAME }
        }

        @JvmStatic
        fun getMetadataFileIfPresent(folder: File): File? {
            val outputFile = getMetadataFile(folder)
            return if (outputFile.exists()) outputFile else null
        }

        @JvmStatic
        fun getMetadataFile(folder: File): File {
            return File(folder, METADATA_FILE_NAME)
        }

        @JvmStatic
        fun persistApkList(apkDatas: Collection<ApkData>): String {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeHierarchyAdapter(ApkData::class.java, ApkDataAdapter())
            val gson = gsonBuilder.create()
            return gson.toJson(apkDatas)
        }

        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun loadApkList(file: File): Collection<ApkData> {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeHierarchyAdapter(ApkData::class.java, ApkDataAdapter())
            gsonBuilder.registerTypeAdapter(
                    ArtifactType::class.java,
                    OutputTypeTypeAdapter())
            val gson = gsonBuilder.create()
            val recordType = object : TypeToken<List<ApkData>>() {}.type
            return gson.fromJson(FileReader(file), recordType)
        }

        @JvmStatic
        fun load(
                projectPath: Path,
                outputType: ArtifactType?,
                reader: Reader): Collection<BuildOutput> {
            val gsonBuilder = GsonBuilder()

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
                    .filter { outputType == null || it.type == outputType }
                    .map { buildOutput ->
                        BuildOutput(
                                buildOutput.type,
                                buildOutput.apkData,
                                projectPath.resolve(buildOutput.outputPath),
                                buildOutput.properties)
                    }
                    .toList()
        }
    }

    internal class ApkDataAdapter: TypeAdapter<ApkData>() {

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: ApkData?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.beginObject()
            out.name("type").value(value.type.toString())
            out.name("splits").beginArray()
            for (filter in value.filters) {
                out.beginObject()
                out.name("filterType").value(filter.filterType)
                out.name("value").value(filter.identifier)
                out.endObject()
            }
            out.endArray()
            out.name("versionCode").value(value.versionCode.toLong())
            if (value.versionName != null) {
                out.name("versionName").value(value.versionName)
            }
            out.name("enabled").value(value.isEnabled)
            if (value.filterName != null) {
                out.name("filterName").value(value.filterName)
            }
            if (value.outputFileName != null) {
                out.name("outputFile").value(value.outputFileName)
            }
            out.name("fullName").value(value.fullName)
            out.name("baseName").value(value.baseName)
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(reader: JsonReader): ApkData {
            reader.beginObject()
            var outputType: String? = null
            val filters = ImmutableList.builder<FilterData>()
            var versionCode = 0
            var versionName: String? = null
            var enabled = true
            var outputFile: String? = null
            var fullName: String? = null
            var baseName: String? = null
            var filterName: String? = null

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> outputType = reader.nextString()
                    "splits" -> readFilters(reader, filters)
                    "versionCode" -> versionCode = reader.nextInt()
                    "versionName" -> versionName = reader.nextString()
                    "enabled" -> enabled = reader.nextBoolean()
                    "outputFile" -> outputFile = reader.nextString()
                    "filterName" -> filterName = reader.nextString()
                    "baseName" -> baseName = reader.nextString()
                    "fullName" -> fullName = reader.nextString()
                }
            }
            reader.endObject()

            val filterData = filters.build()
            val apkType = VariantOutput.OutputType.valueOf(outputType!!)

            return ApkData.of(
                    apkType,
                    filterData,
                    versionCode,
                    versionName,
                    filterName,
                    outputFile,
                    fullName ?: "",
                    baseName ?: "",
                    enabled)
        }

        @Throws(IOException::class)
        private fun readFilters(reader: JsonReader, filters: ImmutableList.Builder<FilterData>) {

            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var filterType: VariantOutput.FilterType? = null
                var value: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "filterType" -> filterType = VariantOutput.FilterType.valueOf(reader.nextString())
                        "value" -> value = reader.nextString()
                    }
                }
                if (filterType != null && value != null) {
                    filters.add(FilterDataImpl(filterType, value))
                }
                reader.endObject()
            }
            reader.endArray()
        }
    }

    internal class OutputTypeTypeAdapter : TypeAdapter<ArtifactType>() {

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: ArtifactType) {
            out.beginObject()
            out.name("type").value(value.name())
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(reader: JsonReader): ArtifactType {
            reader.beginObject()
            if (!reader.nextName().endsWith("type")) {
                throw IOException("Invalid format")
            }
            val nextString = reader.nextString()
            val outputType: ArtifactType = try {
                InternalArtifactType.valueOf(nextString)
            } catch (e: IllegalArgumentException) {
                AnchorOutputType.valueOf(nextString)
            }

            reader.endObject()
            return outputType
        }
    }
}