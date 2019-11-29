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

package com.android.build.gradle.internal.publishing

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.BUNDLE_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.METADATA_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
import com.android.build.gradle.internal.scope.InternalArtifactType.AIDL_PARCELABLE
import com.android.build.gradle.internal.scope.InternalArtifactType.APK
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING
import com.android.build.gradle.internal.scope.InternalArtifactType.APP_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.AAPT_PROGUARD_FILE
import com.android.build.gradle.internal.scope.InternalArtifactType.BUNDLE
import com.android.build.gradle.internal.scope.InternalArtifactType.BUNDLE_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.CONSUMER_PROGUARD_FILE
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DEFINED_ONLY_SYMBOL_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_BASE_MODULE_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_AND_RUNTIME_DEPS_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_SET_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_TRANSITIVE_DEPS
import com.android.build.gradle.internal.scope.InternalArtifactType.FULL_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JNI
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PUBLISH_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_INSTALLED_BASE_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_FEATURE_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_LIBRARY_DEPENDENCIES_REPORT
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_FEATURE_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.SIGNING_CONFIG
import com.android.build.gradle.internal.scope.InternalArtifactType.MODULE_BUNDLE
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLIC_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_HEADERS
import com.android.build.gradle.internal.scope.InternalArtifactType.RES_STATIC_LIBRARY
import com.android.build.gradle.internal.scope.InternalArtifactType.SYMBOL_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
import com.android.build.gradle.internal.scope.AnchorOutputType.ALL_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_LIBRARY_CLASSES
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap

/**
 * Publishing spec for variants and tasks outputs.
 *
 *
 * This builds a bi-directional mapping between task outputs and published artifacts (for project
 * to project publication), as well as where to publish the artifact (with
 * [org.gradle.api.artifacts.Configuration] via the [PublishedConfigType] enum.)
 *
 *
 * This mapping is per [VariantType] to allow for different task outputs to be published
 * under the same [ArtifactType].
 *
 *
 * This mapping also offers reverse mapping override for tests (per [VariantType] as well),
 * allowing a test variant to not use exactly the published artifact of the tested variant but a
 * different version. This allows for instance the unit tests of libraries to use the full Java
 * classes, including the R class for unit testing, while the published artifact does not contain
 * the R class. Similarly, the override can extend the published scope (api vs runtime), which is
 * needed to run the unit tests.
 */
class PublishingSpecs {

    /**
     * The publishing spec for a variant
     */
    interface VariantSpec {
        val variantType: VariantType
        val outputs: Set<OutputSpec>
        val testingSpecs: Map<VariantType, VariantSpec>

        fun getTestingSpec(variantType: VariantType): VariantSpec

        fun getSpec(artifactType: ArtifactType, publishConfigType: PublishedConfigType?): OutputSpec?
    }

    /**
     * A published output
     */
    interface OutputSpec {
        val outputType: com.android.build.api.artifact.ArtifactType
        val artifactType: ArtifactType
        val publishedConfigTypes: ImmutableList<PublishedConfigType>
    }

    companion object {
        private var builder: ImmutableMap.Builder<VariantType, VariantSpec>? = ImmutableMap.builder()
        private lateinit var variantMap: Map<VariantType, VariantSpec>

        init {
            variantSpec(VariantTypeImpl.BASE_APK) {

                api(MANIFEST_METADATA, ArtifactType.MANIFEST_METADATA)
                // use TYPE_JAR to give access to this via the model for now,
                // the JarTransform will convert it back to CLASSES
                // FIXME: stop using TYPE_JAR for APK_CLASSES
                api(APP_CLASSES, ArtifactType.JAR)
                api(APP_CLASSES, ArtifactType.CLASSES)
                api(APK_MAPPING, ArtifactType.APK_MAPPING)

                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)

                // FIXME: need data binding artifacts as well for Dynamic apps.

                runtime(APK, ArtifactType.APK)
                runtime(InternalArtifactType.APKS_FROM_BUNDLE, ArtifactType.APKS_FROM_BUNDLE)
                runtime(FEATURE_TRANSITIVE_DEPS, ArtifactType.FEATURE_TRANSITIVE_DEPS)

                metadata(METADATA_INSTALLED_BASE_DECLARATION, ArtifactType.METADATA_BASE_MODULE_DECLARATION)

                metadata(BUNDLE_MANIFEST, ArtifactType.BUNDLE_MANIFEST)

                // output of bundle-tool
                metadata(BUNDLE, ArtifactType.BUNDLE)

                // this is only for base modules.
                api(FEATURE_SET_METADATA, ArtifactType.FEATURE_SET_METADATA)
                api(METADATA_BASE_MODULE_DECLARATION,
                    ArtifactType.FEATURE_APPLICATION_ID_DECLARATION)
                api(SIGNING_CONFIG, ArtifactType.FEATURE_SIGNING_CONFIG)


                // ----

                testSpec(VariantTypeImpl.ANDROID_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping
                    api(APP_CLASSES, ArtifactType.CLASSES)
                }

                testSpec(VariantTypeImpl.UNIT_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping. Also add it to the runtime as it's
                    // needed to run the tests!
                    output(ALL_CLASSES, ArtifactType.CLASSES)
                    // JAVA_RES isn't published by the app, but we need it for the unit tests
                    output(JAVA_RES, ArtifactType.JAVA_RES)
                }
            }

            variantSpec(VariantTypeImpl.OPTIONAL_APK) {

                api(MANIFEST_METADATA, ArtifactType.MANIFEST_METADATA)
                // use TYPE_JAR to give access to this via the model for now,
                // the JarTransform will convert it back to CLASSES
                // FIXME: stop using TYPE_JAR for APK_CLASSES
                api(APP_CLASSES, ArtifactType.JAR)
                api(APP_CLASSES, ArtifactType.CLASSES)
                api(APK_MAPPING, ArtifactType.APK_MAPPING)

                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)

                // FIXME: need data binding artifacts as well for Dynamic apps.

                runtime(APK, ArtifactType.APK)
                runtime(FEATURE_TRANSITIVE_DEPS, ArtifactType.FEATURE_TRANSITIVE_DEPS)

                // The intermediate bundle containing only this module. Input for bundle-tool
                metadata(MODULE_BUNDLE, ArtifactType.MODULE_BUNDLE)
                metadata(BUNDLE_MANIFEST, ArtifactType.BUNDLE_MANIFEST)
                metadata(METADATA_LIBRARY_DEPENDENCIES_REPORT, ArtifactType.LIB_DEPENDENCIES)

                // this is only for non-base modules.
                metadata(METADATA_FEATURE_DECLARATION, ArtifactType.METADATA_FEATURE_DECLARATION)
                metadata(METADATA_FEATURE_MANIFEST, ArtifactType.METADATA_FEATURE_MANIFEST)
                metadata(MODULE_AND_RUNTIME_DEPS_CLASSES, ArtifactType.METADATA_CLASSES)
                metadata(FEATURE_AND_RUNTIME_DEPS_JAVA_RES, ArtifactType.METADATA_JAVA_RES)
                metadata(CONSUMER_PROGUARD_FILE, ArtifactType.CONSUMER_PROGUARD_RULES)
                metadata(AAPT_PROGUARD_FILE, ArtifactType.AAPT_PROGUARD_RULES)
                metadata(FEATURE_TRANSITIVE_DEPS, ArtifactType.FEATURE_TRANSITIVE_DEPS)

                // ----

                testSpec(VariantTypeImpl.ANDROID_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping
                    api(APP_CLASSES, ArtifactType.CLASSES)
                }

                testSpec(VariantTypeImpl.UNIT_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping. Also add it to the runtime as it's
                    // needed to run the tests!
                    output(ALL_CLASSES, ArtifactType.CLASSES)
                    // JAVA_RES isn't published by the app, but we need it for the unit tests
                    output(JAVA_RES, ArtifactType.JAVA_RES)
                }
            }


            variantSpec(VariantTypeImpl.LIBRARY) {
                api(COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
                        ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
                api(AIDL_PARCELABLE, ArtifactType.AIDL)
                api(RENDERSCRIPT_HEADERS, ArtifactType.RENDERSCRIPT)
                api(COMPILE_LIBRARY_CLASSES, ArtifactType.CLASSES)

                // manifest is published to both to compare and detect provided-only library
                // dependencies.
                output(LIBRARY_MANIFEST, ArtifactType.MANIFEST)
                output(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                output(DATA_BINDING_ARTIFACT, ArtifactType.DATA_BINDING_ARTIFACT)
                output(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                        ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                output(FULL_JAR, ArtifactType.JAR)

                runtime(RUNTIME_LIBRARY_CLASSES, ArtifactType.CLASSES)
                runtime(LIBRARY_ASSETS, ArtifactType.ASSETS)
                runtime(PACKAGED_RES, ArtifactType.ANDROID_RES)
                runtime(PUBLIC_RES, ArtifactType.PUBLIC_RES)
                runtime(SYMBOL_LIST, ArtifactType.SYMBOL_LIST)
                runtime(SYMBOL_LIST_WITH_PACKAGE_NAME, ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
                runtime(DEFINED_ONLY_SYMBOL_LIST, ArtifactType.DEFINED_ONLY_SYMBOL_LIST)
                runtime(LIBRARY_JAVA_RES, ArtifactType.JAVA_RES)
                runtime(CONSUMER_PROGUARD_FILE, ArtifactType.CONSUMER_PROGUARD_RULES)
                runtime(LIBRARY_JNI, ArtifactType.JNI)
                runtime(LINT_PUBLISH_JAR, ArtifactType.LINT)

                testSpec(VariantTypeImpl.UNIT_TEST) {
                    // unit test need ALL_CLASSES instead of RUNTIME_LIBRARY_CLASSES to get
                    // access to the R class. Also scope should be API+Runtime.
                    output(ALL_CLASSES, ArtifactType.CLASSES)
                }
            }

            variantSpec(VariantTypeImpl.BASE_FEATURE) {

                api(FEATURE_SET_METADATA, ArtifactType.FEATURE_SET_METADATA)
                api(METADATA_BASE_MODULE_DECLARATION,
                        ArtifactType.FEATURE_APPLICATION_ID_DECLARATION)

                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)
                api(APP_CLASSES, ArtifactType.CLASSES)
                api(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                api(COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
                        ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)

                runtime(FEATURE_TRANSITIVE_DEPS, ArtifactType.FEATURE_TRANSITIVE_DEPS)
                runtime(APK, ArtifactType.APK)

                api(DATA_BINDING_ARTIFACT, ArtifactType.DATA_BINDING_ARTIFACT)
                api(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                        ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                api(SIGNING_CONFIG, ArtifactType.FEATURE_SIGNING_CONFIG)
            }

            variantSpec(VariantTypeImpl.FEATURE) {
                metadata(METADATA_FEATURE_DECLARATION, ArtifactType.METADATA_FEATURE_DECLARATION)
                metadata(METADATA_FEATURE_MANIFEST, ArtifactType.METADATA_FEATURE_MANIFEST)
                metadata(MODULE_AND_RUNTIME_DEPS_CLASSES, ArtifactType.METADATA_CLASSES)
                metadata(FEATURE_AND_RUNTIME_DEPS_JAVA_RES, ArtifactType.METADATA_JAVA_RES)
                metadata(CONSUMER_PROGUARD_FILE, ArtifactType.CONSUMER_PROGUARD_RULES)
                metadata(AAPT_PROGUARD_FILE, ArtifactType.AAPT_PROGUARD_RULES)
                metadata(FEATURE_TRANSITIVE_DEPS, ArtifactType.FEATURE_TRANSITIVE_DEPS)
                metadata(MODULE_BUNDLE, ArtifactType.MODULE_BUNDLE)
                metadata(METADATA_LIBRARY_DEPENDENCIES_REPORT, ArtifactType.LIB_DEPENDENCIES)

                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)
                api(APP_CLASSES, ArtifactType.CLASSES)
                api(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                api(COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
                    ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)

                runtime(FEATURE_TRANSITIVE_DEPS, ArtifactType.FEATURE_TRANSITIVE_DEPS)
                runtime(APK, ArtifactType.APK)

                api(DATA_BINDING_ARTIFACT, ArtifactType.DATA_BINDING_ARTIFACT)
                api(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                    ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
            }

            // empty specs
            variantSpec(VariantTypeImpl.TEST_APK)
            variantSpec(VariantTypeImpl.ANDROID_TEST)
            variantSpec(VariantTypeImpl.UNIT_TEST)
            variantSpec(VariantTypeImpl.INSTANTAPP)

            lock()
        }

        @JvmStatic
        fun getVariantSpec(variantType: VariantType): VariantSpec {
            return variantMap[variantType]!!
        }

        @JvmStatic
        internal fun getVariantMap(): Map<VariantType, VariantSpec> {
            return variantMap
        }

        private fun lock() {
            variantMap = builder!!.build()
            builder = null
        }

        private fun variantSpec(
                variantType: VariantType,
                action: VariantSpecBuilder<com.android.build.api.artifact.ArtifactType>.() -> Unit) {
            val specBuilder = VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>(
                    variantType)
            action(specBuilder)
            builder!!.put(variantType, specBuilder.toSpec())
        }

        private fun variantSpec(variantType: VariantType) {
            builder!!.put(variantType, VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>(
                    variantType).toSpec())
        }
    }

    interface VariantSpecBuilder<in T : com.android.build.api.artifact.ArtifactType> {
        val variantType: VariantType

        fun output(taskOutputType: T, action: OutputSpecBuilder.() -> Unit)
        fun output(taskOutputType: T, artifactType: ArtifactType)
        fun api(taskOutputType: T, artifactType: ArtifactType)
        fun runtime(taskOutputType: T, artifactType: ArtifactType)
        fun metadata(taskOutputType: T, artifactType: ArtifactType)
        fun bundle(taskOutputType: T, artifactType: ArtifactType)

        fun testSpec(variantType: VariantType, action: VariantSpecBuilder<com.android.build.api.artifact.ArtifactType>.() -> Unit)
    }

    interface OutputSpecBuilder: OutputSpec {
        override var artifactType: ArtifactType
        override var publishedConfigTypes: ImmutableList<PublishedConfigType>
    }

}

private val API_ELEMENTS_ONLY = ImmutableList.of(API_ELEMENTS)
private val RUNTIME_ELEMENTS_ONLY = ImmutableList.of(RUNTIME_ELEMENTS)
private val API_AND_RUNTIME_ELEMENTS = ImmutableList.of(API_ELEMENTS, RUNTIME_ELEMENTS)
private val METADATA_ELEMENTS_ONLY = ImmutableList.of(METADATA_ELEMENTS)
private val BUNDLE_ELEMENTS_ONLY = ImmutableList.of(BUNDLE_ELEMENTS)

// --- Implementation of the public Spec interfaces

private class VariantPublishingSpecImpl(
        override val variantType: VariantType,
        private val parentSpec: PublishingSpecs.VariantSpec?,
        override val outputs: Set<PublishingSpecs.OutputSpec>,
        testingSpecBuilders: Map<VariantType, VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>>
) : PublishingSpecs.VariantSpec {

    override val testingSpecs: Map<VariantType, PublishingSpecs.VariantSpec>
    private var _artifactMap: Map<ArtifactType, List<PublishingSpecs.OutputSpec>>? = null

    private val artifactMap: Map<ArtifactType, List<PublishingSpecs.OutputSpec>>
        get() {
            val map = _artifactMap
            return if (map == null) {
                val map2 = outputs.groupBy { it.artifactType }
                _artifactMap = map2
                map2
            } else {
                map
            }
        }

    init {
        testingSpecs = testingSpecBuilders.toImmutableMap { it.toSpec(this) }
    }

    override fun getTestingSpec(variantType: VariantType): PublishingSpecs.VariantSpec {
        Preconditions.checkState(variantType.isTestComponent)

        val testingSpec = testingSpecs[variantType]
        return testingSpec ?: this
    }

    override fun getSpec(
        artifactType: ArtifactType,
        publishConfigType: PublishedConfigType?
    ): PublishingSpecs.OutputSpec? {
        return artifactMap[artifactType]?.let {specs ->
            if (specs.size <= 1) {
                specs.singleOrNull()
            } else {
                val matchingSpecs = if (publishConfigType != null) {
                    specs.filter { it.publishedConfigTypes.contains(publishConfigType) }
                } else {
                    specs
                }
                if (matchingSpecs.size > 1) {
                    throw IllegalStateException("Multiple output specs found for $artifactType and $publishConfigType")
                } else {
                    matchingSpecs.singleOrNull()
                }
            }
        } ?: parentSpec?.getSpec(artifactType, publishConfigType)
    }
}

private data class OutputSpecImpl(
        override val outputType: com.android.build.api.artifact.ArtifactType,
        override val artifactType: ArtifactType,
        override val publishedConfigTypes: ImmutableList<PublishedConfigType>) : PublishingSpecs.OutputSpec

// -- Implementation of the internal Spec Builder interfaces

private class VariantSpecBuilderImpl<in T : com.android.build.api.artifact.ArtifactType>(
        override val variantType: VariantType): PublishingSpecs.VariantSpecBuilder<T> {

    private val outputs = mutableSetOf<PublishingSpecs.OutputSpec>()
    private val testingSpecs = mutableMapOf<VariantType, VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>>()

    override fun output(
            taskOutputType: T,
            action: (PublishingSpecs.OutputSpecBuilder) -> Unit) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        action(specBuilder)

        outputs.add(specBuilder.toSpec())
    }

    override fun output(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        outputs.add(specBuilder.toSpec())
    }

    override fun api(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = API_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun runtime(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = RUNTIME_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun metadata(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = METADATA_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun bundle(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = BUNDLE_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun testSpec(
            variantType: VariantType,
            action: PublishingSpecs.VariantSpecBuilder<com.android.build.api.artifact.ArtifactType>.() -> Unit) {
        Preconditions.checkState(!this.variantType.isForTesting)
        Preconditions.checkState(variantType.isTestComponent)
        Preconditions.checkState(!testingSpecs.containsKey(variantType))

        val specBuilder = VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>(
                variantType)
        action(specBuilder)

        testingSpecs[variantType] = specBuilder
    }

    fun toSpec(parentSpec: PublishingSpecs.VariantSpec? = null): PublishingSpecs.VariantSpec {
        return VariantPublishingSpecImpl(
                variantType,
                parentSpec,
                outputs.toImmutableSet(),
                testingSpecs)
    }
}

private class OutputSpecBuilderImpl(override val outputType: com.android.build.api.artifact.ArtifactType) : PublishingSpecs.OutputSpecBuilder {
    override lateinit var artifactType: ArtifactType
    override var publishedConfigTypes: ImmutableList<PublishedConfigType> = API_AND_RUNTIME_ELEMENTS

    fun toSpec(): PublishingSpecs.OutputSpec = OutputSpecImpl(
            outputType,
            artifactType,
            publishedConfigTypes)
}
