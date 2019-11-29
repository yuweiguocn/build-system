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

package com.android.build.gradle.internal.variant2

import com.android.build.api.dsl.variant.VariantFilter
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.EvalIssueReporter

class VariantFilterImpl(
        override val buildType:String,
        override val flavors: List<String>,
        dslScope: DslScope): SealableObject(dslScope), VariantFilter {
    private var _ignoresProd = false
    private var _ignoresAndroidTest = false
    private var _ignoresUnitTest = false

    override fun ignoreAll() {
        if (checkSeal()) {
            _ignoresProd = true
            _ignoresAndroidTest = true
            _ignoresUnitTest = true
        }
    }

    override fun ignoreTests() {
        if (checkSeal()) {
            _ignoresAndroidTest = true
            _ignoresUnitTest = true
        }
    }

    override fun ignoreUnitTests() {
        if (checkSeal()) {
            _ignoresUnitTest = true
        }
    }

    override fun ignoreAndroidTests() {
        if (checkSeal()) {
            _ignoresAndroidTest = true
        }
    }

    fun ignores(type: VariantType) = when(type) {
        VariantTypeImpl.UNIT_TEST -> _ignoresUnitTest
        VariantTypeImpl.ANDROID_TEST -> _ignoresAndroidTest
        else -> _ignoresProd
    }

    val ignoresAll: Boolean
        get() = _ignoresProd && _ignoresAndroidTest && _ignoresUnitTest
}