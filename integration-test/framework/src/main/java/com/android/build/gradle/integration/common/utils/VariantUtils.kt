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

@file:JvmName("VariantUtils")
package com.android.build.gradle.integration.common.utils

import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.JavaArtifact
import com.android.builder.model.Variant
import com.android.builder.model.level2.GlobalLibraryMap
import com.android.builder.model.level2.GraphItem
import com.android.builder.model.level2.Library
import com.google.common.truth.Truth

fun Variant.getAndroidTestArtifact(): AndroidArtifact {
    return getExtraAndroidArtifactByName(AndroidProject.ARTIFACT_ANDROID_TEST)
}

fun Variant.getUnitTestArtifact(): JavaArtifact {
    return getExtraJavaArtifactByName(AndroidProject.ARTIFACT_UNIT_TEST)
}

/**
 * return the only item with the given name, or throw an exception if 0 or 2+ items match
 */
fun Variant.getExtraAndroidArtifactByName(name: String): AndroidArtifact {
    return searchForExistingItem(extraAndroidArtifacts, name, AndroidArtifact::getName, "AndroidArtifact")
}

/**
 * Gets the java artifact with the given name.
 *
 * @param name the name to match, e.g. [AndroidProject.ARTIFACT_UNIT_TEST]
 * @return the only item with the given name
 * @throws AssertionError if no items match or if multiple items match
 */
fun Variant.getExtraJavaArtifactByName(name: String): JavaArtifact {
    return searchForExistingItem(extraJavaArtifacts, name, JavaArtifact::getName, "JavaArtifact")
}

/**
 * search for an item matching the name and return it if found.
 *
 */
fun Variant.getOptionalAndroidArtifact(name: String): AndroidArtifact? {
    return searchForOptionalItem(extraAndroidArtifacts, name, AndroidArtifact::getName)
}

/**
 * tests the dependencies of the main artifact with the provided lists.
 *
 * @param expectedAndroidModules the list of android sub modules as a list of
 *                               (build-id, project-path) pairs
 * @param expectedJavaModules the list of java sub modules as a list of (build-id, project-path)
 *                            pairs
 * @param moduleName an optional module name to display in case of error
 * @param globalLibrary a global library to resolve [GraphItem]. If null, then level 3 model is
 *                      used to find dependencies, otherwise level 4 model is used.
 */
fun Variant.testSubModuleDependencies(
        expectedAndroidModules: List<Pair<String, String>>,
        expectedJavaModules: List<Pair<String, String>>,
        moduleName: String? = null,
        containsExactly: Boolean = true,
        globalLibrary: GlobalLibraryMap? = null) {

    if (globalLibrary != null) {
        // in the case of a global library (level 4), we don't differentiate java vs android
        // modules in the model.
        // build a list of (build-id, path) pairs for sub-modules
        val actualModule: List<Pair<String, String>> =
                mainArtifact.dependencyGraphs.compileDependencies.asSequence()
                        .map { globalLibrary.libraries[it.artifactAddress]!! }
                        .filter { it.type == Library.LIBRARY_MODULE }
                        .map { it.buildId!! to it.projectPath!! }
                        .toList()

        val expectedModules: MutableList<Pair<String, String>> = mutableListOf()
        expectedModules.addAll(expectedAndroidModules)
        expectedModules.addAll(expectedJavaModules)

        val subject = Truth.assertThat(actualModule)
                .named(if (moduleName != null)
                    "Module Dependencies of $moduleName"
                else
                    "Module Dependencies")

        if (containsExactly) {
            subject.containsExactlyElementsIn(expectedModules)
        } else {
            subject.containsAllIn(expectedModules)
        }

    } else {

        // build a list of (build-id, path) pairs for sub-modules
        val actualAndroidModules: List<Pair<String, String>> =
                mainArtifact.dependencies.libraries.asSequence()
                        .filter { it.project != null }
                    .map { it.buildId!! to it.project!! }
                    .toList()
        val actualJavaModules: List<Pair<String, String>> =
                mainArtifact.dependencies.javaModules.map { it.buildId to it.projectPath }

        val androidSubject = Truth.assertThat(actualAndroidModules)
                .named(if (moduleName != null)
                    "Android Dependencies of $moduleName"
                else
                    "Android Dependencies")

        if (containsExactly) {
            androidSubject.containsExactlyElementsIn(expectedAndroidModules)
        } else {
            androidSubject.containsAllIn(expectedAndroidModules)
        }

        val javaSubject = Truth.assertThat(actualJavaModules)
                .named(if (moduleName != null)
                    "Java Dependencies of $moduleName"
                else
                    "Java Dependencies")

        if (containsExactly) {
            javaSubject.containsExactlyElementsIn(expectedJavaModules)
        } else {
            javaSubject.containsAllIn(expectedJavaModules)
        }
    }
}
