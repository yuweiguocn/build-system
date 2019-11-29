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

/**
 * Utilities related to AAPT2 Daemon management.
 */
@file:JvmName("Aapt2MavenUtils")

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.StringOption
import com.android.builder.model.Version
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.ArtifactAttributes
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val TYPE_EXTRACTED_AAPT2_BINARY = "_internal-android-aapt2-binary"
private const val AAPT2_CONFIG_NAME = "_internal_aapt2_binary"

private object Aapt2Version {
    val BUILD_NUMBER: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Aapt2Version::class.java
            .getResourceAsStream("aapt2_version.properties")
            .buffered()
            .use { stream ->
                Properties().let { properties ->
                    properties.load(stream)
                    properties.getProperty("aapt2Version")
                }
            }
    }
}

/**
 * Returns a file collection, which will contain the directory with AAPT2 to be used.
 *
 * See [getAapt2FromMaven].
 *
 * Idempotent.
 */
fun getAapt2FromMaven(globalScope: GlobalScope): FileCollection {
    // Use custom AAPT2 if it was overridden.
    val customAapt2 = globalScope.projectOptions[StringOption.AAPT2_FROM_MAVEN_OVERRIDE]
    if (!Strings.isNullOrEmpty(customAapt2)) {
        if (!customAapt2!!.endsWith(SdkConstants.FN_AAPT2)) {
            error("Custom AAPT2 location does not point to an AAPT2 executable: $customAapt2")
        }
        return globalScope.project.files(File(customAapt2).parentFile)
    }
    return getAapt2FromMaven(globalScope.project)
}

@VisibleForTesting
fun getAapt2FromMaven(project: Project): FileCollection {
    val existingConfig = project.configurations.findByName(AAPT2_CONFIG_NAME)
    if (existingConfig != null) {
        return getArtifactCollection(existingConfig)
    }

    val config = project.configurations.create(AAPT2_CONFIG_NAME) {
        it.isVisible = false
        it.isTransitive = false
        it.isCanBeConsumed = false
        it.description = "The AAPT2 binary to use for processing resources."
    }
    // See tools/base/aapt2 for the classifiers to use.
    val classifier = when (SdkConstants.currentPlatform()) {
        SdkConstants.PLATFORM_WINDOWS -> "windows"
        SdkConstants.PLATFORM_DARWIN -> "osx"
        SdkConstants.PLATFORM_LINUX -> "linux"
        else -> error("Unknown platform '${System.getProperty("os.name")}'")
    }
    val version = "${Version.ANDROID_GRADLE_PLUGIN_VERSION}-${Aapt2Version.BUILD_NUMBER}"
    project.dependencies.add(
        config.name,
        mapOf<String, String>(
            "group" to "com.android.tools.build",
            "name" to "aapt2",
            "version" to version,
            "classifier" to classifier
        )
    )

    project.dependencies.registerTransform {
        it.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        it.to.attribute(
            ArtifactAttributes.ARTIFACT_FORMAT,
            TYPE_EXTRACTED_AAPT2_BINARY
        )
        it.artifactTransform(Aapt2Extractor::class.java)
    }

    return getArtifactCollection(config)
}

private fun getArtifactCollection(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                TYPE_EXTRACTED_AAPT2_BINARY
            )
        }
    }.artifacts.artifactFiles

class Aapt2Extractor @Inject constructor() : ArtifactTransform() {
    override fun transform(input: File): MutableList<File> {
        val outDir = outputDirectory.toPath().resolve(input.nameWithoutExtension)
        Files.createDirectories(outDir)
        ZipInputStream(input.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.isDirectory) {
                    continue
                }
                val destinationFile = outDir.resolve(entry.name)
                Files.createDirectories(destinationFile.parent)
                Files.newOutputStream(destinationFile).buffered().use { output ->
                    ByteStreams.copy(zipInputStream, output)
                }
                // Mark executable on linux.
                if (entry.name == SdkConstants.FN_AAPT2 &&
                    (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX || SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN)) {
                    val permissions = Files.getPosixFilePermissions(destinationFile)
                    Files.setPosixFilePermissions(
                        destinationFile,
                        Sets.union(permissions, setOf(PosixFilePermission.OWNER_EXECUTE))
                    )
                }
            }
        }
        return ImmutableList.of(outDir.toFile())
    }
}


