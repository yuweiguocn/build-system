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

import com.android.build.api.dsl.extension.VariantCallbackHandler
import com.android.build.api.dsl.variant.Variant
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.errors.EvalIssueReporter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import org.gradle.api.Action

/**
 * An holder of variant callbacks.
 */
interface VariantCallbackHolder {
    /**
     * Registers the given [Action] to run on variant satisfying the given [VariantPredicate].
     */
    fun register(predicate: VariantPredicate, action: Action<Variant>)

    fun createVariantCallbackHandler(): VariantCallbackHandler<Variant>
}

/**
 * Model data for the variant.
 *
 * This mostly handles the callbacks for the variant.
 */
class VariantModelData(dslScope: DslScope)
    : SealableObject(dslScope), VariantCallbackHolder {

    // map of (predicate, List<callbacks>).
    private val variantCallbacks: ListMultimap<VariantPredicate, Action<Variant>> =
            ArrayListMultimap.create()

    /**
     * Runs the user callback for the given variants.
     *
     * @param variants the variants to run the user callbacks on
     */
    fun runVariantCallbacks(variants: Collection<Variant>) {
        // all callbacks are associated with a predicate that verifies whether the variants must
        // run on the associated actions.
        val predicates = variantCallbacks.keySet()

        // For each variant, test against all predicates and if each succeed then get the
        // list of callback for the predicate and run all the callbacks.
        for (variant in variants) {
            for (predicate in predicates) {
                if (predicate.accept(variant)) {
                    val actions = variantCallbacks[predicate]
                    for (action in actions) {
                        action.execute(variant)
                    }
                }
            }
        }
    }

    override fun createVariantCallbackHandler(): VariantCallbackHandler<Variant> {
        return VariantCallbackHandlerImpl(this, dslScope)
    }

    override fun register(predicate: VariantPredicate, action: Action<Variant>) {
        if (checkSeal()) {
            variantCallbacks.put(predicate, action)
        }
    }
}
