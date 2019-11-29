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

import com.android.build.api.transform.Context
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder
import com.android.builder.dexing.DexMergerTool
import com.android.builder.dexing.DexingType
import com.android.ide.common.process.ProcessOutput
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.nio.file.Path

open class ExternalLibsMergerTransformTest {

    @Mock lateinit var context : Context
    @Mock private lateinit var callable : DexMergerTransformCallable
    @Mock lateinit var factory : DexMergerTransformCallable.Factory
    @Rule @JvmField val folder : TemporaryFolder = TemporaryFolder()
    private val messageReceiver = NoOpMessageReceiver()

    @Captor
    private lateinit var processOutputCaptor : ArgumentCaptor<ProcessOutput>
    @Captor
    private lateinit var outputDirCaptor : ArgumentCaptor<File>
    @Captor
    private lateinit var outputListCaptor : ArgumentCaptor<MutableIterator<Path>>

    @Before
    fun setUp() = MockitoAnnotations.initMocks(this)

    @Test
    fun testParameterInputs() {
        val transform = ExternalLibsMergerTransform(DexingType.MONO_DEX,
                DexMergerTool.DX,
                21,
                true,
                messageReceiver,
                factory)

        Truth.assertThat(transform.parameterInputs).containsExactly(
                "dex-merger-tool", DexMergerTool.DX.name,
                "dexing-type", DexingType.MONO_DEX.name)
    }

    @Test
    fun testInvocation() {

        val inputs = createJarInput(2) + createJarInput(3)
        val transformInvocation = TransformInvocationBuilder(context)
                .addInputs(inputs)
                .addOutputProvider(TestTransformOutputProvider(folder.newFolder().toPath()))
                .setIncrementalMode(true)
                .build()

        // assert that the list of jars passed for merging is correct.
        Truth.assertThat(testTransformCall(transformInvocation))
                .containsExactlyElementsIn(
                        inputs.flatMap { it.jarInputs }
                                .map { it.file.toPath() } )
    }

    @Test
    fun testIncrementalChange() {
        val inputs = createJarInput(2, Status.CHANGED) +
                createJarInput(3, Status.NOTCHANGED)
        val transformInvocation = TransformInvocationBuilder(context)
                .addInputs(inputs)
                .setIncrementalMode(true)
                .addOutputProvider(TestTransformOutputProvider(folder.newFolder().toPath()))
                .build()

        // assert that the list of jars passed for merging is correct.
        Truth.assertThat(testTransformCall(transformInvocation))
                .containsExactlyElementsIn(
                        inputs.flatMap { it.jarInputs }
                                .map { it.file.toPath() } )
    }

    @Test
    fun testEmptyInvocation() {

        val inputs = createJarInput(2, Status.NOTCHANGED) +
                createJarInput(3, Status.NOTCHANGED)
        val transformInvocation = TransformInvocationBuilder(context)
                .addInputs(inputs)
                .setIncrementalMode(true)
                .addOutputProvider(TestTransformOutputProvider(folder.newFolder().toPath()))
                .build()

        val transform = ExternalLibsMergerTransform(DexingType.MONO_DEX,
                DexMergerTool.D8, 21, true, messageReceiver, factory)

        transform.transform(transformInvocation)

        Mockito.verifyZeroInteractions(factory)
    }

    private fun testTransformCall(transformInvocation : TransformInvocation) : List<Path> {

        val transform = ExternalLibsMergerTransform(DexingType.MONO_DEX,
                DexMergerTool.D8, 21, true, messageReceiver, factory)

        Mockito.`when`(factory.create(
                Mockito.any(),
                Mockito.eq(DexingType.MONO_DEX),
                Mockito.any(ProcessOutput::class.java),
                Mockito.any(),
                Mockito.any(),
                Mockito.isNull(),
                Mockito.any(),
                Mockito.eq(DexMergerTool.D8),
                Mockito.eq(21),
                Mockito.eq(true))).thenReturn(callable)

        transform.transform(transformInvocation)

        Mockito.verify(factory).create(
            Mockito.any(),
            Mockito.eq(DexingType.MONO_DEX),
            processOutputCaptor.capture(),
            outputDirCaptor.capture(),
            outputListCaptor.capture(),
            Mockito.isNull(),
            Mockito.any(),
            Mockito.eq(DexMergerTool.D8),
            Mockito.eq(21),
            Mockito.eq(true)
        )

        val inputs = mutableListOf<Path>()
        outputListCaptor.allValues.forEach {
            while (it.hasNext()) {
                inputs.add(it.next())
            }
        }
        return inputs
    }

    private fun createJarInput(numberOfJars : Int) : ImmutableList<TransformInput> {
        return createJarInput(numberOfJars, Status.ADDED)
    }

    private fun createJarInput(numberOfJars: Int, status : Status) : ImmutableList<TransformInput> {
        val builder = ImmutableList.builder<TransformInput>()
        for (i in 1..numberOfJars) {
            builder.add(TransformTestHelper.SingleJarInputBuilder(folder.newFile())
                    .setStatus(status)
                    .build())
        }
        return builder.build()
    }
}