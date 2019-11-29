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

package com.android.builder.symbols

import com.android.SdkConstants
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.parseArrayLiteral
import com.android.ide.common.symbols.valueStringToInt
import com.android.resources.ResourceType
import com.android.testutils.apk.Zip
import com.android.utils.PathUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.lang.reflect.Field
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.streams.toList

class BytecodeRClassWriterTest {
    @Rule
    @JvmField
    var mTemporaryFolder = TemporaryFolder()

    @Test
    fun generateRFilesTest() {
        val rJar = mTemporaryFolder.newFile("R.jar")

        val symbols = SymbolTable.builder()
                .tablePackage("com.example.foo")
                .add(Symbol.NormalSymbol(ResourceType.ID, "foo", 0x0))
                .add(
                    Symbol.NormalSymbol(
                                ResourceType.DRAWABLE,
                                "bar",
                                0x1))
                .add(Symbol.AttributeSymbol("beep", 0x3))
                .add(Symbol.AttributeSymbol("boop", 0x5))
                .add(
                    Symbol.StyleableSymbol(
                                "styles",
                                ImmutableList.of(0x2, 0x4),
                                ImmutableList.of("style1", "style2")))
                .build()

        exportToCompiledJava(listOf(symbols), rJar.toPath())

        Zip(rJar).use {
            assertThat(it.entries).hasSize(5)

            assertThat(it.entries.map { f -> f.toString() }).containsExactly(
                    "/com/example/foo/R.class",
                    "/com/example/foo/R\$id.class",
                    "/com/example/foo/R\$drawable.class",
                    "/com/example/foo/R\$styleable.class",
                    "/com/example/foo/R\$attr.class")
        }
    }

    @Test
    fun generateRFilesContentTest() {
        val javacCompiledDir = mTemporaryFolder.newFolder("javac-compiled")
        val generatedRJar = mTemporaryFolder.newFile("R.jar")
        val rDotJavaDir = mTemporaryFolder.newFolder("source")

        val appSymbols = SymbolTable.builder()
                .tablePackage("com.example.foo.app")
                .add(Symbol.AttributeSymbol("beep", 0x1))
                .add(Symbol.AttributeSymbol("boop", 0x3))
                .add(
                    Symbol.StyleableSymbol(
                                "styles",
                        ImmutableList.of(0x1004, 0x1002),
                                ImmutableList.of("styles_boop", "styles_beep")))
                .add(
                    Symbol.StyleableSymbol(
                                "other_style",
                        ImmutableList.of(0x1004, 0x1002),
                                ImmutableList.of("foo", "bar.two")))
                .add(Symbol.NormalSymbol(ResourceType.STRING, "libstring", 0x4))
                .build()

        val librarySymbols = SymbolTable.builder()
                .tablePackage("com.example.foo.lib")
                .add(Symbol.NormalSymbol(ResourceType.STRING, "libstring",0x4))
                .build()

        // The existing path: Symbol table --com.android.builder.symbols.exportToJava--> R.java --javac--> R classes
        // Generate the R.java file.
        val appRDotJava = SymbolIo.exportToJava(appSymbols, rDotJavaDir, false)
        val libRDotJava = SymbolIo.exportToJava(librarySymbols, rDotJavaDir, false)
        val javac = ToolProvider.getSystemJavaCompiler()
        val manager = javac.getStandardFileManager(null, null, null)
        // Use javac to compile R.java into R.class, R$id.class. etc.
        val source = manager.getJavaFileObjectsFromFiles(ImmutableList.of(libRDotJava, appRDotJava)) as Iterable<JavaFileObject>
        javac.getTask(null,
                manager, null,
                ImmutableList.of("-d", javacCompiledDir.absolutePath), null,
                source)
                .call()

        val expectedClasses = listOf(
                "com.example.foo.lib.R",
                "com.example.foo.lib.R\$string",
                "com.example.foo.app.R",
                "com.example.foo.app.R\$string",
                "com.example.foo.app.R\$styleable",
                "com.example.foo.app.R\$attr")

        // Sanity check
        assertThat(files(javacCompiledDir.toPath())).containsExactlyElementsIn(expectedClasses)

        // And the method under test.
        exportToCompiledJava(listOf(appSymbols, librarySymbols), generatedRJar.toPath())

        Zip(generatedRJar).use {
            assertThat(it.entries.map { className(it.root.relativize(it)) })
                    .containsExactlyElementsIn(expectedClasses)
        }
        URLClassLoader(arrayOf(javacCompiledDir.toURI().toURL()), null).use { expectedClassLoader ->
            URLClassLoader(arrayOf(generatedRJar.toURI().toURL()), null).use { actualClassLoader ->
                for (javaName in expectedClasses) {
                    val expectedFields = loadFields(expectedClassLoader, javaName)
                    val actualFields = loadFields(actualClassLoader, javaName)
                    assertThat(actualFields).containsExactlyElementsIn(expectedFields)
                }
            }
        }
    }

    @Test
    fun testParseArrayLiteral() {
        assertThat(parseArrayLiteral(0, "{}").asList()).isEmpty()
        assertThat(parseArrayLiteral(1, "{0x7f04002c}").asList())
                .containsExactly(0x7f04002c)
        assertThat(parseArrayLiteral(1, "{0x70}").asList())
                .containsExactly(0x70)
        assertThat(parseArrayLiteral(5, "{ 0x72, 0x73, 0x74, 0x71, 0x70 }").asList())
                .containsExactly(0x72, 0x73, 0x74, 0x71, 0x70)
        assertThat(parseArrayLiteral(2, "{     0x71    ,    0x72   }").asList())
                .containsExactly(0x71, 0x72)

        try {
            parseArrayLiteral(3, "{0x1,0x2}")
            fail("Expected failure - too few listed values")
        } catch (e: IOException) {
            assertThat(e).hasMessageThat().contains("should have 3 item(s)")
        }

        try {
            parseArrayLiteral(1, "{0x1,0x2}")
            fail("Expected failure - too many listed values")
        } catch (e: IOException) {
            assertThat(e).hasMessageThat().contains("should have 1 item(s)")
        }

    }

    @Test
    fun testValueToInt() {
        assertThat(valueStringToInt("0x7f04002c")).isEqualTo(0x7f04002c)
    }

    private fun loadFields(classLoader: ClassLoader, name: String) =
            classLoader.loadClass(name)
                    .fields
                    .map { field ->
                        "${field.type.typeName} ${field.name} = ${valueAsString(field)}" }
                    .toList()

    private fun valueAsString(field: Field) = when (field.type.typeName) {
        "int" -> field.get(null)
        "int[]" -> "[" + (field.get(null) as IntArray).joinToString(",") + "]"
        else -> throw IllegalStateException("Unexpected type " + field.type.typeName)
    }

    private fun files(dir: Path) =
            Files.walk(dir).use {
                it.filter { Files.isRegularFile(it) }
                    .map { className(dir.relativize(it)) }
                    .toList()
            }

    private fun className(relativePath: Path): String =
            PathUtils.toSystemIndependentPath(relativePath)
                    .removeSuffix(SdkConstants.DOT_CLASS)
                    .replace('/', '.')

}
