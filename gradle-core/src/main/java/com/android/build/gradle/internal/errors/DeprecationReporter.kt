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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.options.Option

/**
 * Reporter for issues during evaluation.
 *
 *
 * This handles dealing with errors differently if the project is being run from the command line
 * or from the IDE, in particular during Sync when we don't want to throw any exception
 */
interface DeprecationReporter {

    /** Enum for deprecated element removal target.  */
    enum class DeprecationTarget  constructor(val removalTime: String) {
        // deprecation of compile in favor of api/implementation
        CONFIG_NAME("at the end of 2018"),
        // deprecation due to the move to the new DSL.
        OLD_DSL("at the end of 2018"),
        // Obsolete Dex Options
        DEX_OPTIONS("at the end of 2018"),
        // Deprecation of AAPT, replaced by AAPT2.
        AAPT("at the end of 2018"),
        // When legacy dexer will be removed and fully replaced by D8.
        LEGACY_DEXER(
            "in the future AGP versions. For more details, see " +
                    "https://d.android.com/r/studio-ui/d8-overview.html"),
        // Deprecation of disabling Desugar
        DESUGAR_TOOL("in AGP version 3.4"),
        // Deprecation of Task Access in the variant API
        TASK_ACCESS_VIA_VARIANT("at the end of 2019"),
        // Deprecation of the SDK maven repositories
        SDK_MAVEN_REPOS("in AGP version 3.5")
    }

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param newDslElement the DSL element to use instead, with the name of the class owning it
     * @param oldDslElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param newDslElement the DSL element to use instead, with the name of the class owning it
     * @param oldDslElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param newApiElement the DSL element to use instead, with the name of the class owning it
     * @param oldApiElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param url URL to documentation about the deprecation
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedApi(
        newApiElement: String,
        oldApiElement: String,
        url: String,
        deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecated value usage for a DSL element in the DSL/API.
     *
     * @param dslElement name of DSL element containing the deprecated value, with the name of the
     * class.
     * @param oldValue value of the DSL element which has been deprecated.
     * @param newValue optional new value replacing the deprecated value.
     * @param url optional url for more context.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedValue(
            dslElement: String,
            oldValue: String,
            newValue: String?,
            url: String?,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param oldDslElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportObsoleteUsage(
            oldDslElement: String,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param oldDslElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param url optional url for more context.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportObsoleteUsage(
            oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a renamed Configuration.
     *
     * @param newConfiguration the name of the [org.gradle.api.artifacts.Configuration] to use
     * instead
     * @param oldConfiguration the name of the deprecated [org.gradle.api.artifacts.Configuration]
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * @param url optional url for more context.
     * timing is added to the message.
     */
    fun reportRenamedConfiguration(
            newConfiguration: String,
            oldConfiguration: String,
            deprecationTarget: DeprecationTarget,
            url: String? = null)

    /**
     * Reports a deprecated Configuration, that gets replaced by an optional DSL element
     *
     * @param newDslElement the name of the DSL element that replaces the configuration
     * @param oldConfiguration the name of the deprecated [org.gradle.api.artifacts.Configuration]
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedConfiguration(
        newDslElement: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecated option usage.
     *
     * @param option the deprecated option
     * @param value the value for the flag which should be used to remove the warning
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedOption(
            option: String,
            value: String?,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports deprecated options usage.
     *
     * @param options the set of deprecated options that were used.
     */
    fun reportDeprecatedOptions(options: Set<Option<*>>) {
        for (option in options) {
            reportDeprecatedOption(
                option.propertyName,
                option.defaultValue?.toString(),
                (option.status as Option.Status.Deprecated).deprecationTarget)
        }
    }

    /**
     * Reports experimental options usage.
     */
    fun reportExperimentalOption(option: Option<*>, value: String)

}
