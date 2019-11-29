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

package com.android.build.gradle.internal.fixtures

import com.android.build.gradle.internal.ide.SyncIssueImpl
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue

class FakeEvalIssueReporter(
    private val throwOnError : Boolean = false) : EvalIssueReporter {

    val messages = mutableListOf<String>()
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    override fun reportIssue(type: EvalIssueReporter.Type,
            severity: EvalIssueReporter.Severity,
            exception: EvalIssueException): SyncIssue {
        messages.add(exception.message)
        when(severity) {
            EvalIssueReporter.Severity.ERROR -> errors.add(exception.message)
            EvalIssueReporter.Severity.WARNING -> warnings.add(exception.message)
        }
        if (severity == EvalIssueReporter.Severity.ERROR && throwOnError) {
            throw exception
        }
        return SyncIssueImpl(type, severity, exception.data, exception.message, listOf())
    }
}