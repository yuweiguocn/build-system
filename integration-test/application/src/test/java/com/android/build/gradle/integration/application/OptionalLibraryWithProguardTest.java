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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test for the new optional library mechanism when a library dependency uses some now optional
 * classes and runs proguard, in which case proguard needs to see the optional classes.
 */
@RunWith(FilterableParameterized.class)
public class OptionalLibraryWithProguardTest {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] data() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD, CodeShrinker.R8};
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("optionalLibInLibWithProguard").create();

    @Test
    public void testThatProguardCompilesWithOptionalClasses() throws Exception {
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("clean", "app:assembleDebug");
    }

    @Test
    public void testUnitTestWithOptionalClasses() throws Exception {
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("clean", "mylibrary:test");
    }
}
