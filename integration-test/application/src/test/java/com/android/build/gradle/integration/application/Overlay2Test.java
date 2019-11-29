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

package com.android.build.gradle.integration.application;


import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ImageHelper;
import com.android.builder.model.AndroidProject;
import com.android.testutils.apk.Apk;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for overlay2. */
public class Overlay2Test {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("overlay2").create();

    @Test
    public void checkImageColor() throws IOException, InterruptedException {
        project.executor().run("clean", "assembleDebug");

        int GREEN = ImageHelper.GREEN;

        File resOutput =
                project.file("build/" + AndroidProject.FD_INTERMEDIATES + "/res/merged/one/debug");
        assertThat(new File(resOutput, "drawable_no_overlay.png.flat")).exists();
        assertThat(new File(resOutput, "drawable_type_overlay.png.flat")).exists();
        assertThat(new File(resOutput, "drawable_flavor_overlay.png.flat")).exists();
        assertThat(new File(resOutput, "drawable_type_flavor_overlay.png.flat")).exists();
        assertThat(new File(resOutput, "drawable_variant_type_flavor_overlay.png.flat")).exists();


        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "one");
        //First picture should remain unchanged (first pixel remains green), while all the
        //others should have the first image overlay them (first pixel turns from red to green).
        ImageHelper.checkImageColor(apk.getResource("drawable/no_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/type_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/flavor_overlay.png"), GREEN);
        ImageHelper.checkImageColor(apk.getResource("drawable/type_flavor_overlay.png"), GREEN);
        ImageHelper.checkImageColor(
                apk.getResource("drawable/variant_type_flavor_overlay.png"), GREEN);
    }
}
