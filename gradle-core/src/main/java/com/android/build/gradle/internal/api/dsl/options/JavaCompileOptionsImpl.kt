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
import com.android.build.api.dsl.options.JavaCompileOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.OptionalSupplier
import com.google.common.base.Charsets
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import javax.inject.Inject

open class JavaCompileOptionsImpl @Inject constructor(dslScope: DslScope)
        : InitializableSealable<JavaCompileOptions>(dslScope), JavaCompileOptions {

    @Suppress("LeakingThis")
    private val _annotationProcessorOptions = OptionalSupplier(this, AnnotationProcessorOptionsImpl::class.java, dslScope)

    override var sourceCompatibility: JavaVersion = JavaVersion.VERSION_1_6
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun setSourceCompatibility(value: Any) {
        if (checkSeal()) {
            sourceCompatibility = JavaVersion.toVersion(value)
        }
    }

    override var targetCompatibility: JavaVersion = JavaVersion.VERSION_1_6
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun setTargetCompatibility(value: Any) {
        if (checkSeal()) {
            targetCompatibility = JavaVersion.toVersion(value)
        }
    }

    override var encoding: String =  Charsets.UTF_8.name()
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }
    override var incremental: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override val annotationProcessorOptions: AnnotationProcessorOptions
        get() = _annotationProcessorOptions.get()

    override fun annotationProcessorOptions(action: Action<AnnotationProcessorOptions>) {
        action.execute(_annotationProcessorOptions.get())
    }

    override fun initWith(that: JavaCompileOptions) {
        if (checkSeal()) {
            sourceCompatibility = that.sourceCompatibility
            targetCompatibility = that.targetCompatibility
            encoding = that.encoding
            incremental = that.incremental
            if (that is JavaCompileOptionsImpl) {
                _annotationProcessorOptions.copyFrom(that._annotationProcessorOptions)
            }
        }
    }

    override fun seal() {
        super.seal()
        _annotationProcessorOptions.seal()
    }
}