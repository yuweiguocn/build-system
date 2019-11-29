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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.internal.dsl.SigningConfig
import java.io.File
import java.io.IOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/** Tests for the [SigningConfigMetadata]  */
class SigningConfigMetadataTest {
    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    lateinit var outputDirectory : File
    lateinit var storeFile : File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        outputDirectory = temporaryFolder.newFolder()
        storeFile = temporaryFolder.newFile()
    }

    @Test
    @Throws(IOException::class)
    fun testSaveAndLoad() {
        val signingConfig = SigningConfig("signingConfig_name")
        signingConfig.storePassword = "foobar"
        signingConfig.keyPassword = "baz"
        signingConfig.storeFile = storeFile
        signingConfig.isV2SigningEnabled = true
        signingConfig.isV1SigningEnabled = false
        SigningConfigMetadata.save(outputDirectory, signingConfig)

        val files = outputDirectory.listFiles()
        assertThat(files).hasLength(1)

        val config = SigningConfigMetadata.load(files[0])
        assertThat(config).isEqualTo(signingConfig)
    }

    @Test
    @Throws(IOException::class)
    fun testSavedFileIsReadWriteByOwnerOnly() {
        val signingConfig = SigningConfig("signingConfig_name")
        signingConfig.storePassword = "foobar"
        signingConfig.keyPassword = "baz"
        signingConfig.storeFile = storeFile
        signingConfig.isV2SigningEnabled = true
        signingConfig.isV1SigningEnabled = false
        SigningConfigMetadata.save(outputDirectory, signingConfig)

        val files = outputDirectory.listFiles()
        assertThat(files).hasLength(1)

        val file = files[0]
        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
            val perms = Files.getPosixFilePermissions(file.toPath())
            assertThat(perms).containsExactly(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE)
        } else {
            // Windows special handling, check that we can read and write.
            val view = Files.getFileAttributeView(file.toPath(), AclFileAttributeView::class.java)
            val expectedEntry = AclEntry.newBuilder()
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
            assertThat(view.acl).containsExactly(expectedEntry)
        }
    }
}
