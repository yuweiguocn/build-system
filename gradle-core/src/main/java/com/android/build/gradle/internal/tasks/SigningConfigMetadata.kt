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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.IOException
import org.apache.commons.io.FileUtils
import org.gradle.api.file.FileCollection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission



/**
 * Information containing the signing config metadata that can be consumed by other modules as
 * persisted json file
 */
class SigningConfigMetadata {
    companion object {
        private const val PERSISTED_FILE_NAME = "signing-config.json"

        @Throws(IOException::class)
        fun load(input: FileCollection?): SigningConfig? {
            return load(getOutputFile(input))
        }

        @Throws(IOException::class)
        fun save(outputDirectory: File, signingConfig: SigningConfig?) {
            val outputFile = File(outputDirectory, PERSISTED_FILE_NAME)
            // create the file, so we can set the permissions on it.
            outputFile.createNewFile()
            if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
                // set read, write permissions for owner only.
                val perms = HashSet<PosixFilePermission>()
                perms.add(PosixFilePermission.OWNER_READ)
                perms.add(PosixFilePermission.OWNER_WRITE)
                Files.setPosixFilePermissions(outputFile.toPath(), perms)
            } else {
                // on windows, use AclEntry to set the owner read/write permission.
                val view = Files.getFileAttributeView(
                    outputFile.toPath(), AclFileAttributeView::class.java)
                val entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(view.owner)
                    .setPermissions(
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.WRITE_OWNER,
                        AclEntryPermission.SYNCHRONIZE,
                        AclEntryPermission.DELETE)
                    .build()
                view.acl = listOf(entry)
            }

            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            val gson = gsonBuilder.create()
            FileUtils.write(outputFile, gson.toJson(signingConfig), StandardCharsets.UTF_8)
        }

        @Throws(IOException::class)
        fun load(input: File?): SigningConfig? {
            if (input == null) return null
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            val gson = gsonBuilder.create()
            input.bufferedReader(StandardCharsets.UTF_8).use { fileReader ->
                return gson.fromJson(
                    fileReader,
                    SigningConfig::class.java
                )
            }
        }

        fun getOutputFile(input: FileCollection?): File? {
            if (input == null) return null
            if (input.asFileTree.isEmpty) return null
            val file = input.asFileTree.singleFile
            if (file.name != PERSISTED_FILE_NAME) return null
            return file
        }

        fun getOutputFile(directory: File): File {
            return File(directory, PERSISTED_FILE_NAME)
        }
    }
}
