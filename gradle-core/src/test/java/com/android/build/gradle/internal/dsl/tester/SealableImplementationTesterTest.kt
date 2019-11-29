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

package com.android.build.gradle.internal.dsl.tester

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dsl.SealableImplementationTester
import com.android.build.gradle.internal.dsl.tester.negative.BooleanNotSealed
import com.android.build.gradle.internal.dsl.tester.negative.BooleanNotSealedImpl
import com.android.build.gradle.internal.dsl.tester.negative.UnprotectedFinalList
import com.android.build.gradle.internal.dsl.tester.negative.UnprotectedFinalListImpl
import com.android.build.gradle.internal.dsl.tester.negative.UnprotectedFinalMap
import com.android.build.gradle.internal.dsl.tester.negative.UnprotectedFinalMapImpl
import com.android.build.gradle.internal.dsl.tester.positive.TopLevelInterface
import com.android.build.gradle.internal.dsl.tester.positive.TopLevelInterfaceImpl
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import kotlin.reflect.KType

open class SealableImplementationTesterTest {

    @Mock
    lateinit var issueReporter: EvalIssueReporter
    @Mock
    lateinit var deprecationReporter: DeprecationReporter

    lateinit var dslScope: DslScope

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        dslScope = DslScopeImpl(issueReporter, deprecationReporter, FakeObjectFactory())
    }

    @Test
    fun topLevelPositiveTest() {
        val sealedProperties = mutableListOf(
                "booleanProperty",
                "intProperty",
                "valueInterfaceOne",
                "valueInterfaceOneProperty")
        val tester = SealableImplementationTester(issueReporter,
                this::instantiate) { property ->
//            Mockito.verify(issueReporter).reportError(EvalIssueReporter.Type.GENERIC,
//                    Mockito.anyString(),
//                    Mockito.anyString())
            Truth.assertWithMessage("${property.name} not in list of properties")
                    .that(sealedProperties).contains(property.name)
            sealedProperties.remove(property.name)
        }

        tester.checkSealableType(TopLevelInterface::class)
        tester.checkSealableType(ValueInterfaceOne::class)
        Truth.assertThat(sealedProperties).isEmpty()
        Truth.assertThat(tester.visitedTypes).containsExactly(
                TopLevelInterface::class,
                ValueInterfaceOne::class
        )
    }

    @Test
    fun booleanNotSealedTest() {

        val tester = SealableImplementationTester(issueReporter, this::instantiate) { property ->
            Truth.assertThat(property.name).isEqualTo("booleanProperty")
//            Truth.assertThat(property.getter.call(booleanNotSealed)).isEqualTo(true)
            Mockito.verifyNoMoreInteractions(issueReporter)
        }

        tester.checkSealableType(BooleanNotSealed::class)
    }

    @Test(expected = AssertionError::class)
    fun unprotectedFinalListTest() {
        val tester = SealableImplementationTester(issueReporter, this::instantiate) { _ ->
            Mockito.verifyNoMoreInteractions(issueReporter)
        }
        tester.checkSealableType(UnprotectedFinalList::class)
    }

    @Test(expected = AssertionError::class)
    fun unprotectedFinalMapTest() {
        val tester = SealableImplementationTester(issueReporter, this::instantiate) { _ ->
            Mockito.verifyNoMoreInteractions(issueReporter)
        }
        tester.checkSealableType(UnprotectedFinalMap::class)
    }

    private fun instantiate(type: KType): Any {
        when(type.classifier) {
            Boolean::class -> return true
            Int::class -> return 321
            String::class -> return "String"

            TopLevelInterface::class -> return TopLevelInterfaceImpl(dslScope)
            ValueInterfaceOne::class -> return ValueInterfaceOneImpl(dslScope)
            UnprotectedFinalList::class -> return UnprotectedFinalListImpl(dslScope)
            UnprotectedFinalMap::class -> return UnprotectedFinalMapImpl(dslScope)
            BooleanNotSealed::class -> return BooleanNotSealedImpl(dslScope)
        }
        throw IllegalArgumentException("Do not know how to instantiate $type")
    }
}