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

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.options.Option

class FakeDeprecationReporter: DeprecationReporter {

    val deprecationWarnings = mutableListOf<String>()

    override fun reportDeprecatedUsage(newDslElement: String,
            oldDslElement: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        deprecationWarnings.add(oldDslElement)
    }

    override fun reportDeprecatedUsage(newDslElement: String,
            oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        deprecationWarnings.add(oldDslElement)
    }

    override fun reportObsoleteUsage(oldDslElement: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        deprecationWarnings.add(oldDslElement)
    }

    override fun reportObsoleteUsage(oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        deprecationWarnings.add(oldDslElement)
    }

    override fun reportDeprecatedApi(
        newApiElement: String,
        oldApiElement: String,
        url: String,
        deprecationTarget: DeprecationReporter.DeprecationTarget
    ) {
        deprecationWarnings.add(oldApiElement)
    }

    override fun reportRenamedConfiguration(
        newConfiguration: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationReporter.DeprecationTarget,
        url: String?
    ) {
        deprecationWarnings.add(oldConfiguration)
    }

    override fun reportDeprecatedConfiguration(
        newDslElement: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationReporter.DeprecationTarget
    ) {
        deprecationWarnings.add(oldConfiguration)
    }

    override fun reportDeprecatedValue(dslElement: String,
            oldValue: String,
            newValue: String?,
            url: String?,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        deprecationWarnings.add(dslElement)
    }

    override fun reportDeprecatedOption(option: String,
            value: String?,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        deprecationWarnings.add(option)
    }

    override fun reportExperimentalOption(option: Option<*>, value: String) {
        deprecationWarnings.add(option.propertyName)
    }
}