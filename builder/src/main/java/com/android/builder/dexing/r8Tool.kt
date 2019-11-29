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

@file:JvmName("R8Tool")

package com.android.builder.dexing

import com.android.SdkConstants
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.ide.common.blame.MessageReceiver
import com.android.tools.r8.ArchiveProgramResourceProvider
import com.android.tools.r8.ClassFileConsumer
import com.android.tools.r8.CompatProguardCommandBuilder
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.DataDirectoryResource
import com.android.tools.r8.DataEntryResource
import com.android.tools.r8.DataResourceConsumer
import com.android.tools.r8.DataResourceProvider
import com.android.tools.r8.DexIndexedConsumer
import com.android.tools.r8.ProgramResource
import com.android.tools.r8.ProgramResourceProvider
import com.android.tools.r8.R8
import com.android.tools.r8.StringConsumer
import com.android.tools.r8.Version
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.utils.ArchiveResourceProvider
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

fun isProguardRule(name: String): Boolean {
    val lowerCaseName = name.toLowerCase()
    return lowerCaseName.startsWith("meta-inf/proguard/")
            || lowerCaseName.startsWith("/meta-inf/proguard/")
}

fun getR8Version(): String = Version.getVersionString()

/**
 * Converts the specified inputs, according to the configuration, and writes dex or classes to
 * output path.
 */
fun runR8(
    inputClasses: Collection<Path>,
    output: Path,
    inputJavaResources: Collection<Path>,
    javaResourcesJar: Path,
    libraries: Collection<Path>,
    toolConfig: ToolConfig,
    proguardConfig: ProguardConfig,
    mainDexListConfig: MainDexListConfig,
    messageReceiver: MessageReceiver,
    useFullR8: Boolean = false
) {
    val logger: Logger = Logger.getLogger("R8")
    if (logger.isLoggable(Level.FINE)) {
        logger.fine("*** Using R8 to process code ***")
        logger.fine("Main dex list config: $mainDexListConfig")
        logger.fine("Proguard config: $proguardConfig")
        logger.fine("Tool config: $toolConfig")
        logger.fine("Program classes: $inputClasses")
        logger.fine("Java resources: $inputJavaResources")
        logger.fine("Library classes: $libraries")
    }
    val r8CommandBuilder = CompatProguardCommandBuilder(!useFullR8, D8DiagnosticsHandler(messageReceiver))

    if (toolConfig.r8OutputType == R8OutputType.DEX) {
        r8CommandBuilder.minApiLevel = toolConfig.minSdkVersion
        if (toolConfig.minSdkVersion < 21) {
            // specify main dex related options only when minSdkVersion is below 21
            r8CommandBuilder
                .addMainDexRulesFiles(mainDexListConfig.mainDexRulesFiles)
                .addMainDexListFiles(mainDexListConfig.mainDexListFiles)

            if (mainDexListConfig.mainDexRules.isNotEmpty()) {
                r8CommandBuilder.addMainDexRules(mainDexListConfig.mainDexRules, Origin.unknown())
            }
            mainDexListConfig.mainDexListOutput?.let {
                r8CommandBuilder.setMainDexListConsumer(StringConsumer.FileConsumer(it))
            }
        }
    }

    r8CommandBuilder
        .addProguardConfigurationFiles(
            proguardConfig.proguardConfigurationFiles.filter { Files.isRegularFile(it) }
        )
        .addProguardConfiguration(proguardConfig.proguardConfigurations, Origin.unknown())

    if (proguardConfig.proguardMapInput != null
        && Files.exists(proguardConfig.proguardMapInput)
    ) {
        r8CommandBuilder.addProguardConfiguration(
            listOf("-applymapping \"${proguardConfig.proguardMapInput}\""),
            Origin.unknown()
        )
    }

    if (proguardConfig.proguardMapOutput != null) {
        Files.deleteIfExists(proguardConfig.proguardMapOutput)
        Files.createDirectories(proguardConfig.proguardMapOutput.parent)
        r8CommandBuilder.setProguardMapOutputPath(proguardConfig.proguardMapOutput)
    }

    val compilationMode =
        if (toolConfig.isDebuggable) CompilationMode.DEBUG else CompilationMode.RELEASE

    val dataResourceConsumer = ClassFileConsumer.ArchiveConsumer(javaResourcesJar)
    val programConsumer =
        if (toolConfig.r8OutputType == R8OutputType.CLASSES) {
            val baseConsumer: ClassFileConsumer = if (Files.isDirectory(output)) {
                ClassFileConsumer.DirectoryConsumer(output)
            } else {
                ClassFileConsumer.ArchiveConsumer(output)
            }
            object : ClassFileConsumer.ForwardingConsumer(baseConsumer) {
                override fun getDataResourceConsumer(): DataResourceConsumer? {
                    return dataResourceConsumer
                }
            }
        } else {
            val baseConsumer: DexIndexedConsumer = if (Files.isDirectory(output)) {
                DexIndexedConsumer.DirectoryConsumer(output)
            } else {
                DexIndexedConsumer.ArchiveConsumer(output)
            }
            object : DexIndexedConsumer.ForwardingConsumer(baseConsumer) {
                override fun getDataResourceConsumer(): DataResourceConsumer? {
                    return dataResourceConsumer
                }
            }
        }

    @Suppress("UsePropertyAccessSyntax")
    r8CommandBuilder
        .setDisableMinification(toolConfig.disableMinification)
        .setDisableTreeShaking(toolConfig.disableTreeShaking)
        .setDisableDesugaring(toolConfig.disableDesugaring)
        .setMode(compilationMode)
        .setProgramConsumer(programConsumer)

    // Use this to control all resources provided to R8
    val r8ProgramResourceProvider = R8ProgramResourceProvider()

    for (path in inputClasses) {
        when {
            Files.isRegularFile(path) -> r8ProgramResourceProvider.addProgramResourceProvider(
                ArchiveProgramResourceProvider.fromArchive(path))
            Files.isDirectory(path) -> Files.walk(path).use {
                it.filter { Files.isRegularFile(it) && it.toString().endsWith(SdkConstants.DOT_CLASS) }
                    .forEach { r8CommandBuilder.addProgramFiles(it) }
            }
            else -> throw IOException("Unexpected file format: $path")
        }
    }

    val dirResources = inputJavaResources.filter {
        if (!Files.isDirectory(it)) {
            val resourceOnlyProvider =
                ResourceOnlyProvider(ArchiveResourceProvider.fromArchive(it, true))
            r8ProgramResourceProvider.dataResourceProviders.add(resourceOnlyProvider.dataResourceProvider)
            false
        } else {
            true
        }
    }

    r8ProgramResourceProvider.dataResourceProviders.add(R8DataResourceProvider(dirResources))

    r8CommandBuilder.addProgramResourceProvider(r8ProgramResourceProvider)

    ClassFileProviderFactory(libraries).use { libClasspath ->
        r8CommandBuilder.addLibraryResourceProvider(libClasspath.orderedProvider)
        R8.run(r8CommandBuilder.build())
    }

    proguardConfig.proguardMapOutput?.let {
        if (Files.notExists(it)) {
            // R8 might not create a mapping file, so we have to create it, http://b/37053758.
            Files.createFile(it)
        }
    }
}

enum class R8OutputType {
    DEX,
    CLASSES,
}

/** Main dex related parameters for the R8 tool. */
data class MainDexListConfig(
    val mainDexRulesFiles: Collection<Path> = listOf(),
    val mainDexListFiles: Collection<Path> = listOf(),
    val mainDexRules: List<String> = listOf(),
    val mainDexListOutput: Path? = null
)

/** Proguard-related parameters for the R8 tool. */
data class ProguardConfig(
    val proguardConfigurationFiles: List<Path>,
    val proguardMapOutput: Path?,
    val proguardMapInput: Path?,
    val proguardConfigurations: List<String>
)

/** Configuration parameters for the R8 tool. */
data class ToolConfig(
    val minSdkVersion: Int,
    val isDebuggable: Boolean,
    val disableTreeShaking: Boolean,
    val disableDesugaring: Boolean,
    val disableMinification: Boolean,
    val r8OutputType: R8OutputType
)

private class ProGuardRulesFilteringVisitor(
    private val visitor: DataResourceProvider.Visitor?
) : DataResourceProvider.Visitor {
    override fun visit(directory: DataDirectoryResource) {
        visitor?.visit(directory)
    }

    override fun visit(resource: DataEntryResource) {
        if (!isProguardRule(resource.getName())) {
            visitor?.visit(resource)
        }
    }
}

private class R8ProgramResourceProvider : ProgramResourceProvider {
    private val programResourcesList: MutableList<ProgramResource> = ArrayList()

    val dataResourceProviders: MutableList<DataResourceProvider> = ArrayList()

    fun addProgramResourceProvider(provider: ProgramResourceProvider) {
        programResourcesList.addAll(provider.programResources)
        provider.dataResourceProvider?.let {
            dataResourceProviders.add(it)
        }
    }

    override fun getProgramResources() = programResourcesList

    override fun getDataResourceProvider() = object : DataResourceProvider {
        override fun accept(visitor: DataResourceProvider.Visitor?) {
            val visitorWrapper = ProGuardRulesFilteringVisitor(visitor)
            for (provider in dataResourceProviders) {
                provider.accept(visitorWrapper)
            }
        }
    }
}

/** Provider that loads all resources from the specified directories.  */
private class R8DataResourceProvider(val dirResources: Collection<Path>) : DataResourceProvider {
    override fun accept(visitor: DataResourceProvider.Visitor?) {
        val seen = mutableSetOf<Path>()
        val logger: Logger = Logger.getLogger("R8")
        for (resourceBase in dirResources) {
            Files.walk(resourceBase).use {
                it.forEach {
                    val relative = resourceBase.relativize(it)
                    if (it != resourceBase
                        && !it.toString().endsWith(SdkConstants.DOT_CLASS)
                        && seen.add(relative)) {
                        when {
                            Files.isDirectory(it) -> visitor!!.visit(
                                DataDirectoryResource.fromFile(
                                    resourceBase, resourceBase.relativize(it)
                                )
                            )
                            else -> visitor!!.visit(
                                DataEntryResource.fromFile(
                                    resourceBase, resourceBase.relativize(it)
                                )
                            )
                        }
                    } else {
                        logger.fine { "Ignoring entry $relative from $resourceBase" }
                    }
                }
            }
        }
    }
}

private class ResourceOnlyProvider(val originalProvider: ProgramResourceProvider): ProgramResourceProvider {
    override fun getProgramResources() = listOf<ProgramResource>()

    override fun getDataResourceProvider() = originalProvider.getDataResourceProvider()
}