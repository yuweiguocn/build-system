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

import com.android.build.api.dsl.extension.EmbeddedTestProperties
import com.android.build.api.dsl.variant.TestVariant
import com.android.build.api.dsl.variant.UnitTestVariant
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.model.TestOptions
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet

class EmbeddedTestPropertiesImpl(dslScope: DslScope)
        : SealableObject(dslScope), EmbeddedTestProperties {

    override var testBuildType: String? = "debug"
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override val testOptions: TestOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun testOptions(action: Action<TestOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override val testVariants: DomainObjectSet<TestVariant>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    @Suppress("OverridingDeprecatedMember")
    override val unitTestVariants: DomainObjectSet<UnitTestVariant>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun seal() {
        super.seal()
    }
}