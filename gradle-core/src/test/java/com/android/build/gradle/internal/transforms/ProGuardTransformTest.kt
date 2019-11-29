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
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.VariantTypeImpl
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Zip
import com.android.testutils.truth.MoreTruth
import org.gradle.api.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.toList

class ProGuardTransformTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var outputProvider: TransformOutputProvider
    private lateinit var outputDir: Path
    private lateinit var variantConfigDirName: String

    @Before
    fun setUp() {
        outputDir = tmp.newFolder().toPath()
        variantConfigDirName = tmp.newFolder().name
        outputProvider = TestTransformOutputProvider(outputDir)
        context = Mockito.mock(Context::class.java)
    }

    @Test
    fun testKeepClassAPI() {

        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile())
                .setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = ProGuardTransform(createScope())

        transform.keep("public class test.A")

        transform.transform(invocation)

        val resultJar = resultJar()

        MoreTruth.assertThat(resultJar).contains("test/A.class")
        MoreTruth.assertThat(resultJar).doesNotContain("test/B.class")
    }

    @Test
    fun testKeepClassFileRule() {

        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile())
                .setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val rulesFile = newFile("-keep public class test.A")

        val transform = ProGuardTransform(createScope(setOf(rulesFile)))

        transform.transform(invocation)

        val resultJar = resultJar()

        MoreTruth.assertThat(resultJar).contains("test/A.class")
        MoreTruth.assertThat(resultJar).doesNotContain("test/B.class")
    }

    @Test
    fun testUseProguardConfigurationFile() {

        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
        }
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile())
                .setContentTypes(CLASSES).build()

        val configFile = newFile("-keep public class test.A")

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = ProGuardTransform(createScope())

        transform.setConfigurationFiles(FakeFileCollection(configFile))

        transform.transform(invocation)

        val resultJar = resultJar()

        MoreTruth.assertThat(resultJar).contains("test/A.class")
        MoreTruth.assertThat(resultJar).doesNotContain("test/B.class")
    }

    @Test
    fun testUseProguardConfigurationFile_andRulesFile() {

        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
        }
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile())
                .setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val rulesFile = newFile("-keep public class test.B")

        val configFile = newFile("-keep public class test.A")

        val transform = ProGuardTransform(createScope(setOf(rulesFile)))

        transform.setConfigurationFiles(FakeFileCollection(configFile))

        transform.transform(invocation)

        val resultJar = resultJar()

        MoreTruth.assertThat(resultJar).contains("test/A.class")
        MoreTruth.assertThat(resultJar).contains("test/B.class")
    }

    @Test
    fun testUseProguardConfigurationFile_multipleFiles() {

        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
        }
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile())
                .setContentTypes(CLASSES).build()

        val classes2 = tmp.root.toPath().resolve("classes2.jar")
        ZipOutputStream(classes2.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/C.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "C"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/D.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "D"));
            zip.closeEntry()
        }

        val jarInput2 =
            TransformTestHelper.singleJarBuilder(classes2.toFile())
                .setContentTypes(CLASSES).build()

        val configFileA = newFile("-keep public class test.A")
        val configFileC = newFile("-keep public class test.C")

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .addInput(jarInput2)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = ProGuardTransform(createScope())

        transform.setConfigurationFiles(FakeFileCollection(configFileA, configFileC))

        transform.transform(invocation)

        val resultJar = resultJar()

        MoreTruth.assertThat(resultJar).contains("test/A.class")
        MoreTruth.assertThat(resultJar).contains("test/C.class")
        MoreTruth.assertThat(resultJar).doesNotContain("test/B.class")
        MoreTruth.assertThat(resultJar).doesNotContain("test/D.class")
    }

    @Test
    fun testUseProguardConfigurationFile_multilineRule() {

        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
        }
        val jarInput =
            TransformTestHelper.singleJarBuilder(classes.toFile())
                .setContentTypes(CLASSES).build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .addInput(jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val configFile = newFile("-keep\n    public class test.A")

        val transform = ProGuardTransform(createScope())

        transform.setConfigurationFiles(FakeFileCollection(configFile))

        transform.transform(invocation)

        val resultJar = resultJar()

        MoreTruth.assertThat(resultJar).contains("test/A.class")
        MoreTruth.assertThat(resultJar).doesNotContain("test/B.class")
    }

    private fun resultJar() : Zip {
        return Zip(Files.walk(outputDir).filter { it.toString().endsWith(".jar") }.toList().single())
    }

    private fun newFile(withContent: String): File {
        val f = tmp.newFile()
        f.writeText(withContent)
        return f
    }

    private fun createScope(configFiles: Set<File> = setOf()) : VariantScope {
        val androidBuilder = Mockito.mock(AndroidBuilder::class.java)
        val bootClassPath = listOf(TestUtils.getPlatformFile("android.jar"))
        Mockito.`when`(androidBuilder.getBootClasspath(anyBoolean())).thenReturn(bootClassPath)

        val project = Mockito.mock(Project::class.java)
        val configFilesCollection = FakeConfigurableFileCollection(configFiles)
        Mockito.`when`(project.files()).thenReturn(configFilesCollection)

        val globalScope = Mockito.mock(GlobalScope::class.java)
        Mockito.`when`(globalScope.androidBuilder).thenReturn(androidBuilder)
        Mockito.`when`(globalScope.project).thenReturn(project)
        Mockito.`when`(globalScope.buildDir).thenReturn(outputDir.toFile())

        val variantData = Mockito.mock(BaseVariantData::class.java)
        Mockito.`when`(variantData.type).thenReturn(VariantTypeImpl.BASE_APK)

        val variantConfig = Mockito.mock(GradleVariantConfiguration::class.java)
        Mockito.`when`(variantConfig.dirName).thenReturn(variantConfigDirName)

        val scope = Mockito.mock(VariantScope::class.java)
        Mockito.`when`(scope.globalScope).thenReturn(globalScope)
        Mockito.`when`(scope.variantData).thenReturn(variantData)
        Mockito.`when`(scope.variantConfiguration).thenReturn(variantConfig)
        Mockito.`when`(scope.bootClasspath).thenReturn(FakeFileCollection(bootClassPath))
        Mockito.`when`(scope.outputProguardMappingFile)
            .thenReturn(tmp.root.resolve("mapping/mapping.txt"))
        return scope
    }
}