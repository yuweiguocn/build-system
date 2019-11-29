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

import org.gradle.api.file.ConfigurableFileCollection
import java.io.File

/**
 * A fake [ConfigurableFileCollection] that can be created without [Project].
 */
class FakeConfigurableFileCollection(vararg files : Any?)
    : FakeFileCollection(*files), ConfigurableFileCollection {

    private var _builtBy = mutableSetOf<Any?>()

    override fun from(vararg collection: Any?): ConfigurableFileCollection {
        rawFiles.addAll(collection)
        resolved = false
        return this
    }

    override fun getFrom(): MutableSet<Any> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setFrom(p0: MutableIterable<*>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setFrom(vararg p0: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun builtBy(vararg tasks: Any?): ConfigurableFileCollection {
        tasks.forEach { _builtBy.add(it) }
        return this
    }

    override fun getBuiltBy(): MutableSet<Any?> {
        return _builtBy
    }

    override fun setBuiltBy(tasks: MutableIterable<*>?): ConfigurableFileCollection {
        _builtBy = tasks!!.toMutableSet()
        return this
    }

}