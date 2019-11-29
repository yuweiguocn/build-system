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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.TypedValue
import com.android.build.api.dsl.options.ExternalNativeBuildOptions
import com.android.build.api.dsl.options.JavaCompileOptions
import com.android.build.api.dsl.options.NdkOptions
import com.android.build.api.dsl.options.PostProcessingOptions
import com.android.build.api.dsl.options.ShaderOptions
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.FallbackStrategyImpl
import com.android.build.gradle.internal.api.dsl.model.TypedValueImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.options.ExternalNativeBuildOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.JavaCompileOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.NdkOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.PostProcessingFilesOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.PostProcessingOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.ShaderOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.builder.errors.EvalIssueReporter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.reflect.ClassPath
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.api.JavaVersion
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

class DslImplementationSealableTest {

    @Mock lateinit var issueReporter: EvalIssueReporter
    @Mock lateinit var deprecationReporter: DeprecationReporter
    lateinit var dslScope: DslScope

    val dslTypes : List<KClass<out Any>> = listOf(
            BuildType::class,
            SigningConfig::class,
            ExternalNativeBuildOptions::class,
            JavaCompileOptions::class,
            NdkOptions::class,
            ShaderOptions::class,
            PostProcessingOptions::class)

    val testedTypes: MutableList<KClass<*>> = mutableListOf()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        dslScope = DslScopeImpl(issueReporter, deprecationReporter, FakeObjectFactory())

    }

    private fun getDslInterfaces(classPath: ClassPath) =
            classPath.getTopLevelClasses("com.android.build.api.dsl")
                    .map(ClassPath.ClassInfo::load)

    /**
     * Finds all top level classes in "com.android.build.api.dsl", and for each class, it will run testApi
     */
    @Test
    fun findAllDslInterfaces() {
        val classpath = ClassPath.from(this.javaClass.classLoader)
        getDslInterfaces(classpath).forEach(this::testApi)
    }

    /**
     * Test a DSL API implementation.
     *
     * so far, only loads the class and ensure it is loaded successfully.
     */
    private fun testApi(apiClass : Class<*>) {
        assertThat(apiClass.isInterface)
                .named("""${apiClass.name} is interface""")
                .isTrue()
    }

    @Test
    fun realTest() {
        val tester = SealableImplementationTester(
                issueReporter, this::instantiate, this::propertyChecker)

        dslTypes.forEach({it ->
            System.out.println("Testing type ${it.simpleName}")
            testedTypes.add(it)
            tester.checkSealableType(it)
        })

        // check that all visited types are tested.
        tester.visitedTypes.removeAll(testedTypes)
        Truth.assertWithMessage(
                "All visited types should be tested")
                .that(tester.visitedTypes).isEmpty()
    }

    private fun instantiate(type: KType): Any = when(type.classifier) {
        // Basic Types
        Boolean::class -> java.lang.Boolean.TRUE
        Int::class -> 12
        String::class -> "abc"
        Any::class -> Object()

        // Kotlin Types
        MutableList::class -> MutableList(4) { _ -> instantiate(type.arguments[0].type!!) }
        MutableMap::class -> mutableMapOf(
                Pair(instantiate(type.arguments[0].type!!), instantiate(type.arguments[1].type!!)))
        MutableSet::class -> mutableSetOf(instantiate(type.arguments[0].type!!))

        // Java Types
        File::class -> File("/not/real/file")

        // Guava Types
        ListMultimap::class -> ArrayListMultimap.create<Any, Any>(2, 2)

        // DSL types
        TypedValue::class -> TypedValueImpl("type", "type_name", "type_value")
        SigningConfig::class -> SigningConfigImpl("signing", dslScope)
        BuildType::class -> BuildTypeImpl(
                "foo",
                VariantPropertiesImpl(dslScope),
                BuildTypeOrProductFlavorImpl(dslScope) {
                    PostProcessingFilesOptionsImpl(dslScope)
                },
                BuildTypeOrVariantImpl("buildType", dslScope),
                FallbackStrategyImpl(dslScope),
                dslScope)

        ExternalNativeBuildOptions::class -> ExternalNativeBuildOptionsImpl(dslScope)
        JavaCompileOptions::class -> JavaCompileOptionsImpl(dslScope)
        NdkOptions::class -> NdkOptionsImpl(dslScope)
        ShaderOptions::class -> ShaderOptionsImpl(dslScope)
        PostProcessingOptions::class -> PostProcessingOptionsImpl(dslScope)

        JavaVersion::class -> JavaVersion.VERSION_1_8

        else -> throw IllegalArgumentException("I don't know how to instantiate $type")
    }

    private fun propertyChecker(property: KProperty<*>) {
        Logger.getAnonymousLogger().log(Level.FINE, "propertyCheck : ${property.name}")
//        Mockito.verify(issueReporter).reportError(Mockito.eq(SyncIssue.TYPE_GENERIC),
//                Mockito.anyString(),
//                Mockito.anyString())
    }
}
