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

import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.internal.reflect.JavaReflectionUtil
import java.lang.reflect.Constructor

/**
 * a fake [ObjectFactory] to used in tests.
 *
 * This just calls the constructor directly.
 *
 */
class FakeObjectFactory : ObjectFactory {
    override fun <K : Any?, V : Any?> mapProperty(p0: Class<K>, p1: Class<V>): MapProperty<K, V> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sourceDirectorySet(p0: String, p1: String): SourceDirectorySet {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun directoryProperty(): DirectoryProperty {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun fileProperty(): RegularFileProperty {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : Any?> newInstance(theClass: Class<out T>, vararg constructorParams: Any?): T {
        @Suppress("UNCHECKED_CAST")
        val constructors: Array<out Constructor<T>> = theClass.declaredConstructors as Array<out Constructor<T>>

        val actualParamsTypes = getParamTypes(constructorParams)

        for (constructor in constructors) {
            if (checkCompatibility(actualParamsTypes, constructor.parameterTypes)) {
                return constructor.newInstance(*constructorParams)
            }
        }

        throw RuntimeException("Failed to find matching constructor for $actualParamsTypes")
    }

    override fun <T : Any?> property(p0: Class<T>?): Property<T> {
        TODO("not implemented")
    }

    override fun <T : Named?> named(p0: Class<T>?, p1: String?): T {
        TODO("not implemented")
    }

    override fun <T : Any?> listProperty(p0: Class<T>?): ListProperty<T> {
        TODO("not implemented")
    }

    override fun <T : Any?> setProperty(p0: Class<T>?): SetProperty<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun getParamTypes(params: Array<out Any?>): Array<Class<*>?> {
        val result = arrayOfNulls<Class<*>>(params.size)

        for (i in result.indices) {
            val param = params[i]
            if (param != null) {
                var pType: Class<*> = param.javaClass
                if (pType.isPrimitive) {
                    pType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(pType)
                }

                result[i] = pType
            }
        }

        return result
    }

    private fun checkCompatibility(argumentTypes: Array<Class<*>?>, parameterTypes: Array<Class<*>?>): Boolean {
        if (argumentTypes.size != parameterTypes.size) {
            return false
        }

        for (i in argumentTypes.indices) {
            val argumentType = argumentTypes[i]
            var parameterType: Class<*>? = parameterTypes[i]

            val primitive = parameterType?.isPrimitive ?: false
            if (primitive) {
                if (argumentType == null) {
                    return false
                }

                parameterType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(parameterType)
            }

            if (argumentType != null && !parameterType!!.isAssignableFrom(argumentType)) {
                return false
            }
        }

        return true
    }

}