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

import com.android.build.api.dsl.options.ExternalNativeBuildOptions
import com.android.build.api.dsl.options.ExternalNativeCmakeOptions
import com.android.build.api.dsl.options.ExternalNativeNdkBuildOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.InitializableSealable
import org.gradle.api.Action
import javax.inject.Inject

open class ExternalNativeBuildOptionsImpl @Inject constructor(dslScope: DslScope)
        : InitializableSealable<ExternalNativeBuildOptions>(dslScope),
        ExternalNativeBuildOptions {

    override val externalNativeNdkBuildOptions: ExternalNativeNdkBuildOptions?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun externalNativeNdkBuildOptions(action: Action<ExternalNativeNdkBuildOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val externalNativeCmakeOptions: ExternalNativeCmakeOptions?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun externalNativeCmakeOptions(action: Action<ExternalNativeCmakeOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun initWith(that: ExternalNativeBuildOptions) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}