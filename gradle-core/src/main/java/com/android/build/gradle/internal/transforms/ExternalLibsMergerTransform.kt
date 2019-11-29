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

package com.android.build.gradle.internal.transforms

import android.databinding.tool.util.Preconditions
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.builder.dexing.DexMergerTool
import com.android.builder.dexing.DexingType
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.MessageReceiver
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.DexParser
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.util.concurrent.ForkJoinPool

/**
 * {@link Transform} that consumes all external libs and pre-merge them into a single Dex Archive.
 * This is particularly useful in mono dex incremental cases where the external libs rarely change
 * and we can re-merge this single Dex archive more effectively rather than re-merging each external
 * library individually.
 */
class ExternalLibsMergerTransform(
        private val dexingType: DexingType,
        private val dexMergerTool: DexMergerTool,
        private val minSdkVersion: Int,
        private val isDebuggable: Boolean,
        private val messageReceiver: MessageReceiver,
        private val callableFactory : DexMergerTransformCallable.Factory) : Transform() {

    private val logger = LoggerWrapper.getLogger(ExternalLibsMergerTransform::class.java)
    private val forkJoinPool = ForkJoinPool()

    override fun getName() = "externalLibsDexMerger"

    override fun getInputTypes() : Set<QualifiedContent.ContentType>
            = ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE)

    override fun getScopes() : MutableSet<in QualifiedContent.Scope>
            = ImmutableSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES)

    override fun getParameterInputs(): MutableMap<String, Any>
            = ImmutableMap.builder<String, Any>()
                .put("dexing-type", dexingType.name)
                .put("dex-merger-tool", dexMergerTool.name)
                .build()

    override fun isIncremental() = true

    override fun transform(transformInvocation: TransformInvocation) {

        val flattenInputs = transformInvocation.inputs
                .flatMap { it.jarInputs }

        // if we are in incremental mode and none of our inputs have changed, return immediately.
        if (transformInvocation.isIncremental
            && flattenInputs.none { it.status != Status.NOTCHANGED }) {
            return
        }

        Preconditions.check(transformInvocation.outputProvider != null,
                "No OutputProvider for ExternalLibsMergerTransform")

        // we need to re-merge all jars except the removed ones.
        val jarInputList = flattenInputs
            .stream()
            .filter { it.status != Status.REMOVED }
            .map { it.file.toPath() }
            .iterator()

        val outputHandler = ParsingProcessOutputHandler(
                ToolOutputParser(DexParser(), Message.Kind.ERROR, logger),
                ToolOutputParser(DexParser(), logger),
                messageReceiver)

        val outputDir = transformInvocation.outputProvider!!.getContentLocation("main",
                outputTypes,
                scopes,
                Format.DIRECTORY)
        FileUtils.cleanOutputDir(outputDir)

        // if all jars were removed, nothing to do.
        if (!jarInputList.hasNext()) {
            return
        }

        outputHandler.createOutput().use { processOutputHandler ->
            val callable = callableFactory.create(
                    messageReceiver,
                    dexingType,
                    processOutputHandler,
                    outputDir,
                    jarInputList,
                    null,
                    forkJoinPool,
                    dexMergerTool,
                    minSdkVersion,
                    isDebuggable)
            // since we are merging into a single DEX_ARCHIVE (possibly containing 1 to many DEX
            // merged DEX files, no need to use a separate thread.
            callable.call()
            forkJoinPool.shutdown()
        }
    }
}
