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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.Initializable
import org.gradle.api.Incubating

/**
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface InstrumentationOptions : Initializable<InstrumentationOptions> {

    /**
     * Test application ID.
     *
     * See [Set the Application ID](https://developer.android.com/studio/build/application-id.html)
     */
    /** Sets the test application ID.  */
    var applicationId: String?

    /**
     * Test instrumentation runner class name.
     *
     * This is a fully qualified class name of the runner, e.g. `
     * android.test.InstrumentationTestRunner`
     *
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    /** Sets the test instrumentation runner to the given value.  */
    var instrumentationRunner: String?

    /**
     * Test instrumentation runner custom arguments.
     *
     * e.g. `[key: "value"]` will give `
     * adb shell am instrument -w **-e key value** com.example`...".
     *
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     *
     * Test runner arguments can also be specified from the command line:
     * <pre>
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
    </pre> *
     */
    /** Sets the test instrumentation runner custom arguments.  */
    var instrumentationRunnerArguments: MutableMap<String, String>

    /**
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    var handleProfiling: Boolean?

    /**
     * See [instrumentation](http://developer.android.com/guide/topics/manifest/instrumentation-element.html).
     */
    var functionalTest: Boolean?
}
