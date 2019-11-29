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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter

/**
 * This class is responsible for determining which ABIs are needed for the build based on the
 * relevant contents of build.gradle DSL.
 */
class AbiConfigurator(
        ndkHandlerSupportedAbis: Collection<Abi>,
        ndkHandlerDefaultAbis: Collection<Abi>,
        externalNativeBuildAbiFilters: Set<String>,
        ndkConfigAbiFilters: Set<String>,
        splitsFilterAbis: Set<String>,
        ideBuildOnlyTargetAbi: Boolean,
        ideBuildTargetAbi: String?) {

    val allAbis: Collection<String>
    val validAbis: Collection<Abi>

    /** Sort and join a list of strings for an error message */
    private fun sortAndJoinAbiStrings(elements: Collection<String>): String {
        return elements.sorted().joinToString(", ")
    }
    private fun sortAndJoinAbi(elements: Collection<Abi>): String {
        return elements.sorted().joinToString(", ")
    }

    init {
        val ndkHandlerSupportedAbiStrings = ndkHandlerSupportedAbis.map(Abi::getName)
        val userChosenAbis =
                externalNativeBuildAbiFilters union splitsFilterAbis union ndkConfigAbiFilters
        val userMistakes =
                userChosenAbis subtract ndkHandlerSupportedAbiStrings
        if (!userMistakes.isEmpty()) {
            error("ABIs [${sortAndJoinAbiStrings(userMistakes)}] are not supported for platform. " +
                "Supported ABIs are [${sortAndJoinAbiStrings(ndkHandlerSupportedAbiStrings)}].")
        }

        val configurationAbis : Collection<Abi>
        if (userChosenAbis.isEmpty()) {
            // The user didn't explicitly name any ABIs so return the default set
            allAbis = ndkHandlerDefaultAbis.map(Abi::getName)
            configurationAbis = ndkHandlerDefaultAbis
        } else {
            // The user explicitly named some ABIs
            val recognizeAbleAbiStrings = Abi.values()
                    .map(Abi::getName)
                    .toSet()
            val selectedAbis =
                    sequenceOf(externalNativeBuildAbiFilters,
                            ndkConfigAbiFilters,
                            splitsFilterAbis)
                            .filter { !it.isEmpty() }
                            .fold(recognizeAbleAbiStrings) { total, next -> total intersect next }

            // Produce the list of expected JSON files. This list includes possibly invalid ABIs
            // so that generator can create fallback JSON for them.
            allAbis = selectedAbis union userMistakes
            // These are ABIs that are available on the current platform
            configurationAbis = selectedAbis.mapNotNull(Abi::getByName)
        }

        // Lastly, if there is an injected ABI set and none of the ABIs is actually buildable by
        // this project then issue an error.
        if (ideBuildOnlyTargetAbi && ideBuildTargetAbi != null && !ideBuildTargetAbi.isEmpty()) {
            val injectedAbis = ideBuildTargetAbi.split(",").map { it.trim() }
            val injectedLegalAbis = injectedAbis.mapNotNull(Abi::getByName)
            validAbis = if (injectedLegalAbis.isEmpty()) {
                // The user (or android studio) didn't select any legal ABIs, that's an error
                // since there's nothing to build. Fall back to the ABIs from build.gradle so
                // that there's something to show the user.
                error("ABIs [$ideBuildTargetAbi] set by " +
                                "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                                "flag is not supported. Supported ABIs " +
                                "are [${sortAndJoinAbiStrings(allAbis)}].")
                configurationAbis
            } else {
                val invalidAbis = injectedAbis.filter { Abi.getByName(it) == null }
                if (!invalidAbis.isEmpty()) {
                    // The user (or android studio) selected some illegal ABIs. Give a warning and
                    // continue on.
                    warn("ABIs [$ideBuildTargetAbi] set by " +
                        "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                        "flag contained '${sortAndJoinAbiStrings(invalidAbis)}' which is invalid.")
                }

                val legalButNotTargetedByConfiguration = injectedLegalAbis subtract configurationAbis
                if (legalButNotTargetedByConfiguration.isNotEmpty()) {
                    // The user (or android studio) selected some ABIs that are valid but that
                    // aren't targeted by this build configuration. Warn but continue on with any
                    // ABIs that were valid.
                    warn("ABIs [$ideBuildTargetAbi] set by " +
                        "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                        "flag contained '${sortAndJoinAbi(legalButNotTargetedByConfiguration)}' " +
                        "not targeted by this project.")
                    // Keep ABIs actually targeted
                    injectedLegalAbis intersect configurationAbis
                } else {
                    injectedLegalAbis
                }
            }
        } else {
            validAbis = configurationAbis
        }
    }
}
