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

package com.android.build.gradle.internal.api.dsl.options

import com.android.build.api.dsl.options.DexOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.errors.DeprecationReporter
import javax.inject.Inject

open class DexOptionsImpl @Inject constructor(dslScope: DslScope)
    : InitializableSealable<DexOptions>(dslScope), DexOptions {

    private val _additionalParameters: SealableList<String> = SealableList.new(dslScope)


    override var preDexLibraries: Boolean =true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var jumboMode: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var dexInProcess: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var keepRuntimeAnnotatedClasses: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var threadCount: Int? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var javaMaxHeapSize: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var additionalParameters: MutableList<String>
        get() = _additionalParameters
        set(value) {
            _additionalParameters.reset(value)
        }

    override var maxProcessCount: Int? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    @Suppress("OverridingDeprecatedMember")
    override var optimize: Boolean? = null
        set(value) {
            if (checkSeal()) {
                dslScope.deprecationReporter.reportObsoleteUsage("DexOptions.optimize",
                        DeprecationReporter.DeprecationTarget.OLD_DSL)
                field = value
            }
        }

    @Suppress("OverridingDeprecatedMember")
    override var incremental: Boolean = false
        set(value) {
            if (checkSeal()) {
                dslScope.deprecationReporter.reportObsoleteUsage("DexOptions.incremental",
                        DeprecationReporter.DeprecationTarget.OLD_DSL)
                field = value
            }
        }

    override fun initWith(that: DexOptions) {
        if (checkSeal()) {
            preDexLibraries = that.preDexLibraries
            jumboMode = that.jumboMode
            dexInProcess = that.dexInProcess
            keepRuntimeAnnotatedClasses = that.keepRuntimeAnnotatedClasses
            threadCount = that.threadCount
            javaMaxHeapSize = that.javaMaxHeapSize
            _additionalParameters.reset(that.additionalParameters)
            maxProcessCount = that.maxProcessCount

            @Suppress("DEPRECATION")
            optimize = that.optimize
            @Suppress("DEPRECATION")
            incremental = that.incremental
        }
    }

    override fun seal() {
        super.seal()
        _additionalParameters.seal()
    }
}