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

package com.android.build.gradle.internal.res

import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.resources.CompileResourceRequest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Aapt2ErrorUtilsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testMessageRewriting() {
        val request = CompileResourceRequest(
            inputFile = temporaryFolder.newFile("processed"),
            inputDirectoryName = "values",
            outputDirectory = temporaryFolder.newFolder(),
            originalInputFile = temporaryFolder.newFile("original")
        )

        val aaptException = Aapt2Exception.create(
            description = "desc inputFile=" + request.inputFile.absolutePath,
            cause = null,
            output = "output inputFile=" + request.inputFile.absolutePath,
            processName = "process",
            command = "command inputFile=" + request.inputFile.absolutePath
        )

        val rewritten = rewriteCompileException(aaptException, request)

        assertThat(rewritten.description).contains(request.originalInputFile.absolutePath)
        assertThat(rewritten.output).contains(request.originalInputFile.absolutePath)
        assertThat(rewritten.command).contains(request.inputFile.absolutePath)
    }
}