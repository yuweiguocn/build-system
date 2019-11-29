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

package com.android.build.gradle.internal.dsl.tester.positive

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableMap
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.dsl.tester.ValueInterfaceOne
import com.android.build.gradle.internal.dsl.tester.ValueInterfaceOneImpl
import com.android.builder.errors.EvalIssueReporter

interface TopLevelInterface {

    var booleanProperty: Boolean

    var intProperty: Int

    var valueInterfaceOne: ValueInterfaceOne

    val valueInterfaceOnes : List<ValueInterfaceOne>

    val valueInterfaceOnesMap : Map<String, ValueInterfaceOne>
}

class TopLevelInterfaceImpl(dslScope: DslScope)
    : SealableObject(dslScope), TopLevelInterface {

    override var valueInterfaceOne: ValueInterfaceOne = ValueInterfaceOneImpl(dslScope)
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var booleanProperty: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var intProperty: Int = 123
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override val valueInterfaceOnes : SealableList<ValueInterfaceOne> = SealableList.new(dslScope)

    override val valueInterfaceOnesMap: SealableMap<String, ValueInterfaceOne> =
            SealableMap.new(dslScope)

    override fun seal() {
        super.seal()

        @Suppress("UNCHECKED_CAST")
        (valueInterfaceOne as ValueInterfaceOneImpl).seal()
        @Suppress("UNCHECKED_CAST")
        (valueInterfaceOnes as SealableList<Any>).seal()
        @Suppress("UNCHECKED_CAST")
        (valueInterfaceOnesMap as SealableMap<Any, Any>).seal()
    }
}