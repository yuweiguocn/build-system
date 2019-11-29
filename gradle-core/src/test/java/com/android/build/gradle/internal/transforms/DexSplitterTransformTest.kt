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

package com.android.build.gradle.internal.transforms

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.transform.Context
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * Testing the basic scenarios for DexSplitterTransform.
 */
class DexSplitterTransformTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var r8Context: Context
    private lateinit var r8OutputProvider: TransformOutputProvider
    private lateinit var r8OutputProviderDir: File
    private lateinit var dexSplitterContext: Context
    private lateinit var dexSplitterOutputProvider: TransformOutputProvider
    private lateinit var dexSplitterOutputProviderDir: File
    private lateinit var dexSplitterOutputDir: File
    private lateinit var baseClasses: File
    private lateinit var featureClasses: File

    @Mock private lateinit var mappingFileSrc: BuildableArtifact
    @Mock private lateinit var baseJars: BuildableArtifact

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        r8OutputProviderDir = tmp.newFolder()
        r8OutputProvider = TestTransformOutputProvider(r8OutputProviderDir.toPath())
        r8Context = Mockito.mock(Context::class.java)
        dexSplitterOutputProviderDir = tmp.newFolder()
        dexSplitterOutputProvider =
                TestTransformOutputProvider(dexSplitterOutputProviderDir.toPath())
        dexSplitterContext = Mockito.mock(Context::class.java)
        dexSplitterOutputDir = tmp.newFolder()

        baseClasses = File(tmp.root, "base/base.jar")
        FileUtils.mkdirs(baseClasses.parentFile)
        TestInputsGenerator.jarWithEmptyClasses(baseClasses.toPath(), listOf("base/A", "base/B"))
        Mockito.`when`(baseJars.iterator())
            .thenReturn(listOf(baseClasses).iterator())

        featureClasses = File(tmp.root, "feature-foo.jar")
        FileUtils.mkdirs(featureClasses.parentFile)
        TestInputsGenerator.jarWithEmptyClasses(
            featureClasses.toPath(), listOf("feature/A", "feature/B"))
    }

    @Test
    fun testBasic() {
        // We run R8 first to generate dex file from jar files.
        runR8(listOf(baseClasses, featureClasses), "class **")

        // Check that r8 ran as expected before running dexSplitter
        val r8Dex = getDex(r8OutputProviderDir.toPath())
        assertThat(r8Dex).containsClasses("Lbase/A;", "Lbase/B;", "Lfeature/A;", "Lfeature/B;")

        runDexSplitter(File(r8OutputProviderDir, "main"), listOf(featureClasses), baseJars)

        checkDexSplitterOutputs()
    }

    @Test
    fun testNonExistentMappingFile() {

        Mockito.`when`(mappingFileSrc.iterator())
            .thenReturn(listOf(File("/path/to/nowhere")).iterator())

        // We run R8 first to generate dex file from jar files.
        runR8(listOf(baseClasses, featureClasses), "class **")

        // Check that r8 ran as expected before running dexSplitter
        val r8Dex = getDex(r8OutputProviderDir.toPath())
        assertThat(r8Dex).containsClasses("Lbase/A;", "Lbase/B;", "Lfeature/A;", "Lfeature/B;")

        runDexSplitter(
            File(r8OutputProviderDir, "main"),
            listOf(featureClasses),
            baseJars,
            mappingFileSrc)

        checkDexSplitterOutputs()
    }

    private fun runDexSplitter(
        dexDir: File,
        featureJars: List<File>,
        baseJars: BuildableArtifact,
        mappingFileSrc: BuildableArtifact? = null
    ) {
        val dexSplitterInput = TransformTestHelper.directoryBuilder(dexDir).build()
        val dexSplitterInvocation =
                TransformTestHelper
                        .invocationBuilder()
                        .addInput(dexSplitterInput)
                        .setContext(this.dexSplitterContext)
                        .setTransformOutputProvider(dexSplitterOutputProvider)
                        .build()

        val dexSplitterTransform =
                DexSplitterTransform(
                        dexSplitterOutputDir,
                        FakeFileCollection(featureJars),
                        baseJars,
                        mappingFileSrc = mappingFileSrc)

        dexSplitterTransform.transform(dexSplitterInvocation)
    }


    private fun runR8(jars: List<File>, r8Keep: String? = null) {
        val jarInputs =
            jars.asSequence().map {
                TransformTestHelper.singleJarBuilder(it).setContentTypes(RESOURCES, CLASSES).build()
            }.toSet()
        val r8Invocation =
                TransformTestHelper
                        .invocationBuilder()
                        .setInputs(jarInputs)
                        .setContext(this.r8Context)
                        .setTransformOutputProvider(r8OutputProvider)
                        .build()

        val r8Transform = getR8Transform()
        r8Keep?.let { r8Transform.keep(it) }

        r8Transform.transform(r8Invocation)
    }

    private fun checkDexSplitterOutputs() {
        val baseDex = getDex(dexSplitterOutputProviderDir.toPath())
        assertThat(baseDex).containsClasses("Lbase/A;", "Lbase/B;")
        assertThat(baseDex).doesNotContainClasses("Lfeature/A;", "Lfeature/B;")

        val featureDex = getDex(File(dexSplitterOutputDir, "feature-foo").toPath())
        assertThat(featureDex).containsClasses("Lfeature/A;", "Lfeature/B;")
        assertThat(featureDex).doesNotContainClasses("Lbase/A;", "Lbase/B;")

        Truth.assertThat(dexSplitterOutputDir.listFiles().map {it.name} ).doesNotContain("base")
    }

    private fun getDex(path: Path): Dex {
        val dexFiles = Files.walk(path).filter { it.toString().endsWith(".dex") }.toList()
        return Dex(dexFiles.single())
    }

    private fun getR8Transform(
        mainDexRulesFiles: FileCollection = FakeFileCollection(),
        java8Support: VariantScope.Java8LangSupport = VariantScope.Java8LangSupport.UNUSED,
        proguardRulesFiles: ConfigurableFileCollection = FakeConfigurableFileCollection(),
        outputProguardMapping: File = tmp.newFile(),
        disableMinification: Boolean = true,
        minSdkVersion: Int = 21
    ): R8Transform {
        return R8Transform(
                bootClasspath = lazy { listOf(TestUtils.getPlatformFile("android.jar")) },
                minSdkVersion = minSdkVersion,
                isDebuggable = true,
                java8Support = java8Support,
                disableTreeShaking = false,
                disableMinification = disableMinification,
                mainDexListFiles = FakeFileCollection(),
                mainDexRulesFiles = mainDexRulesFiles,
                inputProguardMapping = FakeFileCollection(),
                outputProguardMapping = outputProguardMapping,
                proguardConfigurationFiles = proguardRulesFiles,
                variantType = VariantTypeImpl.BASE_APK,
                includeFeaturesInScopes = false,
                dexingType = DexingType.NATIVE_MULTIDEX,
                messageReceiver= NoOpMessageReceiver()
        )
    }
}