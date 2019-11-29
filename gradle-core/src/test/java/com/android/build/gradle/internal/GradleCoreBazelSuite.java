/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.testutils.BazelRunfilesManifestProcessor;
import com.android.testutils.JarTestSuiteRunner;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/** Suite used to run tests with Bazel. Skips tests that are known to fail under Bazel for now. */
@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
    GradleCoreBazelSuite.class,
    com.android.build.gradle.external.gnumake.NdkSampleTest.class,
})
public class GradleCoreBazelSuite {
    @BeforeClass
    public static void setUp() {
        BazelRunfilesManifestProcessor.setUpRunfiles();
    }
}
