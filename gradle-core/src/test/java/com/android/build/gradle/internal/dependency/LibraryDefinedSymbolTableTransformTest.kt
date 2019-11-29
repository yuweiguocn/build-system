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

package com.android.build.gradle.internal.dependency

import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class LibraryDefinedSymbolTableTransformTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var explodedAar: File

    @Before
    fun setup() {
        explodedAar = temporaryFolder.newFolder("aar")
        val resDir = File(explodedAar, "res")
        val valuesDir = File(resDir, "values")
        FileUtils.mkdirs(valuesDir)

        val valuesXml = File(valuesDir, "values.xml")
        Files.write(
                valuesXml.toPath(),
                ImmutableList.of(
                        "<resources>",
                        "    <string name=\"app_name\">My app</string>",
                        "    <string name=\"desc\">It does something</string>",
                        "</resources>"))

        val stylesXml = File(valuesDir, "styles.xml")
        Files.write(
                stylesXml.toPath(),
                ImmutableList.of(
                        "<resources>",
                        "    <attr name=\"myAttr\" format=\"color\" />",
                        "    <declare-styleable name=\"ds\">",
                        "        <attr name=\"android:name\" />",
                        "        <attr name=\"android:color\" />",
                        "        <attr name=\"myAttr\" />",
                        "    </declare-styleable>",
                        "</resources>"))
        val stylesReversedXml = File(valuesDir, "stylesReversed.xml")
        Files.write(
            stylesReversedXml.toPath(),
            ImmutableList.of(
                "<resources>",
                "    <declare-styleable name=\"ds2\">",
                "        <attr name=\"myAttr2\" />",
                "        <attr name=\"maybeAttr\" />",
                "    </declare-styleable>",
                "<attr name=\"myAttr2\" format=\"color\" />",
                "</resources>"))

        val manifest = File(explodedAar, "AndroidManifest.xml")
        Files.write(
                manifest.toPath(),
                ImmutableList.of(
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "    package=\"com.example.mylibrary\" >",
                        "</manifest>"))
    }

    @Test
    fun parseAar() {
        val transform = LibraryDefinedSymbolTableTransform()

        val outputDir = temporaryFolder.newFolder("out")
        transform.outputDirectory = outputDir

        val result = transform.transform(explodedAar)

        Truth.assertThat(result).hasSize(1)
        Truth.assertThat(result[0].name).isEqualTo("com.example.mylibrary-R-def.txt")

        val expected = SymbolTable.builder()
            .tablePackage("com.example.mylibrary")
            .add(Symbol.AttributeSymbol("myAttr", 0, false))
            .add(Symbol.AttributeSymbol("maybeAttr", 0, true))
            .add(Symbol.AttributeSymbol("myAttr2", 0, false))
            .add(Symbol.NormalSymbol(ResourceType.STRING, "app_name", 0))
            .add(Symbol.NormalSymbol(ResourceType.STRING, "desc", 0))
            .add(
                Symbol.StyleableSymbol(
                    "ds",
                    ImmutableList.of(),
                ImmutableList.of("android:name", "android:color", "myAttr")
                )
            )
            .add(
                Symbol.StyleableSymbol(
                    "ds2",
                    ImmutableList.of(),
                    ImmutableList.of("myAttr2", "maybeAttr")
                )
            )
            .build()

        val actual = SymbolIo.readRDef(result[0].toPath())
        assertThat(actual).isEqualTo(expected)
    }
}