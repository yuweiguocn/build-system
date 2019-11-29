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

import com.android.build.api.dsl.options.ShaderOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import com.google.common.collect.ListMultimap
import javax.inject.Inject

open class ShaderOptionsImpl @Inject constructor(dslScope: DslScope)
        : InitializableSealable<ShaderOptions>(dslScope), ShaderOptions {

    override var glslcArgs: MutableList<String>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
    override var scopedGlslcArgs: ListMultimap<String, String>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun initWith(that: ShaderOptions) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}