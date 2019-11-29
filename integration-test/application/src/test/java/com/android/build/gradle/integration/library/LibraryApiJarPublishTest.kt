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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Test to make sure we publish non-instrumented library jar for API. */
class LibraryApiJarPublishTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldLibraryApp.create())
        .create()

    @Test
    fun testApiJarPublishing() {
        project.getSubproject("lib").buildFile.appendText(
            """import com.android.build.api.transform.*
            |class BrokenTransform extends Transform {
            |    public String getName() { return ""; }
            |    public Set<QualifiedContent.ContentType> getInputTypes() {
            |        return Collections.singleton(QualifiedContent.DefaultContentType.CLASSES);
            |    }
            |    public Set<? super QualifiedContent.Scope> getScopes() {
            |        return Collections.singleton(QualifiedContent.Scope.PROJECT);
            |    }
            |    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
            |        return Collections.emptySet();
            |    }
            |    public boolean isIncremental() { return false; }
            |    public void transform(TransformInvocation transformInvocation) {
            |        throw new RuntimeException("Incorrect transform");
            |    }
            |}
            |android {
            |    registerTransform(new BrokenTransform())
            |}
            """.trimMargin()
        )

        val result = project.executor().expectFailure().run(":lib:bundleLibRuntimeDebug")
        assertThat(result.failureMessage).contains("Incorrect transform")

        // make sure we do not use transformed library for compilation
        project.executor().run(":app:compileDebugJavaWithJavac")
    }
}
