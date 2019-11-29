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

import com.android.build.api.dsl.options.PostProcessingFilesOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import javax.inject.Inject

open class PostProcessingFilesOptionsImpl @Inject constructor(dslScope: DslScope)
        : InitializableSealable<PostProcessingFilesOptions>(dslScope), PostProcessingFilesOptions {

    // the actual backing data for the exposed properties.
    private val _proguardFiles: SealableList<Any> = SealableList.new(dslScope)
    private val _testProguardFiles: SealableList<Any> = SealableList.new(dslScope)
    private val _consumerProguardFiles: SealableList<Any> = SealableList.new(dslScope)

    override var proguardFiles: MutableList<Any>
        get() = _proguardFiles
        set(value) {
            _proguardFiles.reset(value)
        }

    override var testProguardFiles: MutableList<Any>
        get() = _testProguardFiles
        set(value) {
            _testProguardFiles.reset(value)
        }

    override var consumerProguardFiles: MutableList<Any>
        get() = _consumerProguardFiles
        set(value) {
            _consumerProguardFiles.reset(value)
        }

    override fun initWith(that: PostProcessingFilesOptions) {
        _proguardFiles.reset(that.proguardFiles)
        _testProguardFiles.reset(that.testProguardFiles)
        _consumerProguardFiles.reset(that.consumerProguardFiles)
    }

    override fun seal() {
        super.seal()

        _proguardFiles.seal()
        _testProguardFiles.seal()
        _consumerProguardFiles.seal()
    }
}
