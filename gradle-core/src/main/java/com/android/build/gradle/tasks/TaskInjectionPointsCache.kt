/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.google.common.base.Joiner
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.UncheckedExecutionException
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses

/**
 * Populate a cache of introspection information for tasks. This information will be used when tasks
 * are allocated and configured to store expected output and information about tasks inputs.
 *
 * The cache is a static field on the class as it never needs to be garbage collected. If the task
 * changes, that means a new plugin has been deliverered and Gradle guarantees a new class loader
 * will be used ensure the cache is always up to date.
 *
 * Cached information is the input and output injection points obtained through reflection.
 */
class TaskInjectionPointsCache {

    /**
     * Defines the kind of injection target we support.
     * So far, only two kind of injection target are supported, input for all input related fields
     * and output for all output related fields.
     */
    private enum class InjectionKind {
        INPUT {
            override fun createInjectionPoint(
                annotatedElement: Method,
                property: KMutableProperty1<Any, Provider<FileSystemLocation>?>,
                id: ArtifactType,
                injectionPoints: InjectionPointsBuilder
            ) {
                injectionPoints.addInput(InputInjectionPoint(property,
                    id,
                    annotatedElement.getDeclaredAnnotation(Current::class.java) == null,
                    annotatedElement.getDeclaredAnnotation(Optional::class.java) != null))
            }

            override fun checkFunction(annotatedElement: Method, annotation: Any, expectedType: KClass<*>) {
                if (annotatedElement.returnType.typeName != BuildableArtifact::class.qualifiedName) {
                    throw RuntimeException("Annotated method does not return a BuildableArtifact")
                }
            }
        },
        OUTPUT {
            private val annotationToOutputNameModels = mapOf(
                Initial::class to Initial::out,
                Replace::class to Replace::out)

            override fun createInjectionPoint(
                annotatedElement: Method,
                property: KMutableProperty1<Any, Provider<FileSystemLocation>?>,
                id: ArtifactType,
                injectionPoints: InjectionPointsBuilder
            ) {

                for ((annotationType, outputNameProvider) in annotationToOutputNameModels) {
                    val annotation = annotatedElement.getAnnotation(annotationType.java)
                    if (annotation != null) {
                        injectionPoints.addOutput(OutputInjectionPoint(property, id,
                            (outputNameProvider as KProperty1<Annotation, String>).get(annotation)))
                        return
                    }
                }
                throw RuntimeException(
                    "Task: ${annotatedElement.declaringClass.name}\n\t" +
                            "Method: ${annotatedElement.toGenericString()}\n\t" +
                            "must be annotated by one of the following annotations : \n\t" +
                            Joiner.on(',').join(annotationToOutputNameModels.keys))
            }

            override fun checkFunction(annotatedElement: Method, annotation: Any, expectedType: KClass<*>) {
                val returnTypeName = annotatedElement.returnType.typeName
                if (returnTypeName != Provider::class.qualifiedName) {
                    throw RuntimeException(
                        "Task: ${annotatedElement.declaringClass.name}\n\t" +
                                "Method: ${annotatedElement.toGenericString()}\n\t" +
                                "annotated with $annotation is expected to return a Provider<${expectedType.simpleName}> but instead returns $returnTypeName")
                }
                val genericReturnType = annotatedElement.genericReturnType
                if (genericReturnType is ParameterizedType) {
                    val typeParameters = genericReturnType.actualTypeArguments
                    if (typeParameters.size != 1) {
                        throw RuntimeException("No parameterized type for Provider<> specified")
                    }
                    if (typeParameters[0].typeName != expectedType.qualifiedName) {
                        throw RuntimeException(
                            "Task: ${annotatedElement.declaringClass.name}\n\t" +
                                    "Method: ${annotatedElement.toGenericString()}\n\t" +
                                    "annotated with $annotation is expected to return a Provider<${expectedType.simpleName}>\n\t" +
                                    "but instead returns Provider<${typeParameters[0].typeName}>")
                    }
                }
            }
        };

        /**
         * check that the provided method for injecting the buildable artifact value is suitable
         * for injection.
         */
        abstract fun checkFunction(annotatedElement: Method, annotation: Any, expectedType: KClass<*>)

        /**
         * Creates the injection point metadata on the provided method. This metadata will be used
         * later to allocate the buildable artifacts and finally inject them into the task instance.
         */
        abstract fun createInjectionPoint(
            annotatedElement: Method,
            property: KMutableProperty1<Any, Provider<FileSystemLocation>?>,
            id: ArtifactType,
            injectionPoints: InjectionPointsBuilder)
    }

    companion object {

        /**
         * Data structure declaring possible signatures of injection points on Task implementation.
         *
         * An injection point has a kind (input for consuming and output for producing) a type
         * which is the field type as defined on the task class (although it really is a Provider
         * of that type) and a fileType which is the file type associated with the expected
         * artifact (a file or directory).
         */
        private data class InjectionPointModel(
            val kind: InjectionKind,
            val injectionPointType: KClass<*>,
            val fileType: ArtifactType.Kind)

        /**
         * Helper object to populate the cache with information issued from reflection.
         *
         * it will go over all methods annotated for injection and will create injection point
         * metadata.
         */
        private val introspector = object : CacheLoader<Class<*>, InjectionPoints>() {

            /**
             * Injection Points model.
             */
            private val injectionModels = mapOf(
                OutputFile::class to InjectionPointModel(
                    InjectionKind.OUTPUT,
                    RegularFile::class,
                    ArtifactType.Kind.FILE
                ),
                OutputDirectory::class to InjectionPointModel(
                    InjectionKind.OUTPUT,
                    Directory::class,
                    ArtifactType.Kind.DIRECTORY
                ),
                InputFiles::class to InjectionPointModel(
                    InjectionKind.INPUT,
                    Directory::class,
                    ArtifactType.Kind.DIRECTORY
                )
            )

            private fun <R> findProperty(kclass: KClass<*>, propertyName: String): KMutableProperty1<Any, R?>? {
                val prop = kclass.memberProperties.find { it.name == propertyName }
                if (prop!=null) {
                    return prop as KMutableProperty1<Any, R?>
                }
                kclass.superclasses.forEach {
                    val superTypeProp = findProperty<R>(it, propertyName)
                    if (superTypeProp != null) return superTypeProp
                }
                return null
            }

            /**
             * Find the annotation that is itself annotated with @ProviderID and extract the ArtifactType
             */
            private fun findID(annotatedElement: AnnotatedElement): ArtifactType? {
                annotatedElement.annotations.forEach{
                    val idProvider = it.annotationClass.findAnnotation<IDProvider>()
                    if (idProvider != null) {
                        it.annotationClass.java.methods[0].invoke(it)
                        val idProviderMethod = it.annotationClass.java.methods.find { method ->
                            method.name == idProvider.fieldName
                        }
                        if (idProviderMethod != null) {
                            val id = idProviderMethod.invoke(it)
                            if (id is ArtifactType) {
                                return id
                            } else {
                                throw RuntimeException(
                                    "$it is annotated with @IdProvider," +
                                            " and the target method is $idProviderMethod, " +
                                            "yet $id is not an instance of ArtifactType")
                            }
                        } else {
                            throw RuntimeException("Cannot find property ${idProvider.fieldName} " +
                                    "on annotation type ${it.annotationClass}")
                        }
                    }
                }
                return null
            }

            override fun load(type: Class<*>?): InjectionPoints {

                if (type == null) {
                    throw RuntimeException("Cannot pass null as a task type to cache.")
                }
                val injectionPoints = InjectionPointsBuilder()

                type.methods
                    .filter { !it.declaringClass.name.startsWith("org.gradle.api") }
                    .forEach {


                    for ((annotationType, injectionPointModel) in injectionModels) {

                        val outputAnnotation = it.getAnnotation(annotationType.java)
                        if (outputAnnotation != null) {

                            val requestedId = findID(it)
                            if (requestedId != null) {
                                val propertyDefinition = findProperty<Provider<FileSystemLocation>>(
                                    type.kotlin,
                                    it.name.substring(3).decapitalize()
                                ) ?: throw RuntimeException("Cannot find property for ${it.name}")

                                if (requestedId.kind() != injectionPointModel.fileType) {
                                    throw RuntimeException(
                                        "Task: ${it.declaringClass.name}\n\t" +
                                                "Method: ${it.toGenericString()}\n\t" +
                                                "annotated with $outputAnnotation expecting a ${injectionPointModel.fileType} \n\t" +
                                                "but its ArtifactID \"${requestedId.name()} is set to be a ${requestedId.kind()}"
                                    )
                                }

                                injectionPointModel.kind.checkFunction(it, outputAnnotation, injectionPointModel.injectionPointType)

                                injectionPointModel.kind.createInjectionPoint(it, propertyDefinition, requestedId, injectionPoints)
                            }
                        }
                    }
                }
                return injectionPoints.build()
            }
        }

        // cache instance, one per class, never garbage collected.
        private val cache : LoadingCache<Class<*>, InjectionPoints>
                = CacheBuilder.newBuilder().build(introspector)

        /**
         * Retrieve or populate a cache entry providing its key (the task configuration class).
         */
        fun getInjectionPoints(type: Class<*>): InjectionPoints {
            try {
                return cache.get(type)
            } catch(e: UncheckedExecutionException) {
                throw e.cause ?: e
            }
        }
    }

    /**
     * Metadata about a declared output injection point on a task implementation.
     */
    data class OutputInjectionPoint(
        // is this making sense that it is an optional Provider like for input ?
        val injectionPoint: KMutableProperty1<Any, Provider<FileSystemLocation>?>,
        var id: ArtifactType,
        val out: String)

    /**
     * Metadata about a declared input injection point on a task implementation.
     */
    data class InputInjectionPoint(
        val injectionPoint: KMutableProperty1<Any, Provider<FileSystemLocation>?>,
        val id: ArtifactType,
        val isFinalVersion: Boolean,
        val isOptional: Boolean
    )

    private class InjectionPointsBuilder {
        val outputs = mutableListOf<OutputInjectionPoint>()
        val inputs = mutableListOf<InputInjectionPoint>()

        fun addOutput(injectionPoint: OutputInjectionPoint) {
            outputs.add(injectionPoint)
        }

        fun addInput(injectionPoint: InputInjectionPoint) {
            inputs.add(injectionPoint)
        }

        fun build() = InjectionPoints(ImmutableList.copyOf(inputs),
            ImmutableList.copyOf(outputs))

    }

    /**
     * Collection of input and output injection points information for a task.
     */
    class InjectionPoints(
        val inputs: Collection<InputInjectionPoint>,
        val outputs: Collection<OutputInjectionPoint>)
}
