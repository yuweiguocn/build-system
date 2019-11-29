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

import com.android.build.api.transform.Context
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.android.testutils.truth.PathSubject.assertThat
import org.gradle.api.file.FileCollection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.objectweb.asm.Type
import java.nio.file.Path

/**
 * Testing scenarios for R8 transform processing class files which outputs DEX.
 */
class R8MainDexListTransformTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var outputProvider: TransformOutputProvider
    private lateinit var outputDir: Path

    @Before
    fun setUp() {
        outputDir = tmp.newFolder().toPath()
        outputProvider = TestTransformOutputProvider(outputDir)
        context = Mockito.mock(Context::class.java)
    }

    @Test
    fun testMainDexRules() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java)
        )
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile())
            .setContentTypes(CLASSES)
            .build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val mainDexRuleFile = tmp.newFile()
        mainDexRuleFile.printWriter().use {
            it.println("-keep class " + Animal::class.java.name)
        }
        val mainDexRulesFileCollection = FakeFileCollection(setOf(mainDexRuleFile))

        val transform =
            createTransform(mainDexRulesFiles = mainDexRulesFileCollection, minSdkVersion = 19)
        transform.keep("class **")

        transform.transform(invocation)
        val mainDex = Dex(outputDir.resolve("main").resolve("classes.dex"))
        assertThat(mainDex)
            .containsExactlyClassesIn(
                listOf(
                    Type.getDescriptor(CarbonForm::class.java),
                    Type.getDescriptor(Animal::class.java)
                )
            )

        val secondaryDex = Dex(outputDir.resolve("main").resolve("classes2.dex"))
        assertThat(secondaryDex).containsExactlyClassesIn(listOf(Type.getDescriptor(Toy::class.java)))
    }

    @Test
    fun testMonoDex() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java)
        )
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile())
            .setContentTypes(CLASSES)
            .build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val mainDexRuleFile = tmp.newFile()
        mainDexRuleFile.printWriter().use {
            it.println("-keep class " + CarbonForm::class.java.name)
        }
        val mainDexRulesFileCollection = FakeFileCollection(setOf(mainDexRuleFile))

        val transform =
            createTransform(
                mainDexRulesFiles = mainDexRulesFileCollection,
                minSdkVersion = 19,
                dexingType = DexingType.MONO_DEX
            )
        transform.keep("class **")
        transform.transform(invocation)

        assertThatDex(outputDir.resolve("main/classes.dex").toFile())
            .containsExactlyClassesIn(
                listOf(
                    Type.getDescriptor(CarbonForm::class.java),
                    Type.getDescriptor(Animal::class.java),
                    Type.getDescriptor(Toy::class.java)
                )
            )
        assertThat(outputDir.resolve("main/classes2.dex")).doesNotExist()
    }

    private fun createTransform(
        mainDexRulesFiles: FileCollection = FakeFileCollection(),
        minSdkVersion: Int = 21,
        dexingType: DexingType = DexingType.LEGACY_MULTIDEX
    ): R8Transform {
        return R8Transform(
            bootClasspath = lazy { listOf(TestUtils.getPlatformFile("android.jar")) },
            minSdkVersion = minSdkVersion,
            isDebuggable = true,
            java8Support = VariantScope.Java8LangSupport.UNUSED,
            disableTreeShaking = false,
            disableMinification = true,
            mainDexListFiles = FakeFileCollection(),
            mainDexRulesFiles = mainDexRulesFiles,
            inputProguardMapping = FakeFileCollection(),
            outputProguardMapping = tmp.newFile(),
            proguardConfigurationFiles = FakeConfigurableFileCollection(),
            variantType = VariantTypeImpl.BASE_APK,
            includeFeaturesInScopes = false,
            messageReceiver = NoOpMessageReceiver(),
            dexingType = dexingType,
            useFullR8 = false
        )
    }
}