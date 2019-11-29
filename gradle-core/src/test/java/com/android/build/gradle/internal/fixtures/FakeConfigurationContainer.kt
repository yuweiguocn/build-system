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

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollectionSchema
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency

class FakeConfigurationContainer
    : FakeNamedDomainObjectContainer<Configuration>(FakeConfigurationFactory(), {it.name}),
        ConfigurationContainer {

    override fun detachedConfiguration(vararg p0: Dependency?): Configuration {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun register(
        p0: String?,
        p1: Action<in Configuration>?
    ): NamedDomainObjectProvider<Configuration> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun register(p0: String?): NamedDomainObjectProvider<Configuration> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun named(p0: String?): NamedDomainObjectProvider<Configuration> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCollectionSchema(): NamedDomainObjectCollectionSchema {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class FakeConfigurationFactory: NamedDomainObjectFactory<Configuration> {
    override fun create(name: String): Configuration {
        return FakeConfiguration(name)
    }
}
