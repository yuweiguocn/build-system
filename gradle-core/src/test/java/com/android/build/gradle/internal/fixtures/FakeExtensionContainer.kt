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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf

class FakeExtensionContainer(private val objectFactory: ObjectFactory): ExtensionContainer {

    override fun <T : Any?> create(name: String, type: Class<T>, vararg constructionArguments: Any): T {
        return objectFactory.newInstance(type, *constructionArguments)
    }

    override fun <T : Any?> add(p0: Class<T>?, p1: String?, p2: T) {
        TODO("not implemented")
    }

    override fun <T : Any?> add(p0: TypeOf<T>?, p1: String?, p2: T) {
        TODO("not implemented")
    }

    override fun add(p0: String?, p1: Any?) {
        TODO("not implemented")
    }

    override fun <T : Any?> configure(p0: Class<T>?, p1: Action<in T>?) {
        TODO("not implemented")
    }

    override fun <T : Any?> configure(p0: TypeOf<T>?, p1: Action<in T>?) {
        TODO("not implemented")
    }

    override fun <T : Any?> configure(p0: String?, p1: Action<in T>?) {
        TODO("not implemented")
    }

    override fun getExtraProperties(): ExtraPropertiesExtension {
        TODO("not implemented")
    }

    override fun <T : Any?> create(p0: Class<T>?,
            p1: String?,
            p2: Class<out T>?,
            vararg p3: Any?): T {
        TODO("not implemented")
    }

    override fun <T : Any?> create(p0: TypeOf<T>?,
            p1: String?,
            p2: Class<out T>?,
            vararg p3: Any?): T {
        TODO("not implemented")
    }

    override fun getSchema(): MutableMap<String, TypeOf<*>> {
        TODO("not implemented")
    }

    override fun <T : Any?> getByType(p0: Class<T>?): T {
        TODO("not implemented")
    }

    override fun <T : Any?> getByType(p0: TypeOf<T>?): T {
        TODO("not implemented")
    }

    override fun getByName(p0: String?): Any {
        TODO("not implemented")
    }

    override fun <T : Any?> findByType(p0: Class<T>?): T {
        TODO("not implemented")
    }

    override fun <T : Any?> findByType(p0: TypeOf<T>?): T {
        TODO("not implemented")
    }

    override fun findByName(p0: String?): Any {
        TODO("not implemented")
    }

    override fun getExtensionsSchema(): ExtensionsSchema {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}