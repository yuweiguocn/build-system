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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.utils.FileUtils.mkdirs
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.attributes.Attribute
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject

class DexingTransform
@Inject constructor(val minSdkVersion: Int, val isDebuggable: Boolean) : ArtifactTransform() {

    // Desugaring is not supported until artifact transforms start passing dependencies
    private val enableDesugaring = false

    override fun transform(input: File): List<File> {
        val outputDir = outputDirectory
        mkdirs(outputDir)

        val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
            minSdkVersion,
            isDebuggable,
            ClassFileProviderFactory(listOf()),
            ClassFileProviderFactory(listOf()),
            enableDesugaring,
            MessageReceiverImpl(
                SyncOptions.ErrorFormatMode.MACHINE_PARSABLE,
                LoggerFactory.getLogger(DexingTransform::class.java)
            )
        )

        ClassFileInputs.fromPath(input.toPath()).use {
                classFileInput -> classFileInput .entries { _ -> true }.use { classesInput ->
                    d8DexBuilder.convert(
                        classesInput,
                        outputDir.toPath(),
                        false
                    )
                }
        }

        return listOf(outputDir)
    }
}

fun getDexingArtifactConfigurations(scopes: Collection<VariantScope>): Set<DexingArtifactConfiguration> {
    return scopes.map {
        DexingArtifactConfiguration(
            it.minSdkVersion.featureLevel,
            it.variantConfiguration.buildType.isDebuggable
        )
    }.toSet()
}

data class DexingArtifactConfiguration(val minSdk: Int, val isDebuggable: Boolean)

@JvmField
val ATTR_MIN_SDK: Attribute<String> = Attribute.of("dexing-min-sdk", String::class.java)
@JvmField
val ATTR_IS_DEBUGGABLE: Attribute<String> =
    Attribute.of("dexing-is-debuggable", String::class.java)

fun getAttributeMap(minSdk: Int, isDebuggable: Boolean) =
    mapOf(ATTR_MIN_SDK to minSdk.toString(), ATTR_IS_DEBUGGABLE to isDebuggable.toString())