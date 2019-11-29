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

package com.android.build.gradle.internal

import com.android.build.gradle.FeatureExtension
import com.android.build.gradle.FeaturePlugin
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.scope.GlobalScope
import org.gradle.api.artifacts.ProjectDependency

/**
 * [ModelBuilder] class created by [FeaturePlugin]. It needs to be put in a separate file to work
 * around https://issuetracker.google.com/73383831.
 */
class FeatureModelBuilder(
    globalScope: GlobalScope,
    variantManager: VariantManager,
    taskManager: TaskManager,
    config: FeatureExtension,
    extraModelInfo: ExtraModelInfo,
    projectType: Int,
    generation: Int
) : ModelBuilder<FeatureExtension>(
    globalScope,
    variantManager,
    taskManager,
    config,
    extraModelInfo,
    projectType,
    generation
) {
    override fun isBaseSplit(): Boolean {
        return extension.baseFeature!!
    }

    override fun getDynamicFeatures(): MutableCollection<String> {
        return getDynamicFeatures(globalScope)
    }

    companion object {
        @JvmStatic
        fun getDynamicFeatures(globalScope: GlobalScope): MutableCollection<String> {
            @Suppress("DEPRECATION")
            val featureConfig =
                globalScope.project.configurations.getByName(VariantDependencies.CONFIG_NAME_FEATURE)
            val dependencies = featureConfig.dependencies
            return dependencies
                .asSequence()
                .filter { it is ProjectDependency }
                .map { (it as ProjectDependency).dependencyProject.path }
                .toMutableList()
        }
    }
}