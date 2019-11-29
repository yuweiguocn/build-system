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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.repository.Revision;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for renderscript with NDK mode enabled. */
public class RenderscriptNdkTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("renderscriptNdk")
                    .addGradleProperties("android.useDeprecatedNdk=true")
                    .create();

    @Before
    public void setUp() throws IOException, InterruptedException {
        Revision ndkRevision = NdkHandler.findRevision(project.getAndroidNdkHome());
        // ndk r11, r12 are missing renderscript, ndk r10 does support it and has null revision
        assume().that(ndkRevision).isNull();

        project.execute("clean", "assembleDebug");
    }

    @Test
    public void checkPackagedDotSoFiles() throws IOException {
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/armeabi-v7a/librs.mono.so");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/armeabi-v7a/librenderscript.so");
    }
}
