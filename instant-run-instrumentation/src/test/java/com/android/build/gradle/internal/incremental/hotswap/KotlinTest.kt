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

package com.android.build.gradle.internal.incremental.hotswap

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement
import com.google.common.truth.Truth.assertThat
import com.kotlin.JvmOverloadsTest
import com.kotlin.UserClass
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class KotlinTest {

    @Rule
    @JvmField
    val harness = ClassEnhancement()

    @Test
    @Throws(ClassNotFoundException::class,
            IOException::class,
            NoSuchFieldException::class,
            InstantiationException::class,
            IllegalAccessException::class)
    fun methodReferenceUser() {
        harness.reset()

        val userClass = UserClass()
        assertThat(userClass.dontDoMuch()).isEqualTo("my_name with Kotlin ")

        harness.applyPatch("kotlin")
        assertThat(userClass.dontDoMuch()).isEqualTo("my_name with happy Kotlin ")

    }

    @Test
    @Throws(ClassNotFoundException::class,
            IOException::class,
            NoSuchFieldException::class,
            InstantiationException::class,
            IllegalAccessException::class)
    fun jvmOverloadsTest() {
        harness.reset()

        val jvmOverloadsTest = JvmOverloadsTest()
        assertThat(jvmOverloadsTest.doSomething()).isEqualTo("foo")

        harness.applyPatch("kotlin")
        val newValue = JvmOverloadsTest()
        assertThat(newValue.doSomething()).isEqualTo("bar")
    }
}