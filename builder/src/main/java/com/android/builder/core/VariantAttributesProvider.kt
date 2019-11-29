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

package com.android.builder.core

import com.android.builder.errors.EvalIssueReporter
import com.google.common.base.Preconditions.checkState

import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor
import com.google.common.base.Strings
import java.io.Serializable
import java.io.File
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Provides attributes for the variant.
 *
 * The attributes are from data merged from the manifest and product flavor.
 *
 * @param mergedFlavor the merged product flavor
 * @param buildType the type used for the build
 * @param isTestVariant whether the current variant is for a test component.
 * @param manifestSupplier the supplier of manifest attributes.
 * @param manifestFile the file for the manifest.
 */
class VariantAttributesProvider(
        var mergedFlavor: ProductFlavor,
        private val buildType: BuildType,
        private val isTestVariant: Boolean,
        private val manifestSupplier: ManifestAttributeSupplier,
        private val manifestFile: File,
        var fullName: String) {

    /**
     * Returns the application id override value coming from the Product Flavor and/or the Build
     * Type. If the package/id is not overridden then this returns null.
     *
     * @return the id override or null
     */
    val idOverride: String?
        get() {
            var idName = mergedFlavor.applicationId

            val idSuffix = DefaultProductFlavor.mergeApplicationIdSuffix(
                    buildType.applicationIdSuffix,
                    mergedFlavor.applicationIdSuffix)

            if (!idSuffix.isEmpty()) {
                idName = idName ?: packageName
                idName = if (idSuffix[0] == '.') idName + idSuffix else idName + '.' + idSuffix
            }

            return idName
        }

    /**
     * Returns the package name from the manifest file.
     *
     * @return the package name or throws an exception if not found in the manifest.
     */
    val packageName: String
        get() {
            checkState(!isTestVariant)
            return manifestSupplier.`package` ?: throw RuntimeException(
                    "Cannot read packageName from ${manifestFile.absolutePath}")
        }

    /**
     * Returns the split name from the manifest file.
     *
     * @return the split name or null if not found.
     */
    val split: String?
        get() = manifestSupplier.split

    /**
     * Returns the version name for this variant. This could be coming from the manifest or could be
     * overridden through the product flavors, and can have a suffix specified by the build type.
     *
     * @return the version name
     */
    val versionName: String?
        get() {
            var versionName = mergedFlavor.versionName
            var versionSuffix = mergedFlavor.versionNameSuffix

            if (versionName == null && !isTestVariant) {
                versionName = manifestSupplier.versionName
            }

            versionSuffix = DefaultProductFlavor.mergeVersionNameSuffix(
                    buildType.versionNameSuffix, versionSuffix)

            if (versionSuffix != null && !versionSuffix.isEmpty()) {
                versionName = Strings.nullToEmpty(versionName) + versionSuffix
            }

            return versionName
        }

    /**
     * Returns the version code for this variant. This could be coming from the manifest or could be
     * overridden through the product flavors, and can have a suffix specified by the build type.
     *
     * @return the version code or -1 if there was non defined.
     */
    val versionCode: Int
        get() {
            var versionCode = mergedFlavor.versionCode ?: -1

            if (versionCode == -1 && !isTestVariant) {
                versionCode = manifestSupplier.versionCode
            }

            return versionCode
        }

    /**
     * Returns the instrumentation runner, found in the build file or manifest file
     *
     * @return the instrumentation runner or `null` if there is none specified.
     */
    val instrumentationRunner: String?
        get() = mergedFlavor.testInstrumentationRunner ?: manifestSupplier.instrumentationRunner

    /**
     * Returns the targetPackage from the instrumentation tag in the manifest file.
     *
     * @return the targetPackage or `null` if there is none specified.
     */
    val targetPackage: String?
        get() = manifestSupplier.targetPackage

    /**
     * Returns the functionalTest, found in the build file or manifest file.
     *
     * @return the functionalTest or `null` if there is none specified.
     */
    val functionalTest: Boolean?
        get() = mergedFlavor.testFunctionalTest ?: manifestSupplier.functionalTest

    /**
     * Returns the handleProfiling, found in the build file or manifest file.
     *
     * @return the handleProfiling or `null` if there is none specified.
     */
    val handleProfiling: Boolean?
        get() = mergedFlavor.testHandleProfiling ?: manifestSupplier.handleProfiling

    /**
     * Returns the testLabel from the instrumentation tag in the manifest file.
     *
     * @return the testLabel or `null` if there is none specified.
     */
    val testLabel: String?
        get() = manifestSupplier.testLabel

    /**
     * Returns value of the `extractNativeLibs` attribute of the `application` tag, if
     * present in the manifest file.
     */
    val extractNativeLibs: Boolean?
        get() = manifestSupplier.extractNativeLibs

    /**
     * Returns the application ID even if this is a test variant.
     *
     * @return the application ID
     */
    fun getApplicationId(testedPackage: String): String {
        var id: String?

        if (isTestVariant) {
            id = mergedFlavor.testApplicationId
            if (id == null) {
                id = "$testedPackage.test"
            } else {
                if (id == testedPackage) {
                    throw RuntimeException(
                            "Application and test application id cannot be the same: both are $id for $fullName")
                }
            }

        } else {
            // first get package override.
            // if it's null, this means we just need the default package
            // from the manifest since both flavor and build type do nothing.
            id = idOverride ?: packageName
        }

        return id
    }

    /**
     * Returns the original application ID before any overrides from flavors. If the variant is a
     * test variant, then the application ID is the one coming from the configuration of the tested
     * variant, and this call is similar to [.getApplicationId]
     *
     * @return the original application ID
     */
    fun getOriginalApplicationId(testedPackage: String): String {
        return if (isTestVariant) {
            getApplicationId(testedPackage)
        } else packageName

    }

    /**
     * Returns the test app application ID, which should only be called from a test variant.
     *
     * @return the test application ID
     */
    fun getTestApplicationId(testedPackage: String): String {
        checkState(isTestVariant)

        return if (!Strings.isNullOrEmpty(mergedFlavor.testApplicationId)) {
            // if it's specified through build file read from there
            mergedFlavor.testApplicationId!!
        } else {
            // otherwise getApplicationId() contains rules for getting the
            // applicationId for the test app from the tested application
            getApplicationId(testedPackage)
        }
    }

    val versionNameSerializableSupplier: Supplier<String?>
        get() {
            val file = if (isTestVariant) null else manifestFile
            val versionSuffix = DefaultProductFlavor.mergeVersionNameSuffix(
                buildType.versionNameSuffix, mergedFlavor.versionNameSuffix
            )
            return SerializableStringSupplier(file, mergedFlavor.versionName, versionSuffix)
        }

    val versionCodeSerializableSupplier: IntSupplier
        get() {
            val versionCode = mergedFlavor.versionCode ?: -1
            val file = if (isTestVariant) null else manifestFile
            return SerializableIntSupplier(file, versionCode)
        }

    private class SerializableStringSupplier(
            private val manifestFile: File? = null,
            private val versionName: String? = null,
            private val versionSuffix: String? = null) : Supplier<String?>, Serializable {

        private var cachedVersionName: String? = null

        override fun get(): String? {
            if (cachedVersionName != null) {
                return cachedVersionName
            }
            cachedVersionName = versionName
            if (cachedVersionName == null && manifestFile != null) {
                cachedVersionName = DefaultManifestParser(manifestFile, { true }, null).versionName
            }

            if (versionSuffix != null && !versionSuffix.isEmpty()) {
                cachedVersionName = Strings.nullToEmpty(cachedVersionName) + versionSuffix
            }

            return cachedVersionName
        }
    }

    private class SerializableIntSupplier(
                private val manifestFile: File? = null,
                private var versionCode: Int = -1) : IntSupplier, Serializable
    {
        private var isCached: Boolean = false

        override fun getAsInt(): Int {
            if (isCached) {
                return versionCode
            }
            if (versionCode == -1 && manifestFile != null) {
                versionCode = DefaultManifestParser(manifestFile, { true }, null).versionCode
            }
            isCached = true
            return versionCode
        }
    }

}
