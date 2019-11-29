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

package com.android.build.gradle.internal.variant2

import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer

/** Data for a particular dimension
 * ([com.android.build.api.dsl.model.ProductFlavor]/[com.android.build.api.dsl.model.BuildType])
 */
class DimensionData<out T>(
        val dimensionObject: T,
        private val sourceSet: AndroidSourceSet,
        private val androidTestSourceSet: AndroidSourceSet?,
        private val unitTestSourceSet: AndroidSourceSet?,
        configurationContainer: ConfigurationContainer) {

    init {
        androidTestSourceSet?.let {
            makeTestExtendMain(sourceSet, it, configurationContainer)
        }

        unitTestSourceSet?.let {
            makeTestExtendMain(sourceSet, it, configurationContainer)
        }
    }

    fun getSourceSet(type: VariantType) = when (type) {
        VariantTypeImpl.ANDROID_TEST -> androidTestSourceSet
        VariantTypeImpl.UNIT_TEST -> unitTestSourceSet
        else -> sourceSet
    }
}

private fun makeTestExtendMain(
        mainSourceSet: AndroidSourceSet,
        testSourceSet: AndroidSourceSet,
        configurations: ConfigurationContainer) {
    linkConfiguration(
            configurations, mainSourceSet.implementationConfigurationName, testSourceSet.implementationConfigurationName)
    linkConfiguration(
            configurations, mainSourceSet.runtimeOnlyConfigurationName, testSourceSet.runtimeOnlyConfigurationName)
}

private fun linkConfiguration(
        configurations: ConfigurationContainer,
        mainConfigName: String,
        testConfigName: String) {
    configurations.getByName(testConfigName).extendsFrom(configurations.getByName(mainConfigName))
}
