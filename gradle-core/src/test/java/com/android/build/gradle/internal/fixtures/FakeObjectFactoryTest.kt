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

import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class FakeObjectFactoryTest {

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @Test
    fun noParam() {
        val instance = FakeObjectFactory().newInstance(TestClass::class.java)

        Truth.assertThat(instance.foo).named("Foo property").isNull()
    }

    @Test
    fun correctParam() {
        val instance = FakeObjectFactory().newInstance(TestClass::class.java, "value")

        Truth.assertThat(instance.foo).named("Foo property").isEqualTo("value")
    }

    @Test
    fun wrongParam() {
        exception.expect(RuntimeException::class.java)
        FakeObjectFactory().newInstance(TestClass::class.java, "value1", "value1")
    }

    @Test
    fun superTypeSupport() {
        val objectFactory = FakeObjectFactory()

        // create a TestClassChild on its own
        val testClassChild = objectFactory.newInstance(TestClassChild::class.java)

        // create another one using the first one as param
        objectFactory.newInstance(TestClassChild::class.java, testClassChild)
    }
}

// test classes for the instantiator.
open class TestClass(val foo: String?) {
    constructor(): this(null)
}

class TestClassChild(testClass: TestClass?): TestClass(testClass?.foo) {
    constructor(): this(null)
}