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

import com.android.build.api.dsl.options.AnnotationProcessorOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableMap
import javax.inject.Inject

open class AnnotationProcessorOptionsImpl @Inject constructor(dslScope: DslScope)
    : InitializableSealable<AnnotationProcessorOptions>(dslScope), AnnotationProcessorOptions {

    private val _classNames: SealableList<String> = SealableList.new(dslScope)
    private val _arguments: SealableMap<String, String> = SealableMap.new(dslScope)

    override var classNames: MutableList<String>
        get() = _classNames
        set(value) {
            _classNames.reset(value)
        }

    override var arguments: MutableMap<String, String>
        get() = _arguments
        set(value) {
            _arguments.reset(value)
        }

    override var includeCompileClasspath: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun initWith(that: AnnotationProcessorOptions) {
        if (checkSeal()) {
            _classNames.reset(that.classNames)
            _arguments.reset(that.arguments)
            includeCompileClasspath = that.includeCompileClasspath
        }
    }

    override fun seal() {
        super.seal()
        _classNames.seal()
        _arguments.seal()
    }
}