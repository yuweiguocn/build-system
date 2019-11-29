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

import com.android.build.api.dsl.options.InstrumentationOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableMap
import javax.inject.Inject

open class InstrumentationOptionsImpl @Inject constructor(dslScope: DslScope)
    : InitializableSealable<InstrumentationOptions>(dslScope), InstrumentationOptions {

    private val _instrumentationRunnerArguments: SealableMap<String, String> = SealableMap.new(dslScope)

    override var applicationId: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var instrumentationRunner: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var instrumentationRunnerArguments: MutableMap<String, String>
        get() = _instrumentationRunnerArguments
        set(value) {
            _instrumentationRunnerArguments.reset(value)
        }

    override var handleProfiling: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var functionalTest: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun initWith(that: InstrumentationOptions) {
        if (checkSeal()) {
            applicationId = that.applicationId
            instrumentationRunner = that.instrumentationRunner
            _instrumentationRunnerArguments.reset(that.instrumentationRunnerArguments)
            handleProfiling = that.handleProfiling
            functionalTest = that.functionalTest
        }
    }

    override fun seal() {
        super.seal()
        _instrumentationRunnerArguments.seal()
    }
}