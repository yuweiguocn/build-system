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

package com.android.build.gradle.internal.core

import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.dsl.CoreBuildType
import com.android.build.gradle.internal.getProguardFiles
import com.android.build.gradle.internal.scope.CodeShrinker
import org.gradle.api.Project
import java.io.File

/**
 * This is an implementation of PostProcessingOptions interface for the old DSL
 */
class OldPostProcessingOptions(
    private val coreBuildType: CoreBuildType,
    private val project: Project
) : PostProcessingOptions {
    override fun getProguardFiles(type: ProguardFileType): Collection<File> = coreBuildType.getProguardFiles(type)

    override fun getDefaultProguardFiles(): List<File> =
        listOf(ProguardFiles.getDefaultProguardFile(ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName, project))

    override fun getPostprocessingFeatures(): PostprocessingFeatures? = null

    override fun getCodeShrinker() = when {
        !coreBuildType.isMinifyEnabled -> null
        coreBuildType.isUseProguard == false -> CodeShrinker.R8
        else -> CodeShrinker.PROGUARD
    }

    override fun resourcesShrinkingEnabled(): Boolean = coreBuildType.isShrinkResources
}
