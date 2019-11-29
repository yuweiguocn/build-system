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

package com.android.build.gradle.internal.process

import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.model.Rules
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.io.IOException

class OpenJDKJarSignerTest {

    @get:Rules
    val folder= TemporaryFolder()

    @Before
    fun setUp() {
        folder.create()
    }

    @Test
    fun testInvalidJarSignerLocation() {
        val mySigner = object: OpenJDKJarSigner() {
            override fun start(processBuilder: ProcessBuilder): Process {
                throw IOException("Cannot find file " + processBuilder.command()[0])
            }
        }

        val signature = JarSigner.Signature(folder.newFile("keystore"),
            "password", "alias", "password")
        try {
            mySigner.sign(folder.newFile("jarToBeSigned"), signature)
        } catch(e: RuntimeException) {
            assertThat(e.message).contains("please add it to the PATH")
        }
    }

    @Test(expected = RuntimeException::class)
    fun testJarSignerCrashing() {
        val mySigner = object: OpenJDKJarSigner() {
            override fun start(processBuilder: ProcessBuilder): Process {
                throw RuntimeException("core dumped")
            }
        }

        val signature = JarSigner.Signature(folder.newFile("keystore"),
            "password", "alias", "password")
        mySigner.sign(folder.newFile("jarToBeSigned"), signature)
    }

    @Test
    fun testInvocation() {
        val keyStoreFile = folder.newFile("keystore")
        val jarToBeSigned = folder.newFile("jarToBeSigned")
        var storePassword: File? = null
        var aliasPassword: File? = null

        val mySigner = object: OpenJDKJarSigner() {
            override fun start(processBuilder: ProcessBuilder): Process {
                val command = processBuilder.command()
                assertThat(command).hasSize(9)
                assertThat(command[0]).endsWith(OpenJDKJarSigner.jarSignerExecutable)
                assertThat(command[2]).isEqualTo(keyStoreFile.absolutePath)
                storePassword = File(command[4])
                assertThat(storePassword!!.exists()).isTrue()
                assertThat(FileUtils.loadFileWithUnixLineSeparators(storePassword!!))
                    .isEqualTo("store_password")
                aliasPassword = File(command[6])
                assertThat(aliasPassword!!.exists()).isTrue()
                assertThat(FileUtils.loadFileWithUnixLineSeparators(aliasPassword!!))
                    .isEqualTo("alias_password")
                assertThat(command[7]).isEqualTo(jarToBeSigned.absolutePath)
                assertThat(command[8]).isEqualTo("alias")

                assertThat(processBuilder.redirectError().file().exists()).isTrue()
                assertThat(processBuilder.redirectOutput().file().exists()).isTrue()
                return Mockito.mock(Process::class.java)
            }
        }
        val signature = JarSigner.Signature(keyStoreFile,
            "store_password", "alias", "alias_password")
        mySigner.sign(jarToBeSigned, signature)

        assertThat(storePassword?.exists()).isFalse()
        assertThat(aliasPassword?.exists()).isFalse()
    }

    @Test
    fun testInvocationWitMinimalParameters() {
        val keyStoreFile = folder.newFile("keystore")
        val jarToBeSigned = folder.newFile("jarToBeSigned")
        val mySigner = object: OpenJDKJarSigner() {
            override fun start(processBuilder: ProcessBuilder): Process {
                val command = processBuilder.command()
                assertThat(command).hasSize(4)
                assertThat(command[0]).endsWith(OpenJDKJarSigner.jarSignerExecutable)
                assertThat(command[2]).isEqualTo(keyStoreFile.absolutePath)
                assertThat(command[3]).isEqualTo(jarToBeSigned.absolutePath)

                assertThat(processBuilder.redirectError().file().exists()).isTrue()
                assertThat(processBuilder.redirectOutput().file().exists()).isTrue()
                return Mockito.mock(Process::class.java)
            }
        }
        val signature = JarSigner.Signature(keyStoreFile, null, null, null)
        mySigner.sign(jarToBeSigned, signature)
    }
}
