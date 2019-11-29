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
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.errors.EvalIssueReporter.Type
import org.gradle.api.Action

internal class VariantCallbackHandlerImpl<T: Variant> private constructor(
        private val predicate: VariantPredicate,
        private val variantCallbackHolder: VariantCallbackHolder,
        private val dslScope: DslScope)
    : VariantCallbackHandler<T> {

    internal constructor(
            variantCallbackHolder: VariantCallbackHolder,
            dslScope: DslScope)
            : this(VariantPredicate(dslScope), variantCallbackHolder, dslScope)

    override fun withName(name: String): VariantCallbackHandler<T> {
        return VariantCallbackHandlerImpl(
                predicate.cloneWithName(name), variantCallbackHolder, dslScope)
    }

    override fun <S : Variant> withType(variantClass: Class<S>): VariantCallbackHandler<S> {
        return VariantCallbackHandlerImpl(
                predicate.cloneWithClass(variantClass), variantCallbackHolder, dslScope)
    }

    override fun withBuildType(name: String): VariantCallbackHandler<T> {
        return VariantCallbackHandlerImpl(
                predicate.cloneWithBuildType(name), variantCallbackHolder, dslScope)
    }

    override fun withProductFlavor(name: String): VariantCallbackHandler<T> {
        return VariantCallbackHandlerImpl(
                predicate.cloneWithFlavor(name), variantCallbackHolder, dslScope)
    }

    override fun all(action: Action<T>) {
        registerAction(action, predicate)
    }

    override fun <S : Variant> withType(variantClass: Class<S>, action: Action<S>) {
        registerAction(action, predicate.cloneWithClass(variantClass))
    }

    override fun withName(name: String, action: Action<T>) {
        registerAction(action, predicate.cloneWithName(name))
    }

    override fun withBuildType(name: String, action: Action<T>) {
        registerAction(action, predicate.cloneWithBuildType(name))
    }

    override fun withProductFlavor(name: String, action: Action<T>) {
        registerAction(action, predicate.cloneWithFlavor(name))
    }

    private fun <V: Variant> registerAction(action: Action<V>, predicate: VariantPredicate) {
        @Suppress("UNCHECKED_CAST")
        variantCallbackHolder.register(predicate, action as Action<Variant>)
    }
}

/**
 * Class representing filters on a variant.
 *
 * This is used to test whether a user callaback must be run against a given variant. Each user
 * callback must be associated with a [VariantPredicate] instance.
 *
 * The class is immutable and is a data class (implements [equals] and [hashCode] so it can be
 * used as a [Map] key.
 *
 * The class also contains utility method to create a new predicate with additional filter
 * parameters.
 */
data class VariantPredicate(
        val name: String?,
        val theClass: Class<*>?,
        val buildTypeName: String?,
        val flavorNames: List<String>?,
        private val dslScope: DslScope) {

    internal constructor(dslScope: DslScope): this(null, null, null, null, dslScope)

    fun accept(variant: Variant): Boolean {
        if (name != null && variant.name != name) {
            return false
        }

        if (theClass != null && !theClass.isInstance(variant)) {
            return false
        }

        if (buildTypeName != null && variant.buildTypeName != buildTypeName) {
            return false
        }

        if (flavorNames != null && flavorNames.isNotEmpty() && !variant.flavorNames.containsAll(flavorNames)) {
            return false
        }

        return true
    }

    /**
     * Creates a new predicate with an additional name-based filter.
     */
    internal fun cloneWithName(name: String): VariantPredicate {
        if (this.name != null) {
            dslScope.issueReporter.reportError(
                    Type.GENERIC, EvalIssueException("Already filtered on variant name"))
        }

        return VariantPredicate(
                name,
                theClass,
                buildTypeName,
                flavorNames,
                dslScope)
    }

    /**
     * Creates a new predicate with an additional class type-based filter.
     */
    internal fun cloneWithClass(variantClass: Class<*>): VariantPredicate {
        if (this.theClass != null) {
            dslScope.issueReporter.reportError(
                    Type.GENERIC,EvalIssueException("Already filtered on variant type"))
        }

        return VariantPredicate(
                name,
                variantClass,
                buildTypeName,
                flavorNames,
                dslScope)
    }


    /**
     * Creates a new predicate with an additional build type name-based filter.
     */
    internal fun cloneWithBuildType(buildTypeName: String): VariantPredicate {
        if (this.buildTypeName != null) {
            dslScope.issueReporter.reportError(
                    Type.GENERIC,EvalIssueException("Already filtered on build type name"))
        }

        return VariantPredicate(
                name,
                theClass,
                buildTypeName,
                flavorNames,
                dslScope)
    }

    /**
     * Creates a new predicate with an additional flavor name-based filter. This adds to the
     * existing flavor names, expecting all of them to be in different dimensions.
     */
    internal fun cloneWithFlavor(flavorName: String): VariantPredicate {
        if (flavorNames == null || flavorNames.isEmpty()) {
            return VariantPredicate(
                    name,
                    theClass,
                    buildTypeName,
                    listOf(flavorName),
                    dslScope)
        }

        // FIXME we should double check we're not adding a new name from a dimension already
        // filtered on
        val flavors = ArrayList(flavorNames)
        flavors.add(flavorName)

        return VariantPredicate(
                name,
                theClass,
                buildTypeName,
                flavors,
                dslScope)
    }

}