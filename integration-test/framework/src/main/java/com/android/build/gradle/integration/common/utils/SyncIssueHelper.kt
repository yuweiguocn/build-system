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

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.android.build.gradle.integration.common.utils

import com.android.builder.model.SyncIssue
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import org.junit.Assert.fail

fun checkIssuesForSameSeverity(issues: Collection<SyncIssue>, severity: Int) {
    testIssuesForSingleValue(issues, severity, "severity", SyncIssue::getSeverity)
}

fun checkIssuesForSameType(issues: Collection<SyncIssue>, type: Int) {
    testIssuesForSingleValue(issues, type, "type", SyncIssue::getType)
}

fun checkIssuesForSameData(issues: Collection<SyncIssue>, data: String?) {
    testIssuesForSingleValue(issues, data, "data", SyncIssue::getData)
}

fun checkIssuesWithSeverityIn(issues: Collection<SyncIssue>, vararg severity: Integer) {
    testIssuesForPossibleValue(issues, severity.asList(), "severity", SyncIssue::getSeverity)
}

fun checkIssuesFoTypeIn(issues: Collection<SyncIssue>, vararg type: Integer) {
    testIssuesForPossibleValue(issues, type.asList(), "type", SyncIssue::getType)
}

fun checkIssuesForDataIn(issues: Collection<SyncIssue>, vararg data: String) {
    testIssuesForPossibleValue(issues, data.asList(), "data", SyncIssue::getData)
}

fun checkSomeIssuesHaveSeverityValue(issues: Collection<SyncIssue>, data: String): Int =
    testIssuesForAtleastOneWithValue(issues, data, "severity", SyncIssue::getSeverity)

fun checkSomeIssuesHaveTypeValue(issues: Collection<SyncIssue>, data: String): Int =
    testIssuesForAtleastOneWithValue(issues, data, "type", SyncIssue::getType)

fun checkSomeIssuesHaveDataValue(issues: Collection<SyncIssue>, data: String): Int =
    testIssuesForAtleastOneWithValue(issues, data, "data", SyncIssue::getData)


private fun <T> testIssuesForSingleValue(
        issues: Collection<SyncIssue>,
        expectedValue: T?,
        propName: String,
        function: (SyncIssue) ->  T) {
    val incorrectIssues = ArrayList<SyncIssue>()
    val correctIssues = ArrayList<SyncIssue>()
    issues.forEach {
        val value = function.invoke(it)
        if (expectedValue != value) {
            incorrectIssues.add(it)
        } else {
            correctIssues.add(it)
        }
    }

    if (!incorrectIssues.isEmpty()) {
        val total = issues.size
        fail("Not true that all <$issues> have '$propName' == <$expectedValue>. It contains correct items (${correctIssues.size}/$total) <$correctIssues>, and incorrect items (${incorrectIssues.size}/$total) <$incorrectIssues>")
    }
}

private fun <T> testIssuesForPossibleValue(
        issues: Collection<SyncIssue>,
        expectedValues: List<T?>,
        propName: String,
        function: (SyncIssue) ->  T) {
    val incorrectIssues : ListMultimap<T, SyncIssue> = ArrayListMultimap.create()
    val correctIssues = ArrayList<SyncIssue>()
    issues.forEach {
        val value = function.invoke(it)
        if (!expectedValues.contains(value)) {
            incorrectIssues.put(value, it)
        } else {
            correctIssues.add(it)
        }
    }

    if (!incorrectIssues.isEmpty) {
        val total = issues.size
        incorrectIssues.values()

        fail("Not true that all <$issues> have '$propName' in <$expectedValues>'. It contains correct items (${correctIssues.size}/$total) <$correctIssues>, and incorrect items (${incorrectIssues.size()}/$total) <${incorrectIssues.values()}>")
    }
}

private fun <T> testIssuesForAtleastOneWithValue(issues: Collection<SyncIssue>, expectedValue: T?, propName: String, function: (SyncIssue) -> T): Int {
    var count = 0
    issues.forEach {
        if (expectedValue == function.invoke(it)) {
            count++
        }
    }

    if (count == 0) {
        fail("Not true that  <$issues> contains at least one issue with '$propName' == <$expectedValue>.")
    }

    return count
}
