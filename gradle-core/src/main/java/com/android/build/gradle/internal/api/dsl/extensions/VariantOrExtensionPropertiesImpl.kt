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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.extension.VariantOrExtensionProperties
import com.android.build.api.dsl.options.AaptOptions
import com.android.build.api.dsl.options.DexOptions
import com.android.build.api.dsl.options.LintOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.options.DexOptionsImpl
import com.android.build.gradle.internal.api.dsl.sealing.OptionalSupplier
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.model.DataBindingOptions
import org.gradle.api.Action

class VariantOrExtensionPropertiesImpl(dslScope: DslScope)
        : SealableObject(dslScope),
        VariantOrExtensionProperties {

    private val _dexOptions = OptionalSupplier(this, DexOptionsImpl::class.java, dslScope)

    override fun aaptOptions(action: Action<AaptOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val aaptOptions: AaptOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun dexOptions(action: Action<DexOptions>) {
        action.execute(_dexOptions.get())
    }

    override val dexOptions: DexOptions
        get() = _dexOptions.get()

    override val lintOptions: LintOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun lintOptions(action: Action<LintOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val dataBinding: DataBindingOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun dataBinding(action: Action<DataBindingOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun initWith(that: VariantOrExtensionPropertiesImpl) {
        if (checkSeal()) {
            _dexOptions.copyFrom(that._dexOptions)
        }
    }

    override fun seal() {
        super.seal()
        _dexOptions.seal()
    }
}