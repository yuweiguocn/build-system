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

import com.android.build.api.dsl.options.VectorDrawablesOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableSet
import javax.inject.Inject

open class VectorDrawableOptionsImpl @Inject constructor(dslScope: DslScope)
    : InitializableSealable<VectorDrawablesOptions>(dslScope), VectorDrawablesOptions {

    private val _generatedDensities: SealableSet<String> = SealableSet.new(dslScope)

    override var generatedDensities: MutableSet<String>
        get() = _generatedDensities
        set(value) {
            _generatedDensities.reset(value)
        }

    override var useSupportLibrary: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun initWith(that: VectorDrawablesOptions) {
        if (checkSeal()) {
            useSupportLibrary = that.useSupportLibrary
            _generatedDensities.reset(that.generatedDensities)
        }
    }

    override fun seal() {
        super.seal()

        _generatedDensities.seal()
    }
}