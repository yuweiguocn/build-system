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

package com.android.build.gradle.internal.fixture

import org.gradle.api.NamedDomainObjectContainer

/**
 * Extensions method to create and configure object in named object container as if it was a DSL.
 *
 * The function passed in parameter is an extension function of the created object and therefore
 * doesn't need to use "it"
 */
fun <T> NamedDomainObjectContainer<T>.createAndConfig(name: String, action: T.() -> Unit) {
    val value = this.maybeCreate(name)
    action.invoke(value)
}
