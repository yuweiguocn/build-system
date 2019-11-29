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

package com.android.build.gradle.options

import com.android.build.gradle.internal.errors.DeprecationReporter

interface Option<out T> {

    sealed class Status {
        object EXPERIMENTAL: Status()
        object STABLE: Status()
        class Deprecated(val deprecationTarget: DeprecationReporter.DeprecationTarget): Status()
        object REMOVED: Status()
    }

    val propertyName: String

    val defaultValue: T?
        get() = null

    val status: Status

    val additionalInfo: String
        get() {
            return ""
        }

    fun parse(value: Any): T
}
