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

import com.android.build.gradle.internal.variant2.ContainerFactory
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.artifacts.Configuration

/** fake [ContainerFactory] for tests */
class FakeContainerFactory: ContainerFactory {

    override fun <T, U : T> createContainer(itemClass: Class<U>,
            factory: NamedDomainObjectFactory<T>): NamedDomainObjectContainer<T> {

        // Configuration does not implements Named so we cannot make our fake named container
        // only support items that extend Named.

        if (Named::class.java.isAssignableFrom(itemClass)) {
            return FakeNamedDomainObjectContainer(factory, { (it as Named).name })

        } else if (Configuration::class.java.isAssignableFrom(itemClass)) {
            return FakeNamedDomainObjectContainer(factory, { (it as Configuration).name })
        }

        throw RuntimeException("Unsupported item type '${itemClass.name}' in FakeContainerFactory")
    }
}