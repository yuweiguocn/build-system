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
@file:JvmName("SymbolExportUtils")

package com.android.builder.symbols

import com.android.annotations.VisibleForTesting
import com.android.ide.common.symbols.RGeneration
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.generateMinifyKeepRules
import com.android.ide.common.symbols.getPackageNameFromManifest
import com.android.ide.common.symbols.loadDependenciesSymbolTables
import com.android.ide.common.symbols.mergeAndRenumberSymbols
import com.android.ide.common.symbols.parseManifest
import com.android.utils.FileUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Processes the symbol table and generates necessary files: R.txt, R.java and proguard rules
 * (`aapt_rules.txt`). Afterwards generates `R.java` for all libraries the main
 * library depends on.
 *
 * @param librarySymbols table with symbols of resources for the library.
 * @param libraries libraries which this library depends on
 * @param mainPackageName package name of this library
 * @param manifestFile manifest file
 * @param sourceOut directory to contain R.java
 * @param rClassOutputJar file to output R.jar.
 * @param symbolFileOut R.txt file location
 * @param proguardOut directory to contain proguard rules
 * @param mergedResources directory containing merged resources
 * @param namespacedRClass if true, the generated R class for this library and the  R.txt will
 *                         contain only the resources defined in this library, otherwise they will
 *                         contain all the resources merged from the transitive dependencies.
 */
@Throws(IOException::class)
fun processLibraryMainSymbolTable(
        librarySymbols: SymbolTable,
        libraries: Set<File>,
        mainPackageName: String?,
        manifestFile: File,
        sourceOut: File?,
        rClassOutputJar: File?,
        symbolFileOut: File,
        proguardOut: File?,
        mergedResources: File?,
        platformSymbols: SymbolTable,
        namespacedRClass: Boolean) {

    // Parse the manifest only when necessary.
    val finalPackageName = if (mainPackageName == null || proguardOut != null) {
        val manifestData = parseManifest(manifestFile)
        // Generate aapt_rules.txt containing keep rules if minify is enabled.
        if (proguardOut != null) {
            Files.write(
                    proguardOut.toPath(),
                    generateMinifyKeepRules(manifestData, mergedResources))
        }
        mainPackageName ?: getPackageNameFromManifest(manifestData)
    } else {
        mainPackageName
    }

    // Get symbol tables of the libraries we depend on.
    val depSymbolTables = loadDependenciesSymbolTables(libraries)
    val tablesToWrite =
        processLibraryMainSymbolTable(
            finalPackageName,
            librarySymbols,
            depSymbolTables,
            platformSymbols,
            namespacedRClass,
            symbolFileOut.toPath()
        )

    if (sourceOut != null) {
        FileUtils.cleanOutputDir(sourceOut)
        // Generate R.java files for main and dependencies
        tablesToWrite.forEach { SymbolIo.exportToJava(it, sourceOut, false) }
    }

    if (rClassOutputJar != null) {
        FileUtils.deleteIfExists(rClassOutputJar)
        exportToCompiledJava(tablesToWrite, rClassOutputJar.toPath())
    }
}

@VisibleForTesting
internal fun processLibraryMainSymbolTable(
    finalPackageName: String,
    librarySymbols: SymbolTable,
    depSymbolTables: Set<SymbolTable>,
    platformSymbols: SymbolTable,
    namespacedRClass: Boolean,
    symbolFileOut: Path
): List<SymbolTable> {
    // Merge all the symbols together.
    // We have to rewrite the IDs because some published R.txt inside AARs are using the
    // wrong value for some types, and we need to ensure there is no collision in the
    // file we are creating.
    val allSymbols: SymbolTable = mergeAndRenumberSymbols(
        finalPackageName, librarySymbols, depSymbolTables, platformSymbols
    )

    val mainSymbolTable = if (namespacedRClass) allSymbols.filter(librarySymbols) else allSymbols

    // Generate R.txt file.
    Files.createDirectories(symbolFileOut.parent)
    SymbolIo.writeForAar(mainSymbolTable, symbolFileOut)

    val tablesToWrite =
        RGeneration.generateAllSymbolTablesToWrite(allSymbols, mainSymbolTable, depSymbolTables)
    return tablesToWrite
}

