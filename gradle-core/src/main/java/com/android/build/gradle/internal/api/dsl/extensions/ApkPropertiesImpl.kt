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

import com.android.build.api.dsl.extension.ApkProperties
import com.android.build.api.dsl.options.PackagingOptions
import com.android.build.api.dsl.options.Splits
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.options.PackagingOptionsImpl
import com.android.build.gradle.internal.api.dsl.sealing.OptionalSupplier
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import org.gradle.api.Action

class ApkPropertiesImpl(dslScope: DslScope): SealableObject(dslScope), ApkProperties {

    private val _packagingOptions = OptionalSupplier(
            this, PackagingOptionsImpl::class.java, dslScope)

    override val packagingOptions: PackagingOptions
        get() = _packagingOptions.get()

    override fun packagingOptions(action: Action<PackagingOptions>) {
        action.execute(_packagingOptions.get())
    }

    override val splits: Splits
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun splits(action: Action<Splits>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var generatePureSplits: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun seal() {
        super.seal()
        _packagingOptions.seal()
    }

}