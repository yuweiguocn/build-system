/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.apk.Apk;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests that ensure that java resources files accessed with a relative or absolute path are
 * packaged correctly.
 */
@RunWith(FilterableParameterized.class)
public class MinifyLibAndAppWithJavaResTest {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] data() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD, CodeShrinker.R8};
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minifyLibWithJavaRes").create();

    @Parameterized.Parameter() public CodeShrinker codeShrinker;

    @Test
    public void testDebugPackaging() throws Exception {
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run(":app:assembleDebug");
        Apk debugApk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        assertNotNull(debugApk);
        // check that resources with relative path lookup code have a matching obfuscated package
        // name.
        assertThat(debugApk).contains("com/android/tests/util/resources.properties");
        assertThat(debugApk).contains("com/android/tests/other/resources.properties");
        // check that resources with absolute path lookup remain in the original package name.
        assertThat(debugApk).contains("com/android/tests/util/another.properties");
        assertThat(debugApk).contains("com/android/tests/other/some.xml");
        assertThat(debugApk).contains("com/android/tests/other/another.properties");
    }

    @Test
    public void testReleasePackaging() throws Exception {
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run(":app:assembleRelease");
        Apk releaseApk =
                project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE_SIGNED);
        assertNotNull(releaseApk);
        // check that resources with absolute path lookup remain in the original package name.
        assertThat(releaseApk).contains("com/android/tests/util/another.properties");
        assertThat(releaseApk).contains("com/android/tests/other/some.xml");
        assertThat(releaseApk).contains("com/android/tests/other/another.properties");

        // check that resources with relative path lookup code have a matching obfuscated
        // package name.
        if (codeShrinker == CodeShrinker.PROGUARD) {
            assertThat(releaseApk).contains("com/android/tests/b/resources.properties");
            assertThat(releaseApk).contains("com/android/tests/a/resources.properties");
        } else {
            assertThat(releaseApk).contains("a/a/a/b/resources.properties");
            assertThat(releaseApk).contains("a/a/a/a/resources.properties");
        }
    }
}
