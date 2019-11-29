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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.extension.BuildProperties
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.variant2.DslModelData
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

class BuildPropertiesImpl(
        dslModelData: DslModelData,
        dslScope: DslScope):
        SealableObject(dslScope), BuildProperties {

    override val sourceSets = dslModelData.sourceSets

    override fun sourceSets(action: Action<NamedDomainObjectContainer<out AndroidSourceSet>>) {
        action.execute(sourceSets)
    }

    override var buildToolsVersion: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var compileSdkVersion: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun setCompileSdkVersion(apiLevel: Int) {
        compileSdkVersion = "android-$apiLevel"
    }

    override fun useLibrary(name: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun useLibrary(name: String, required: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var resourcePrefix: String?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun registerTransform(transform: Transform) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val transforms: List<Transform>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val transformsDependencies: List<List<Any>>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    // DEPRECATED

    @Suppress("OverridingDeprecatedMember")
    override fun registerTransform(transform: Transform, vararg dependencies: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}