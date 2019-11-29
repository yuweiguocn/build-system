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

package com.android.build.gradle.internal.api.dsl

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.model.ObjectFactory

/**
 * Scope of the DSL objects.
 *
 * This contains whatever is needed by all the DSL objects:
 * - the issue reporter
 * - the deprecation reporter
 * - the instantiator.
 */
interface DslScope {

    val issueReporter: EvalIssueReporter

    val deprecationReporter: DeprecationReporter

    val objectFactory: ObjectFactory
}