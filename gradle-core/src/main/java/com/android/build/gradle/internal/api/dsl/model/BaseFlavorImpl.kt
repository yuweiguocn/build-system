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

package com.android.build.gradle.internal.api.dsl.model

import com.android.build.api.dsl.model.BaseFlavor
import com.android.build.api.dsl.options.PostProcessingFilesOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import org.gradle.api.Action

class BaseFlavorImpl(dslScope: DslScope)
        : SealableObject(dslScope), BaseFlavor {

    override var wearAppUnbundled: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun missingDimensionStrategy(dimension: String, requestedValue: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun missingDimensionStrategy(dimension: String, vararg requestedValues: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun missingDimensionStrategy(dimension: String, requestedValues: List<String>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun postProcessing(action: Action<PostProcessingFilesOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val postProcessing: PostProcessingFilesOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    fun initWith(that: BaseFlavorImpl) {
        if (checkSeal()) {
            wearAppUnbundled = that.wearAppUnbundled
        }
    }

    // DEPRECATED

    @Suppress("OverridingDeprecatedMember")
    override val generatedDensities: Set<String>?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    @Suppress("OverridingDeprecatedMember")
    override fun setGeneratedDensities(densities: Iterable<String>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override fun wearAppUnbundled(wearAppUnbundled: Boolean?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override var testApplicationId: String?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    @Suppress("OverridingDeprecatedMember")
    override fun testInstrumentationRunnerArgument(key: String, value: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override fun testInstrumentationRunnerArguments(args: Map<String, String>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override var testInstrumentationRunner: String?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    @Suppress("OverridingDeprecatedMember")
    override var testInstrumentationRunnerArguments: Map<String, String>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    @Suppress("OverridingDeprecatedMember")
    override fun setTestHandleProfiling(handleProfiling: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override val testHandleProfiling: Boolean?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    @Suppress("OverridingDeprecatedMember")
    override fun setTestFunctionalTest(functionalTest: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override val testFunctionalTest: Boolean?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
}
