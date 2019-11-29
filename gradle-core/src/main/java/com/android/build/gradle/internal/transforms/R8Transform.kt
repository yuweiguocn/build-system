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

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_DEX_WITH_RESOURCES
import com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_JARS
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.TransformInputUtil.getAllFiles
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.MainDexListConfig
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.ToolConfig
import com.android.builder.dexing.getR8Version
import com.android.builder.dexing.runR8
import com.android.ide.common.blame.MessageReceiver
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transform that uses R8 to convert class files to dex. In case of a library variant, this
 * transform outputs class files.
 *
 * R8 transforms inputs are: program class files, library class files (e.g. android.jar), Proguard
 * configuration files, main dex list configuration files, other tool-specific parameters. Output
 * is dex or class files, depending on whether we are building an APK, or AAR.
 */
class R8Transform(
    private val bootClasspath: Lazy<List<File>>,
    private val minSdkVersion: Int,
    private val isDebuggable: Boolean,
    private val java8Support: VariantScope.Java8LangSupport,
    private var disableTreeShaking: Boolean,
    private var disableMinification: Boolean,
    private val mainDexListFiles: FileCollection,
    private val mainDexRulesFiles: FileCollection,
    private val inputProguardMapping: FileCollection,
    private val outputProguardMapping: File,
    proguardConfigurationFiles: ConfigurableFileCollection,
    variantType: VariantType,
    includeFeaturesInScopes: Boolean,
    private val messageReceiver: MessageReceiver,
    private val dexingType: DexingType,
    private val useFullR8: Boolean = false
) :
        ProguardConfigurable(proguardConfigurationFiles, variantType, includeFeaturesInScopes) {

    // This is a huge sledgehammer, but it is necessary until http://b/72683872 is fixed.
    private val proguardConfigurations: MutableList<String> = mutableListOf("-ignorewarnings")

    var mainDexListOutput: File? = null

    constructor(
        scope: VariantScope,
        mainDexListFiles: FileCollection,
        mainDexRulesFiles: FileCollection,
        inputProguardMapping: FileCollection,
        outputProguardMapping: File
    ) :
            this(
                lazy { scope.globalScope.androidBuilder.getBootClasspath(true) },
                scope.minSdkVersion.featureLevel,
                scope.variantConfiguration.buildType.isDebuggable,
                scope.java8LangSupportType,
                false,
                false,
                mainDexListFiles,
                mainDexRulesFiles,
                inputProguardMapping,
                outputProguardMapping,
                scope.globalScope.project.files(),
                scope.variantData.type,
                scope.consumesFeatureJars(),
                scope.globalScope.messageReceiver,
                scope.dexingType,
                scope.globalScope.projectOptions[BooleanOption.FULL_R8]
            )

    override fun getName(): String = "r8"

    override fun getInputTypes(): MutableSet<out QualifiedContent.ContentType> = CONTENT_JARS

    override fun getOutputTypes(): MutableSet<out QualifiedContent.ContentType> {
        return if (variantType.isAar) {
            CONTENT_JARS
        } else {
            CONTENT_DEX_WITH_RESOURCES
        }
    }

    override fun isIncremental(): Boolean = false

    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> =
        mutableListOf(
                SecondaryFile.nonIncremental(allConfigurationFiles),
                SecondaryFile.nonIncremental(mainDexListFiles),
                SecondaryFile.nonIncremental(mainDexRulesFiles),
                SecondaryFile.nonIncremental(inputProguardMapping)
        )

    override fun getParameterInputs(): MutableMap<String, Any> =
        mutableMapOf(
                "minSdkVersion" to minSdkVersion,
                "isDebuggable" to isDebuggable,
                "disableTreeShaking" to disableTreeShaking,
                "java8Support" to (java8Support == VariantScope.Java8LangSupport.R8),
                "disableMinification" to disableMinification,
                "proguardConfiguration" to proguardConfigurations,
                "fullMode" to useFullR8,
                "dexingType" to dexingType
        )

    override fun getSecondaryFileOutputs(): MutableCollection<File> =
        listOfNotNull(outputProguardMapping, mainDexListOutput).toMutableList()

    override fun keep(keep: String) {
        proguardConfigurations.add("-keep $keep")
    }

    override fun keepattributes() {
        proguardConfigurations.add("-keepattributes *")
    }

    override fun dontwarn(dontwarn: String) {
        proguardConfigurations.add("-dontwarn $dontwarn")
    }

    override fun setActions(actions: PostprocessingFeatures) {
        disableTreeShaking = !actions.isRemoveUnusedCode
        disableMinification = !actions.isObfuscate
        if (!actions.isOptimize) {
            proguardConfigurations.add("-dontoptimize")
        }
    }

    override fun transform(transformInvocation: TransformInvocation) {
        LoggerWrapper.getLogger(R8Transform::class.java)
            .lifecycle(
                """
                |R8 is the new Android code shrinker. If you experience any issues, please file a bug at
                |https://issuetracker.google.com, using 'Shrinker (R8)' as component name. You can
                |disable R8 by updating gradle.properties with 'android.enableR8=false'.
                |Current version is: ${getR8Version()}.
                |""".trimMargin()
            )

        val outputProvider = requireNotNull(
                transformInvocation.outputProvider,
                { "No output provider set" }
        )
        outputProvider.deleteAll()

        val r8OutputType: R8OutputType
        val outputFormat: Format
        if (variantType.isAar) {
            r8OutputType = R8OutputType.CLASSES
            outputFormat = Format.JAR
        } else {
            r8OutputType = R8OutputType.DEX
            outputFormat = Format.DIRECTORY
        }
        val enableDesugaring = java8Support == VariantScope.Java8LangSupport.R8
                && r8OutputType == R8OutputType.DEX
        val toolConfig = ToolConfig(
            minSdkVersion = minSdkVersion,
            isDebuggable = isDebuggable,
            disableTreeShaking = disableTreeShaking,
            disableDesugaring = !enableDesugaring,
            disableMinification = disableMinification,
            r8OutputType = r8OutputType
        )

        val proguardMappingInput =
                if (inputProguardMapping.isEmpty) null else inputProguardMapping.singleFile.toPath()
        val proguardConfig = ProguardConfig(
                allConfigurationFiles.files.map { it.toPath() },
                outputProguardMapping.toPath(),
                proguardMappingInput,
                proguardConfigurations
        )

        val mainDexListConfig = if (dexingType == DexingType.LEGACY_MULTIDEX) {
            MainDexListConfig(
                mainDexRulesFiles.files.map { it.toPath() },
                mainDexListFiles.files.map { it.toPath() },
                getPlatformRules(),
                mainDexListOutput?.toPath()
            )
        } else {
            MainDexListConfig()
        }

        val output = outputProvider.getContentLocation(
                "main",
                if (variantType.isAar) TransformManager.CONTENT_CLASS else TransformManager.CONTENT_DEX ,
                scopes,
                outputFormat
        )

        when (outputFormat) {
            Format.JAR -> Files.createDirectories(output.parentFile.toPath())
            Format.DIRECTORY -> Files.createDirectories(output.toPath())
        }

        val inputJavaResources = mutableListOf<Path>()
        val inputClasses = mutableListOf<Path>()
        transformInvocation.inputs.forEach {
            it.directoryInputs.forEach { dirInput ->
                if (dirInput.contentTypes.contains(RESOURCES)) {
                    inputJavaResources.add(dirInput.file.toPath())
                }
                if (dirInput.contentTypes.contains(CLASSES)){
                    inputClasses.add(dirInput.file.toPath())
                }
            }
            it.jarInputs.forEach { jarInput ->
                if (jarInput.contentTypes.contains(RESOURCES)) {
                    inputJavaResources.add(jarInput.file.toPath())
                }
                if (jarInput.contentTypes.contains(CLASSES)) {
                    inputClasses.add(jarInput.file.toPath())
                }
            }
        }
        val javaResources =
            outputProvider.getContentLocation("java_res", setOf(RESOURCES), scopes, Format.JAR)
        Files.createDirectories(javaResources.toPath().parent)

        val bootClasspathInputs =
            getAllFiles(transformInvocation.referencedInputs) + bootClasspath.value

        runR8(
            inputClasses,
            output.toPath(),
            inputJavaResources,
            javaResources.toPath(),
            bootClasspathInputs.map { it.toPath() },
            toolConfig,
            proguardConfig,
            mainDexListConfig,
            messageReceiver,
            useFullR8
        )
    }
}