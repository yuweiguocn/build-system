/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.api.dsl.extension

import com.android.build.api.dsl.variant.TestVariant
import com.android.build.api.dsl.variant.UnitTestVariant
import com.android.builder.model.TestOptions
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Incubating

/** Partial extension properties for modules that contain tests
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface EmbeddedTestProperties {
    /** Return the name of the BuildType used when running connectedCheck. If null, runs the
     * androidTests for all variants.
     *
     * Default is 'debug'
     */
    var testBuildType: String?

    /** Options for running tests.  */
    val testOptions: TestOptions

    fun testOptions(action: Action<TestOptions>)

    /**
     * Returns the list of (Android) test variants. Since the collections is built after evaluation,
     * it should be used with [DomainObjectSet.all] to process future items.
     */
    @Deprecated("Use variants property")
    val testVariants: DomainObjectSet<TestVariant>

    /**
     * Returns the list of (Android) test variants. Since the collections is built after evaluation,
     * it should be used with [DomainObjectSet.all] to process future items.
     */
    @Deprecated("Use variants property")
    val unitTestVariants: DomainObjectSet<UnitTestVariant>
}
