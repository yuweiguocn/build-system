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

package com.android.builder.symbols

import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SymbolExportUtilsTest {

    @Test
    fun sanityCheckNamespacedRClass() {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val symbolFileOut = fileSystem.getPath("/tmp/out/R.txt")

        val depSymbolTable =
            SymbolTable.builder()
                .tablePackage("com.example.lib")
                .add(Symbol.createAndValidateSymbol(ResourceType.STRING, "libstring", 0))
                .build()
        val librarySymbols =
            SymbolTable.builder()
                .tablePackage("com.example.mylib")
                .add(Symbol.createAndValidateSymbol(ResourceType.STRING, "mystring", 0))
                .build()

        val processedSymbols = processLibraryMainSymbolTable(
            finalPackageName = "com.example.mylib",
            librarySymbols = librarySymbols,
            depSymbolTables = setOf(depSymbolTable),
            platformSymbols = SymbolTable.builder().build(),
            namespacedRClass = false,
            symbolFileOut = symbolFileOut
        )

        assertThat(symbolFileOut).exists()
        assertThat(symbolFileOut).hasContents(
            "int string libstring 0x7f140001",
            "int string mystring 0x7f140002"
        )

        assertThat(processedSymbols).hasSize(2)
        val myProcessedSymbols = processedSymbols[0]
        val libraryProcessedSymbols = processedSymbols[1]
        assertThat(myProcessedSymbols.tablePackage).isEqualTo("com.example.mylib")
        assertThat(myProcessedSymbols.symbols.columnKeySet())
            .containsExactly("libstring", "mystring")
        assertThat(libraryProcessedSymbols.tablePackage).isEqualTo("com.example.lib")
        assertThat(libraryProcessedSymbols.symbols.columnKeySet()).containsExactly("libstring")
    }

    @Test
    fun sanityCheckNonNamespacedRClass() {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val symbolFileOut = fileSystem.getPath("/tmp/out/R.txt")

        val depSymbolTable =
            SymbolTable.builder()
                .tablePackage("com.example.lib")
                .add(Symbol.createAndValidateSymbol(ResourceType.STRING, "libstring", 0))
                .build()
        val librarySymbols =
            SymbolTable.builder()
                .tablePackage("com.example.mylib")
                .add(Symbol.createAndValidateSymbol(ResourceType.STRING, "mystring", 0))
                .build()

        val processedSymbols = processLibraryMainSymbolTable(
            finalPackageName = "com.example.mylib",
            librarySymbols = librarySymbols,
            depSymbolTables = setOf(depSymbolTable),
            platformSymbols = SymbolTable.builder().build(),
            namespacedRClass = true,
            symbolFileOut = symbolFileOut
        )

        assertThat(symbolFileOut).exists()
        assertThat(symbolFileOut).hasContents("int string mystring 0x7f140002")

        assertThat(processedSymbols).hasSize(2)
        val myProcessedSymbols = processedSymbols[0]
        val libraryProcessedSymbols = processedSymbols[1]
        assertThat(myProcessedSymbols.tablePackage).isEqualTo("com.example.mylib")
        assertThat(myProcessedSymbols.symbols.columnKeySet()).containsExactly("mystring")
        assertThat(libraryProcessedSymbols.tablePackage).isEqualTo("com.example.lib")
        assertThat(libraryProcessedSymbols.symbols.columnKeySet()).containsExactly("libstring")
    }
}