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

package com.android.build.api.dsl.extension

import com.android.build.api.dsl.variant.Variant
import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface VariantCallbackHandler<T: Variant> {

    fun all(action: Action<T>)

    fun withName(name: String, action: Action<T>)

    fun withName(name: String) : VariantCallbackHandler<T>

    fun <S : Variant> withType(variantClass: Class<S>, action: Action<S>)

    fun <S : Variant> withType(variantClass: Class<S>) : VariantCallbackHandler<S>

    fun withBuildType(name: String, action: Action<T>)

    fun withBuildType(name: String) : VariantCallbackHandler<T>

    fun withProductFlavor(name: String, action: Action<T>)

    fun withProductFlavor(name: String) : VariantCallbackHandler<T>
}