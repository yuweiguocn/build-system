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

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.variant.AndroidTestVariant
import com.android.build.api.dsl.variant.ApplicationVariant
import com.android.build.api.dsl.variant.LibraryVariant
import com.android.build.api.dsl.variant.UnitTestVariant
import com.android.build.api.dsl.variant.Variant
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.google.common.truth.Truth
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Assert
import java.util.stream.Collectors

fun <T: Named> getNamed(namedList: Collection<T>, name: String): T? {
    for (named in namedList) {
        if (name == named.name) {
            return named
        }
    }

    return null
}

fun getAppVariant(variants: Collection<Variant>,
        name: String): ApplicationVariant = getVariant(variants, name, ApplicationVariant::class.java)

fun getLibVariant(variants: Collection<Variant>,
        name: String): LibraryVariant = getVariant(variants, name, LibraryVariant::class.java)

fun getAndroidTestVariant(variants: Collection<Variant>,
        name: String):AndroidTestVariant = getVariant(variants, name, AndroidTestVariant::class.java)

fun getUnitTestVariant(variants: Collection<Variant>,
        name: String): UnitTestVariant = getVariant(variants, name, UnitTestVariant::class.java)

fun <T : Variant> getVariant(
        variants: Collection<Variant>,
        name: String,
        variantType: Class<T>): T {
    for (variant in variants) {
        if (variant.name == name) {
            Truth.assertThat(variant).isInstanceOf(variantType)
            return variantType.cast(variant)
        }
    }

    throw AssertionError(String.format("Could not find variant name %s. Existing variants: %s",
            name, variants.map { it.name }))
}

fun <T: BaseExtension2> configure(extension: T, action: T.() -> Unit) {
    action(extension)
}

// Should we make this part of the official API?
fun BaseExtension2.buildTypes(action: NamedDomainObjectContainer<BuildType>.() -> Unit) {
    action(this.buildTypes)
}
