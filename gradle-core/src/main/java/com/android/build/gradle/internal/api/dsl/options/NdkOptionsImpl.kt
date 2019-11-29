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

import com.android.build.api.dsl.options.NdkOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableSet
import javax.inject.Inject

open class NdkOptionsImpl @Inject constructor(dslScope: DslScope)
        : InitializableSealable<NdkOptions>(dslScope), NdkOptions {

    //backing properties for lists/sets
    private val _ldlibs: SealableList<String> = SealableList.new(dslScope)
    private val _abiFilters: SealableSet<String> = SealableSet.new(dslScope)

    override var ldLibs: MutableList<String>
        get() = _ldlibs
        set(value) {
            _ldlibs.reset(value)
        }

    override var abiFilters: MutableSet<String>
        get() = _abiFilters
        set(value) {
            _abiFilters.reset(value)
        }

    override var moduleName: String? = null
    override var cFlags: String? = null
    override var stl: String? = null
    override var jobs: Int? = null

    override fun initWith(that: NdkOptions) {
        if (checkSeal()) {
            moduleName = that.moduleName
            cFlags = that.cFlags
            stl = that.stl
            jobs = that.jobs
            _ldlibs.reset(that.ldLibs)
            _abiFilters.reset(that.abiFilters)
        }
    }

    override fun seal() {
        super.seal()

        _ldlibs.seal()
        _abiFilters.seal()

    }
}