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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.CompilerArguments
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_MERGE
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_DATA_BINDING_BASE_FEATURE_INFO
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_DATA_BINDING_FEATURE_INFO
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * Arguments passed to data binding. This class mimics the [CompilerArguments] class except that it
 * also implements [CommandLineArgumentProvider] for input/output annotations.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DataBindingCompilerArguments constructor(
    @get:Input
    val artifactType: CompilerArguments.Type,

    // Use module package provider so that we can delay resolving the module package until execution
    // time (for performance). The resolved module package is set as @Input (see getModulePackage()
    // below), but the provider itself should be set as @Internal.
    @get:Internal
    private val modulePackageProvider: () -> String,

    @get:Input
    val minApi: Int,

    // We can't set the sdkDir as an @InputDirectory because it is too large to compute a hash. We
    // can't set it as an @Input either because it would break cache relocatability. Therefore, we
    // annotate it with @Internal, expecting that the directory's contents should be stable and this
    // won't affect correctness.
    @get:Internal
    val sdkDir: File,

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val dependencyArtifactsDir: BuildableArtifact,

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val layoutInfoDir: BuildableArtifact,

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val classLogDir: BuildableArtifact,

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val baseFeatureInfoDir: BuildableArtifact?,

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val featureInfoDir: BuildableArtifact?,

    @get:Optional
    @get:OutputDirectory
    val aarOutDir: File?,

    @get:Optional
    @get:OutputFile
    val exportClassListOutFile: File?,

    @get:Input
    val enableDebugLogs: Boolean,

    // We don't set this as an @Input because: (1) it doesn't affect the results of data binding
    // processing, and (2) its value is changed between an Android Studio build and a command line
    // build; by not setting it as @Input, we allow the users to get incremental/UP-TO-DATE builds
    // when switching between the two modes (see https://issuetracker.google.com/80555723).
    @get:Internal
    val printEncodedErrorLogs: Boolean,

    @get:Input
    val isTestVariant: Boolean,

    @get:Input
    val isEnabledForTests: Boolean,

    @get:Input
    val isEnableV2: Boolean
) : CommandLineArgumentProvider {

    @Input
    fun getModulePackage() = modulePackageProvider()

    override fun asArguments(): Iterable<String> {
        val arguments = CompilerArguments(
            artifactType = artifactType,
            modulePackage = getModulePackage(),
            minApi = minApi,
            sdkDir = sdkDir,
            dependencyArtifactsDir = dependencyArtifactsDir.get().singleFile,
            layoutInfoDir = layoutInfoDir.get().singleFile,
            classLogDir = classLogDir.get().singleFile,
            baseFeatureInfoDir = baseFeatureInfoDir?.get()?.singleFile,
            featureInfoDir = featureInfoDir?.get()?.singleFile,
            aarOutDir = aarOutDir,
            exportClassListOutFile = exportClassListOutFile,
            enableDebugLogs = enableDebugLogs,
            printEncodedErrorLogs = printEncodedErrorLogs,
            isTestVariant = isTestVariant,
            isEnabledForTests = isEnabledForTests,
            isEnableV2 = isEnableV2
        ).toMap()

        // Don't need to sort the returned list as the order shouldn't matter to Gradle.
        // Also don't need to escape the key and value strings as they will be passed as-is to
        // the Java compiler.
        return arguments.map { entry -> "-A${entry.key}=${entry.value}" }
    }

    companion object {

        @JvmStatic
        fun createArguments(
            variantScope: VariantScope,
            enableDebugLogs: Boolean,
            printEncodedErrorLogs: Boolean
        ): DataBindingCompilerArguments {
            val globalScope = variantScope.globalScope
            val variantData = variantScope.variantData
            val variantConfig = variantScope.variantConfiguration
            val artifacts = variantScope.artifacts

            // Get artifactType
            val artifactVariantData = if (variantData.type.isTestComponent) {
                variantScope.testedVariantData!!
            } else {
                variantData
            }
            val artifactType = if (artifactVariantData.type.isAar) {
                CompilerArguments.Type.LIBRARY
            } else {
                if (artifactVariantData.type.isBaseModule) {
                    CompilerArguments.Type.APPLICATION
                } else {
                    CompilerArguments.Type.FEATURE
                }
            }

            // Get exportClassListOutFile
            val exportClassListOutFile = if (variantData.type.isExportDataBindingClassList) {
                variantScope.generatedClassListOutputFileForDataBinding
            } else {
                null
            }

            return DataBindingCompilerArguments(
                artifactType = artifactType,
                modulePackageProvider = { variantConfig.originalApplicationId },
                minApi = variantConfig.minSdkVersion.apiLevel,
                sdkDir = globalScope.sdkHandler.checkAndGetSdkFolder(),
                dependencyArtifactsDir =
                        artifacts.getFinalArtifactFiles(DATA_BINDING_DEPENDENCY_ARTIFACTS),
                layoutInfoDir =
                        artifacts.getFinalArtifactFiles(DATA_BINDING_LAYOUT_INFO_TYPE_MERGE),
                classLogDir = artifacts.getFinalArtifactFiles(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
                baseFeatureInfoDir =
                        artifacts.getFinalArtifactFilesIfPresent(
                                FEATURE_DATA_BINDING_BASE_FEATURE_INFO),
                featureInfoDir =
                        artifacts.getFinalArtifactFilesIfPresent(FEATURE_DATA_BINDING_FEATURE_INFO),
                aarOutDir = variantScope.bundleArtifactFolderForDataBinding,
                exportClassListOutFile = exportClassListOutFile,
                enableDebugLogs = enableDebugLogs,
                printEncodedErrorLogs = printEncodedErrorLogs,
                isTestVariant = variantData.type.isTestComponent,
                isEnabledForTests = globalScope.extension.dataBinding.isEnabledForTests,
                isEnableV2 = globalScope.projectOptions.get(BooleanOption.ENABLE_DATA_BINDING_V2)
            )
        }
    }
}