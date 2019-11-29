/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

class FakeResolvedDependencyResult(
    private val from: ResolvedComponentResult? = null,
    private val constraint: Boolean? = null,
    private val selected: ResolvedComponentResult? = null,
    private val requested: ComponentSelector? = null
) : ResolvedDependencyResult {

    override fun getFrom() = from ?: error("value not set")
    override fun isConstraint() = constraint ?: error("value not set")
    override fun getSelected() = selected ?: error("value not set")
    override fun getRequested() = requested ?: error("value not set")
}