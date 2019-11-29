/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.library;

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for libMinifyLibDep. */
@RunWith(FilterableParameterized.class)
public class LibMinifyLibDepTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libMinifyLibDep").create();

    @Parameterized.Parameter public CodeShrinker shrinker;

    @Parameterized.Parameters(name = "shrinker={0}")
    public static CodeShrinker[] getSetups() {
        return CodeShrinker.values();
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        executor().run("lint");
    }

    @Test
    public void checkProguard() throws Exception {
        executor().run("assembleDebug");
        File mapping = project.getSubproject("lib").file("build/outputs/mapping/debug/mapping.txt");
        // Check classes are obfuscated unless it is kept by the proguard configuration.
        assertThat(mapping)
                .containsAllOf(
                        "com.android.tests.basic.StringGetter -> com.android.tests.basic.StringGetter",
                        "com.android.tests.internal.StringGetterInternal ->");
        // Assert StringGetterInternal has been renamed, so it must not map to itself.
        assertThat(mapping)
                .doesNotContain(
                        "com.android.tests.internal.StringGetterInternal -> com.android.tests.internal.StringGetterInternal");
    }

    @Test
    public void checkTestAssemblyWithR8() throws Exception {
        executor().with(BooleanOption.ENABLE_R8, true).run("assembleAndroidTest");
    }

    @Test
    public void checkTestAssemblyWithProguard() throws Exception {
        executor().with(BooleanOption.ENABLE_R8, false).run("assembleAndroidTest");
    }

    @NonNull
    private GradleTaskExecutor executor() {
        return project.executor().with(BooleanOption.ENABLE_R8, shrinker == CodeShrinker.R8);
    }
}
