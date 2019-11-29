/*
 * Copyright (C) 2014 The Android Open Source Project
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

@file:JvmName("ModelHelper")
package com.android.build.gradle.integration.common.utils

import java.util.Optional
import java.util.function.BinaryOperator

/**
 * Utils used by other <Class>Utils extension functions.
 */

fun <T> searchForOptionalItem(
        items: Collection<T>,
        name: String,
        nameFunction: (T) -> String): T? {
    return searchForSingleItemInList(items, name, nameFunction).orElse(null)

}

fun <T> searchForExistingItem(
        items: Collection<T>,
        name: String,
        nameFunction: (T) -> String,
        className: String): T {
    return searchForSingleItemInList(items, name, nameFunction)
            .orElseThrow {
                AssertionError(
                        "Unable to find $className '$name'. Options are: " +
                                items.map(nameFunction))
            }
}

fun <T> searchForSingleItemInList(
        items: Collection<T>,
        name: String,
        nameFunction: (T)-> String): Optional<T> =
        items.stream().filter { name == nameFunction(it) }.reduce(toSingleItem())

/**
 * The goal of this operator is not to reduce anything but to ensure that
 * there is a single item in the list. If it gets called it means
 * that there are two object in the list that had the same name, and this is an error.
 *
 * @see .searchForSingleItemInList
 */
fun <T> toSingleItem(): BinaryOperator<T> {
    return BinaryOperator{ name1, _ ->
        throw IllegalArgumentException("Duplicate objects with name: " + name1)
    }
}
