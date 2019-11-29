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

import com.android.build.api.dsl.options.PackagingOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableSet
import javax.inject.Inject

open class PackagingOptionsImpl @Inject constructor(dslScope: DslScope)
    : InitializableSealable<PackagingOptions>(dslScope), PackagingOptions {

    private val _excludes: SealableSet<String> = SealableSet.new(dslScope)
    private val _pickFirsts: SealableSet<String> = SealableSet.new(dslScope)
    private val _merges: SealableSet<String> = SealableSet.new(dslScope)
    private val _doNotStrip: SealableSet<String> = SealableSet.new(dslScope)

    override var excludes: MutableSet<String>
        get() = _excludes
        set(value) {
            _excludes.reset(value)
        }

    override var pickFirsts: MutableSet<String>
        get() = _pickFirsts
        set(value) {
            _pickFirsts.reset(value)
        }

    override var merges: MutableSet<String>
        get() = _merges
        set(value) {
            _merges.reset(value)
        }

    override var doNotStrip: MutableSet<String>
        get() = _doNotStrip
        set(value) {
            _doNotStrip.reset(value)
        }

    override fun exclude(value: String) {
        _excludes.add(value)
    }

    override fun exclude(vararg values: String) {
        _excludes.addAll(values)
    }

    override fun pickFirst(value: String) {
        _pickFirsts.add(value)
    }

    override fun pickFirst(vararg values: String) {
        _pickFirsts.addAll(values)
    }

    override fun merge(value: String) {
        _merges.add(value)
    }

    override fun merge(vararg values: String) {
        _merges.addAll(values)
    }

    override fun initWith(that: PackagingOptions) {
        if (checkSeal()) {
            _excludes.reset(that.excludes)
            _pickFirsts.reset(that.pickFirsts)
            _merges.reset(that.merges)
            _doNotStrip.reset(that.doNotStrip)
        }
    }

    override fun seal() {
        super.seal()

        _excludes.seal()
        _pickFirsts.seal()
        _merges.seal()
        _doNotStrip.seal()
    }
}