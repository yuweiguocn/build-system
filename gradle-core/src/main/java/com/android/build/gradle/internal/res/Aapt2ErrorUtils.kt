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
@file:JvmName("Aapt2ErrorUtils")

package com.android.build.gradle.internal.res

import com.android.build.gradle.internal.errors.humanReadableMessage
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser
import com.android.ide.common.resources.CompileResourceRequest
import com.android.utils.StdLogger
import com.google.common.collect.ImmutableList

/**
 * Rewrite exceptions to point to their original files.
 *
 * Returns the same exception as is if it could not be rewritten.
 *
 * This is expensive, so should only be used if the build is going to fail anyway.
 * The merging log is used directly from memory, as this only is needed within the resource merger.
 */
fun rewriteCompileException(e: Aapt2Exception, request: CompileResourceRequest): Aapt2Exception {
    if (request.blameMap.isEmpty()) {
        return if (request.inputFile == request.originalInputFile) {
            e // Nothing to rewrite.
        } else {
            Aapt2Exception.create(
                description = "Failed to compile android resource " +
                        "'${request.originalInputFile.absolutePath}'.",
                cause = e,
                output = e.output?.replace(request.inputFile.absolutePath, request.originalInputFile.absolutePath),
                processName = e.processName,
                command = e.command

            )
        }
    }
    return rewriteException(e) {
        if (it.file.sourceFile == request.originalInputFile) {
            MergingLog.find(it.position, request.blameMap) ?: it
        } else {
            it
        }
    }
}

/**
 * Rewrite exceptions to point to their original files.
 *
 * Returns the same exception as is if it could not be rewritten.
 *
 * This is expensive, so should only be used if the build is going to fail anyway.
 * The merging log is loaded from files lazily.
 */
fun rewriteLinkException(e: Aapt2Exception, mergingLog: MergingLog): Aapt2Exception {
    return rewriteException(e) { mergingLog.find(it) }
}

/** Attept to rewrite the given exception using the lookup function. */
private fun rewriteException(
    e: Aapt2Exception,
    blameLookup: (SourceFilePosition) -> SourceFilePosition
): Aapt2Exception {
    if (e.output == null) {
        // No AAPT2 output to rewrite.
        return e
    }

    try {
        val messages =
                ToolOutputParser(
                        Aapt2OutputParser(),
                        Message.Kind.SIMPLE,
                        StdLogger(StdLogger.Level.INFO)
                ).parseToolOutput(e.output!!)
        if (!messages.any { it.kind != Message.Kind.SIMPLE }) {
            // No messages were parsed, so nothing to rewrite.
            return e
        }
        return Aapt2Exception.create(
            description = e.description,
            cause = e,
            output = messages.map { message ->
                message.copy(
                    sourceFilePositions =
                    rewritePositions(message.sourceFilePositions, blameLookup)
                )
            }.joinToString("\n") { humanReadableMessage(it) },
            processName = e.processName,
            command = e.command
        )
    } catch (e2: Exception) {
        // Something went wrong, report the original error with the error reporting error supressed.
        return e.apply { addSuppressed(e2) }
    }
}

private fun rewritePositions(
    sourceFilePositions: List<SourceFilePosition>,
    blameLookup: (SourceFilePosition) -> SourceFilePosition
): ImmutableList<SourceFilePosition> =
    ImmutableList.builder<SourceFilePosition>().apply {
        sourceFilePositions.forEach { add(blameLookup.invoke(it)) }
    }.build()