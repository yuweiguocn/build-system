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

import com.android.builder.dexing.isProguardRule
import com.android.utils.FileUtils.mkdirs
import com.google.common.io.ByteStreams
import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.streams.toList

private fun isProguardRule(entry: ZipEntry): Boolean {
    return !entry.isDirectory && isProguardRule(entry.name)
}

class ExtractProGuardRulesTransform @Inject  constructor() : ArtifactTransform() {
    override fun transform(jarFile: File): List<File> {
        ZipFile(jarFile, StandardCharsets.UTF_8).use { zipFile ->
            return zipFile
                .stream()
                .filter { zipEntry -> isProguardRule(zipEntry) }
                .map { zipEntry ->
                    val outPath = zipEntry.name.replace('/', File.separatorChar)
                    val outFile = File(outputDirectory, outPath)
                    mkdirs(outFile.parentFile)

                    BufferedInputStream(zipFile.getInputStream(zipEntry)).use { inFileStream ->
                        BufferedOutputStream(outFile.outputStream()).use {
                            ByteStreams.copy(inFileStream, it)
                        }
                    }
                    outFile
                }
                .toList()
        }
    }
}