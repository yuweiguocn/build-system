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

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.errors.EvalIssueReporter
import com.google.common.truth.Truth
import org.gradle.api.JavaVersion
import org.junit.Assert
import org.mockito.Mockito
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

/**
 * Utility class to test a {@link SealableObject} implementation :
 *
 * Current list of checks are :
 * <ul>
 * <li>All non final fields are declared as Kotlin Properties.</li>
 * <li>All fields are either immutable (basic types or immutable types like File)
 * or a SealableObject</li>
 * <li>All non final can be set as long as seal() has not been called.</li>
 * </ul>
 *
 * Once {@link SealableObject.seal()} has been called on SealableObject, further checks will
 * be triggered :
 *
 * <ul>
 * <li>All non final fields cannot be reset without generating a IssueReporter Error.</li>
 * <li>All non final fields attempted modification is ignored.</li>
 * <li>All fields which are {@link SealableObject} are also sealed.
 * </ul>
 *
 * @param issueReporter issueReporter used by DSL objects.
 * @param instantiator function capable of instantiating DSL Types (and other more basic types)
 * from a DSL interface.
 * @apram propertyChecker function that will be called after a property value was changed to
 * perform any checks (like ensuring an exception was raised in the issue reporter.
 */
open class SealableImplementationTester(
        private val issueReporter: EvalIssueReporter,
        private val instantiator : (KType) -> Any,
        private val propertyChecker: (KProperty<*>) -> Unit
) {

    val visitedTypes: MutableList<KClass<*>> = mutableListOf()

    /**
     * Check that a {@link SealableType} subtype has been implemented correctly following checks
     * described in the class comment.
     *
     * @param sealableType DSL type interface (not implementation)
     */
    fun checkSealableType(sealableType: KClass<*>) {
        visitedTypes+= sealableType
        val sealableObject = instantiator(sealableType.createType()) as SealableObject
        Truth.assertThat(sealableObject).isInstanceOf(SealableObject::class.java)

        checkAllNonFinalFieldsAreProperties(sealableObject)

        Truth.assertThat(sealableObject.checkSeal()).isTrue()

        val properties = sealableType.memberProperties

        // now set all fields, this should always work as the object has not been sealed,
        // keep a copy of the object we are setting on each property.
        properties.forEach( { property ->
            if (property.visibility != KVisibility.PUBLIC) {
                System.out.println("Skipping non public ${property.name}")
                return@forEach
            }

            checkPropertyValueIsTested(property.returnType)

            if (property is KMutableProperty<*>) {
                System.out.println("setting property ${property.name} : ${property.returnType} : ${property.visibility}")
                property.isAccessible = true
                val propertyValue = instantiator(property.returnType)
                property.setter.call(sealableObject, propertyValue)
            } else {
                System.out.println("reading property ${property.name} : ${property.returnType} : ${property.visibility}")
                // this is most likely a property on which there is only a getter, make sure that
                // if the returned type is a sealable object it should not be sealed yet.
                try {
                    val propertyValue = property.getter.call(sealableObject)
                    if (propertyValue is SealableObject) {
                        Truth.assertWithMessage("Readonly property ${property.name} should not be sealed")
                                .that(propertyValue.isSealed()).isFalse()
                    }
                } catch(e: InvocationTargetException) {
                    // only bypass non implemented.
                    Truth.assertWithMessage("Cannot retrieve ${property.name} value on ${sealableObject::class}")
                            .that(e.targetException is NotImplementedError)
                }
            }
        })

        sealableObject.seal()
        Truth.assertThat(sealableObject.isSealed()).isTrue()

        properties.forEach( { property ->
            System.out.println("testing property ${property.name} : ${property.visibility}")

            try {
                val propertyValue = property.getter.call(sealableObject)
                checkPropertyValueIsSealed(sealableType, property.name, propertyValue)

                if (property is KMutableProperty1<*, *> && property.visibility == KVisibility.PUBLIC) {
                    property.isAccessible = true

                    property.setter.call(sealableObject, instantiator(property.returnType))
                    propertyChecker(property)
                    Mockito.clearInvocations(issueReporter)

                    // make sure the property was not actually mutated.
                    Truth.assertWithMessage("Sealed property ${property.name} was mutate")
                            .that(property.getter.call(sealableObject))
                            .isEqualTo(propertyValue)
                }
            } catch(e: InvocationTargetException) {
                // only bypass non implemented.
                Truth.assertWithMessage("Cannot retrieve ${property.name} value on ${sealableObject::class}")
                        .that(e.targetException is NotImplementedError)
            }
        })
    }

    private fun checkPropertyValueIsSealed(type: KClass<*>, name: String, propertyValue : Any?) {
        if (propertyValue is SealableObject) {
            Truth.assertWithMessage(
                    "Property ${type.simpleName}.$name should be sealed")
                    .that(propertyValue.isSealed())
                    .isTrue()
            return
        }
        // basic and immutable types are fine
        if (propertyValue is Boolean || propertyValue is Double || propertyValue is Int ||
                propertyValue is String || propertyValue is Float || propertyValue is File ||
                propertyValue is JavaVersion) {
            return
        }
        // anything else is suspicious, throw a test failure.
        Assert.fail("Property ${type.simpleName}.$name is not immutable nor sealable." )
    }

    private fun checkPropertyValueIsTested(propertyType : KType) {
        System.out.println("Asserting $propertyType")
        if (propertyType.isSubtypeOf(SealableObject::class.createType())) {
            visitedTypes+= propertyType.jvmErasure
        }
        propertyType.arguments.forEach { argument ->
            if (argument.type != null) {
                checkPropertyValueIsTested(argument.type!!)
            }
        }
    }

    private fun checkAllNonFinalFieldsAreProperties(sealableObject: SealableObject) {
        val properties = sealableObject::class.allSuperclasses.flatMap {
            type -> type.declaredMemberProperties }

        val list = collectFields(sealableObject::class.java)
                .filter { it -> it.modifiers.and(Modifier.FINAL) == 0
                        && it.name != "\$jacocoData"
                        && !isDeclaredAsProperty(properties, it) }

        Truth.assertThat(list).isEmpty()

    }
    private fun isDeclaredAsProperty(properties : List<KProperty<*>>, field : Field) : Boolean {
        return properties.firstOrNull { property -> property.name == field.name } != null
    }


    private fun collectFields(type: Class<*>): List<Field> {
        return getHierarchy(type).flatMap { it -> it.declaredFields.toList() }
    }


    private fun getHierarchy(type: Class<*>) : List<Class<*>> {
        val typeHierarchy = ArrayList<Class<*>>()
        var currentType : Class<*>? = type
        while (currentType != null) {
            typeHierarchy.add(currentType)
            currentType = currentType.superclass
        }
        return typeHierarchy
    }


}