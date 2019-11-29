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

package com.android.build.gradle.internal.cxx.process

import com.android.build.gradle.internal.cxx.configure.info
import com.android.builder.core.AndroidBuilder
import com.android.ide.common.process.BuildCommandException
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfo
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.process.ProcessOutputHandler
import com.android.ide.common.process.ProcessResult
import java.io.File
import java.io.IOException

/**
 * This is a builder-style class that composes multiple output streams and invoke process with
 * stderr and stdout sent to those streams. The kinds of stream:
 *
 * - combinedOutput is the interleaved combination of stderr and stdout. This is always gathered
 *   because it is used to return a BuildCommandException if the process failed.
 *
 * - stdout is used in the case that the caller wants a string for stdout. The main scenario for
 *   this is ndk-build -nB to gather the build plan to interpret.
 *
 * - stderr is always logged to info and stdout may be logged to info if the caller requests it.
 */
class ProcessOutputJunction(
    private val process: ProcessInfoBuilder,
    outputFolder: File,
    outputBaseName: String,
    private val logPrefix: String,
    private val lifecycle: (String) -> Unit,
    private val execute: (ProcessInfo, ProcessOutputHandler) -> ProcessResult
) {
    private var logErrorToInfo: Boolean = false
    private var logOutputToInfo: Boolean = false
    private val stderrFile = File(outputFolder, "$outputBaseName.stderr.txt")
    private val stdoutFile = File(outputFolder, "$outputBaseName.stdout.txt")
    private val commandFile = File(outputFolder, "$outputBaseName.command.txt")

    fun logStdoutToInfo(): ProcessOutputJunction {
        logOutputToInfo = true
        return this
    }

    fun logStderrToInfo(): ProcessOutputJunction {
        logErrorToInfo = true
        return this
    }

    fun execute(processHandler: DefaultProcessOutputHandler) {
        commandFile.parentFile.mkdirs()
        commandFile.delete()
        info(process.toString())
        commandFile.writeText(process.toString())
        stderrFile.delete()
        stdoutFile.delete()
        try {
            execute(process.createProcess(), processHandler)
                .rethrowFailure()
                .assertNormalExitValue()
        } catch (e: ProcessException) {
            throw BuildCommandException(
                """
                |${e.message}
                |${stdoutFile.readText()}
                |${stderrFile.readText()}
                """.trimMargin()
            )
        }
    }

    /**
     * Execute the process and return stdout as a String. If you don't need the String then you
     * should use execute() instead.
     */
    @Throws(BuildCommandException::class, IOException::class)
    fun executeAndReturnStdoutString(): String {
        val handler = DefaultProcessOutputHandler(
            stderrFile,
            stdoutFile,
            lifecycle,
            logPrefix,
            logErrorToInfo,
            logOutputToInfo
        )
        execute(handler)
        return stdoutFile.readText()
    }

    /**
     * Execute the process.
     */
    @Throws(BuildCommandException::class, IOException::class)
    fun execute() {
        val handler = DefaultProcessOutputHandler(
            stderrFile,
            stdoutFile,
            lifecycle,
            logPrefix,
            logErrorToInfo,
            logOutputToInfo
        )
        execute(handler)
    }
}

/**
 * Create a ProcessOutputJunction from a ProcessInfoBuilder and an AndroidBuilder.
 */
fun createProcessOutputJunction(
    outputFolder: File,
    outputBaseName: String,
    process: ProcessInfoBuilder,
    androidBuilder: AndroidBuilder,
    logPrefix: String
): ProcessOutputJunction {
    if (outputFolder.toString().contains(".json")) {
        throw RuntimeException("")
    }
    return ProcessOutputJunction(
        process,
        outputFolder,
        outputBaseName,
        logPrefix,
        { message -> androidBuilder.logger.lifecycle(message) },
        { processInfo: ProcessInfo, outputHandler: ProcessOutputHandler ->
            androidBuilder.executeProcess(
                processInfo,
                outputHandler
            )
        })
}
