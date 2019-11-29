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

@file:JvmName("DesugaringProjectConfigurator")

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject

fun configureR8Desugaring(project: GradleTestProject) {
    // keep all classes, do not rename them, as we'd like to assert things about original ones
    project.buildFile.resolveSibling("proguard.txt").writeText(
            """
                -keep class *{*;}
                -dontobfuscate
                """
    )

    project.buildFile.appendText(
            """
                android.buildTypes.debug.minifyEnabled true
                android.buildTypes.debug.proguardFiles 'proguard.txt'
                """
    )
}