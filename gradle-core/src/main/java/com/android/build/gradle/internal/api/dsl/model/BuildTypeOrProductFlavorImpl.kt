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

package com.android.build.gradle.internal.api.dsl.model

import com.android.build.api.dsl.model.BuildTypeOrProductFlavor
import com.android.build.api.dsl.options.PostProcessingFiles
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.errors.DeprecationReporter

class BuildTypeOrProductFlavorImpl(
            dslScope: DslScope,
            private val postProcessingFiles: () -> PostProcessingFiles)
        : SealableObject(dslScope), BuildTypeOrProductFlavor {

    override var applicationIdSuffix: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }
    override var versionNameSuffix: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    @Suppress("OverridingDeprecatedMember")
    override fun proguardFile(proguardFile: Any) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.proguardFiles",
                "proguardFile()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().proguardFiles.add(proguardFile)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun proguardFiles(vararg files: Any) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.proguardFiles",
                "proguardFiles()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().proguardFiles.addAll(files.toMutableList())
    }

    @Suppress("OverridingDeprecatedMember")
    override fun setProguardFiles(proguardFileIterable: Iterable<Any>) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.proguardFiles",
                "setProguardFiles()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().proguardFiles.addAll(proguardFileIterable.toMutableList())
    }

    @Suppress("OverridingDeprecatedMember")
    override fun testProguardFile(proguardFile: Any) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.testProguardFiles",
                "testProguardFile()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().testProguardFiles.add(proguardFile)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun testProguardFiles(vararg proguardFiles: Any) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.testProguardFiles",
                "testProguardFiles()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().testProguardFiles.addAll(proguardFiles.toMutableList())
    }

    @Suppress("OverridingDeprecatedMember")
    override fun setTestProguardFiles(files: Iterable<Any>) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.testProguardFiles",
                "setTestProguardFiles()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().testProguardFiles.addAll(files.toMutableList())
    }

    @Suppress("OverridingDeprecatedMember")
    override fun consumerProguardFile(proguardFile: Any) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.consumerProguardFiles",
                "consumerProguardFile()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().consumerProguardFiles.add(proguardFile)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun consumerProguardFiles(vararg proguardFiles: Any) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.consumerProguardFiles",
                "consumerProguardFiles()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().consumerProguardFiles.addAll(proguardFiles.toMutableList())
    }

    @Suppress("OverridingDeprecatedMember")
    override fun setConsumerProguardFiles(proguardFileIterable: Iterable<Any>) {
        dslScope.deprecationReporter.reportDeprecatedUsage(
                "Postprocessing.consumerProguardFiles",
                "setConsumerProguardFile()",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        postProcessingFiles.invoke().consumerProguardFiles.addAll(proguardFileIterable.toMutableList())
    }

    fun initWith(that: BuildTypeOrProductFlavorImpl) {
        applicationIdSuffix = that.applicationIdSuffix
        versionNameSuffix = that.versionNameSuffix
    }
}
