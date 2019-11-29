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
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.R8OutputType
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.streams.toList

/**
 * Testing the basic scenarios for R8 transform processing class files. Both dex and class file
 * backend are tested.
 */
@RunWith(Parameterized::class)
class R8TransformTest(val r8OutputType: R8OutputType) {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var outputProvider: TransformOutputProvider
    private lateinit var outputDir: Path

    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun setups() = R8OutputType.values().map { arrayOf(it) }
    }

    @Before
    fun setUp() {
        outputDir = tmp.newFolder().toPath()
        outputProvider = TestTransformOutputProvider(outputDir)
        context = Mockito.mock(Context::class.java)
    }

    @Test
    fun testClassesProcessed() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

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

        val transform = createTransform()
        transform.keep("class **")

        transform.transform(invocation)

        assertClassExists("test/A")
        assertClassExists("test/B")
    }

    @Test
    fun testOneClassIsKept_noExtractableRules() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
        }

        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile())
            .setContentTypes(CLASSES, RESOURCES).build()
        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = createTransform()
        transform.keep("class test.A")

        transform.transform(invocation)

        assertClassExists("test/A")
        assertClassDoesNotExist("test/B")
    }

    // This test verifies that R8 transform does NOT extract the rules from the jars if these jars
    // are not explicitly set as a source for rule extraction. This is done in order to control
    // the proguard rules, being able to filter out undesired ones in a non-command-line scenario
    @Test
    fun testOneClassIsKept_hasExtractableRulesInResources() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/proguard/rules.pro"))
            zip.write("-keep class test.B".toByteArray())
            zip.closeEntry()
        }

        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile())
            .setContentTypes(RESOURCES, CLASSES).build()
        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = createTransform()
        transform.keep("class test.A")

        transform.transform(invocation)

        assertClassExists("test/A")
        assertClassDoesNotExist("test/B")
    }

    // This test verifies that R8 transform does NOT extract the rules from the jars if these jars
    // are not explicitly set as a source for rule extraction. This is done in order to control
    // the proguard rules, being able to filter out undesired ones in a non-command-line scenario
    @Test
    fun testOneClassIsKept_hasExtractableRulesInClasses() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/proguard/rules.pro"))
            zip.write("-keep class test.B".toByteArray())
            zip.closeEntry()
        }

        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile())
            .setContentTypes(CLASSES).build()
        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = createTransform()
        transform.keep("class test.A")

        transform.transform(invocation)

        assertClassExists("test/A")
        assertClassDoesNotExist("test/B")
    }

    @Test
    fun testLibraryClassesPassedToR8() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(classes, listOf(Animal::class.java))
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile())
            .setContentTypes(CLASSES).build()

        val libraryClasses = tmp.root.toPath().resolve("library_classes.jar")
        TestInputsGenerator.pathWithClasses(libraryClasses, listOf(CarbonForm::class.java))
        val jarLibrary = TransformTestHelper.singleJarBuilder(libraryClasses.toFile()).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .addReferenceInput(jarLibrary)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = createTransform()
        transform.keep("class **")

        transform.transform(invocation)

        assertClassExists(Animal::class.java)
        assertClassDoesNotExist(CarbonForm::class.java)
    }

    @Test
    fun testDesugaring() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile()).setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()
        val transform =
            createTransform(java8Support = VariantScope.Java8LangSupport.R8, disableTreeShaking = true)
        transform.keep("class ***")

        transform.transform(invocation)

        assertClassExists(Animal::class.java)
        assertClassExists(CarbonForm::class.java)
        assertClassExists(Cat::class.java)
        assertClassExists(Toy::class.java)

        if (r8OutputType == R8OutputType.DEX) {
            val dex = getDex()
            assertThat(dex.version).isEqualTo(35)
            // desugared classes are synthesized
            assertThat(dex.classes.size).isGreaterThan(4)
        } else {
            // no desugared classes are synthesized
            assertThat(Zip(outputDir.resolve("main.jar")).entries).hasSize(4)
        }
    }

    @Test
    fun testProguardConfiguration() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile()).setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val proguardConfiguration = tmp.newFile()
        proguardConfiguration.printWriter().use {
            it.println("-keep class " + Cat::class.java.name + " {*;}")
        }
        val proguardConfigurationFileCollection =
            FakeConfigurableFileCollection(setOf(proguardConfiguration))
        val transform = createTransform(
            java8Support = VariantScope.Java8LangSupport.R8,
            proguardRulesFiles = proguardConfigurationFileCollection
        )

        transform.transform(invocation)

        assertClassExists(Animal::class.java)
        assertClassExists(CarbonForm::class.java)
        assertClassExists(Cat::class.java)
        assertClassExists(Toy::class.java)
        // Check proguard compatibility mode
        assertClassHasAnnotations(Type.getInternalName(Toy::class.java))

        val transform2 = createTransform(java8Support = VariantScope.Java8LangSupport.R8)
        transform2.keep("class " + CarbonForm::class.java.name)

        transform2.transform(invocation)

        assertClassExists(CarbonForm::class.java)
        assertClassDoesNotExist(Animal::class.java)
        assertClassDoesNotExist(Cat::class.java)
        assertClassDoesNotExist(Toy::class.java)
    }

    @Test
    fun testProguardConfiguration_fullR8() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile()).setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val proguardConfiguration = tmp.newFile()
        proguardConfiguration.printWriter().use {
            it.println("-keep class " + Cat::class.java.name + " {*;}")
        }
        val proguardConfigurationFileCollection =
            FakeConfigurableFileCollection(setOf(proguardConfiguration))
        val transform = createTransform(
            java8Support = VariantScope.Java8LangSupport.R8,
            proguardRulesFiles = proguardConfigurationFileCollection,
            useFullR8 = true
        )

        transform.transform(invocation)

        assertClassExists(Animal::class.java)
        assertClassExists(CarbonForm::class.java)
        assertClassExists(Cat::class.java)
        assertClassExists(Toy::class.java)
        // Check full R8 mode
        assertClassDoesNotHaveAnnotations(Type.getInternalName(Toy::class.java))

        val transform2 = createTransform(
            java8Support = VariantScope.Java8LangSupport.R8,
            useFullR8 = true
        )
        transform2.keep("class " + CarbonForm::class.java.name)

        transform2.transform(invocation)

        assertClassExists(CarbonForm::class.java)
        assertClassDoesNotExist(Animal::class.java)
        assertClassDoesNotExist(Cat::class.java)
        assertClassDoesNotExist(Toy::class.java)
    }

    @Test
    fun testNonAsciiClassName() {
        // test for http://b.android.com/221057
        val nonAsciiName = "com/android/tests/basic/Ubicación"
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf(nonAsciiName))
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile()).setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()
        val transform = createTransform()
        transform.keep("class " + nonAsciiName.replace("/", "."))

        transform.transform(invocation)

        assertClassExists(nonAsciiName)
    }

    @Test
    fun testMappingProduced() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A"))
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile()).setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()
        val outputMapping = tmp.newFile()
        val transform =
            createTransform(disableMinification = false, outputProguardMapping = outputMapping)
        transform.keep("class **")

        transform.transform(invocation)
        assertThat(outputMapping).exists()
    }

    @Test
    fun testJavaResourcesCopied() {
        val resources = tmp.root.toPath().resolve("java_res.jar")
        ZipOutputStream(resources.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata1.txt"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("metadata2.txt"))
            zip.closeEntry()
        }
        val resInput =
            TransformTestHelper.singleJarBuilder(resources.toFile())
                .setContentTypes(RESOURCES)
                .build()

        val mixedResources = tmp.root.toPath().resolve("classes_and_res.jar")
        ZipOutputStream(mixedResources.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data/metadata.txt"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("a/b/c/metadata.txt"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
        }
        val jarInput =
            TransformTestHelper.singleJarBuilder(mixedResources.toFile())
                .setContentTypes(CLASSES, RESOURCES)
                .build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .setInputs(resInput, jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = createTransform()
        transform.keep("class **")
        transform.transform(invocation)

        assertClassExists("test/A")

        Zip(outputDir.resolve("java_res.jar")).use {
            assertThat(it).containsFileWithContent("metadata1.txt", "")
            assertThat(it).containsFileWithContent("metadata2.txt", "")
            assertThat(it).containsFileWithContent("data/metadata.txt", "")
            assertThat(it).containsFileWithContent("a/b/c//metadata.txt", "")
            assertThat(it).doesNotContain("test/A.class")
        }
    }

    private fun assertClassExists(clazz: Class<*>) {
       assertClassExists(Type.getInternalName(clazz))
    }

    private fun assertClassExists(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            val dex = getDex()
            assertThat(dex).containsClass("L$className;")
        } else {
            Zip(outputDir.resolve("main.jar")).use {
                assertThat(it).contains("$className.class")
            }
        }
    }

    private fun assertClassDoesNotExist(clazz: Class<*>) {
        assertClassDoesNotExist(Type.getInternalName(clazz))
    }

    private fun assertClassDoesNotExist(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            val dex = getDex()
            assertThat(dex).doesNotContainClasses("L$className;")
        } else {
            Zip(outputDir.resolve("main.jar")).use {
                assertThat(it).doesNotContain("$className.class")
            }
        }
    }

    private fun assertClassHasAnnotations(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            assertThat(getDex()).containsClass(Type.getDescriptor(Toy::class.java)).that()
                .hasAnnotations()
        } else {
            assertThat(hasAnnotations(className)).named("class has annotations").isTrue()
        }
    }

    private fun assertClassDoesNotHaveAnnotations(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            // Check proguard compatibility mode
            assertThat(getDex()).containsClass("L$className;").that()
                .doesNotHaveAnnotations()
        } else {
            assertThat(hasAnnotations(className)).named("class does not have annotations").isFalse()
        }
    }

    private fun hasAnnotations(className: String): Boolean {
        var foundAnnotation = false
        ZipFile(outputDir.resolve("main.jar").toFile()).use {
            val input =
                it.getInputStream(it.getEntry("$className.class"))
            ClassReader(input).accept(object : ClassVisitor(Opcodes.ASM5) {
                override fun visitAnnotation(
                    desc: String?,
                    visible: Boolean
                ): AnnotationVisitor? {
                    foundAnnotation = true
                    return super.visitAnnotation(desc, visible)
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
        }
        return foundAnnotation
    }

    @Test
    fun testClassesIgnoredFromResources() {
        val resDir = tmp.root.resolve("res_dir").also {
            it.mkdir()
            it.resolve("res.txt").createNewFile()
            it.resolve("A.class").createNewFile()
        }
        val resJar = tmp.root.resolve("res.jar")
            ZipOutputStream(resJar.outputStream()).use {
                it.putNextEntry(ZipEntry("data.txt"))
                it.closeEntry()
                it.putNextEntry(ZipEntry("B.class"))
                it.closeEntry()
            }

        val dirInput = TransformTestHelper.directoryBuilder(resDir).setContentType(RESOURCES).build()
        val jarInput = TransformTestHelper.singleJarBuilder(resJar).setContentTypes(RESOURCES).build()

        val invocation = TransformTestHelper.invocationBuilder().setInputs(jarInput, dirInput)
            .setContext(this.context).setTransformOutputProvider(outputProvider).build()

        createTransform().transform(invocation)

        assertThat(outputDir.resolve("main/classes.dex")).doesNotExist()
        Zip(outputDir.resolve("java_res.jar")).use {
            assertThat(it).contains("res.txt")
            assertThat(it).contains("data.txt")
            assertThat(it).doesNotContain("A.class")
            assertThat(it).doesNotContain("B.class")
        }
    }

    private fun getDex(): Dex {
        val dexFiles = Files.walk(outputDir).filter { it.toString().endsWith(".dex") }.toList()
        return Dex(dexFiles.single())
    }

    private fun createTransform(
        mainDexRulesFiles: FileCollection = FakeFileCollection(),
        java8Support: VariantScope.Java8LangSupport = VariantScope.Java8LangSupport.UNUSED,
        proguardRulesFiles: ConfigurableFileCollection = FakeConfigurableFileCollection(),
        outputProguardMapping: File = tmp.newFile(),
        disableMinification: Boolean = true,
        disableTreeShaking: Boolean = false,
        minSdkVersion: Int = 21,
        useFullR8: Boolean = false
    ): R8Transform {
        val variantType =
            if (r8OutputType == R8OutputType.DEX)
                VariantTypeImpl.BASE_APK
            else
                VariantTypeImpl.LIBRARY

        return R8Transform(
            bootClasspath = lazy { listOf(TestUtils.getPlatformFile("android.jar")) },
            minSdkVersion = minSdkVersion,
            isDebuggable = true,
            java8Support = java8Support,
            disableTreeShaking = disableTreeShaking,
            disableMinification = disableMinification,
            mainDexListFiles = FakeFileCollection(),
            mainDexRulesFiles = mainDexRulesFiles,
            inputProguardMapping = FakeFileCollection(),
            outputProguardMapping = outputProguardMapping,
            proguardConfigurationFiles = proguardRulesFiles,
            variantType = variantType,
            includeFeaturesInScopes = false,
            messageReceiver = NoOpMessageReceiver(),
            dexingType = DexingType.NATIVE_MULTIDEX,
            useFullR8 = useFullR8
        )
    }
}