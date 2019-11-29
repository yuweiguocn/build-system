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

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.RELEASE;
import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.testutils.apk.Apk;
import java.util.HashMap;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
/**
 * Assemble tests for embedded.
 */
public class WearVariantTest {

    static class VariantInfo {
        ApkType apkType;
        String[] flavors;

        static VariantInfo of(ApkType apkType, String... flavors) {
            return new VariantInfo(apkType, flavors);
        }

        public VariantInfo(ApkType apkType, String[] flavors) {
            this.apkType = apkType;
            this.flavors = flavors;
        }
    }

    private static final ApkType CUSTOM = ApkType.of("custom", false);

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("embedded")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        project.execute("clean", ":main:assemble");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkEmbedded() throws Exception {
        String embeddedApkPath = FD_RES + '/' + FD_RES_RAW + '/' + ANDROID_WEAR_MICRO_APK +
                DOT_ANDROID_PACKAGE;

        // each micro app has a different version name to distinguish them from one another.
        // here we record what we expect from which.
        // Apk Type           Version name
        //---------------     ------------

        Map<VariantInfo, String> variants = new HashMap<>();
        variants.put(VariantInfo.of(RELEASE, "flavor1"), "flavor1");
        variants.put(VariantInfo.of(RELEASE, "flavor2"), "default");
        variants.put(VariantInfo.of(CUSTOM, "flavor1"), "custom");
        variants.put(VariantInfo.of(CUSTOM, "flavor1"), "custom");
        variants.put(VariantInfo.of(DEBUG, "flavor1"), null);
        variants.put(VariantInfo.of(DEBUG, "flavor2"), null);

        //List<List<String>> variantData = Lists.newArrayList(
        //        //Output apk name             Version name
        //        //---------------             ------------
        //        Lists.newArrayList( "flavor1-release-unsigned", "flavor1" ),
        //        Lists.newArrayList( "flavor2-release-unsigned", "default" ),
        //        Lists.newArrayList( "flavor1-custom-unsigned",  "custom" ),
        //        Lists.newArrayList( "flavor2-custom-unsigned",  "custom" ),
        //        Lists.newArrayList( "flavor1-debug",            null ),
        //        Lists.newArrayList( "flavor2-debug",            null )
        //);

        for (Map.Entry<VariantInfo, String> variant : variants.entrySet()) {
            VariantInfo apkInfo = variant.getKey();
            String versionName = variant.getValue();
            Apk fullApk = project.getSubproject("main").getApk(apkInfo.apkType, apkInfo.flavors);
            assertThat(fullApk).isNotNull();
            assertThat(fullApk.getFile()).isFile();

            if (versionName == null) {
                assertThat(fullApk).doesNotContain(embeddedApkPath);
            } else {
                Apk embeddedApk = new Apk(fullApk.getEntryAsZip(embeddedApkPath).getFile());
                assertThat(embeddedApk)
                        .named("Embedded apk '" + embeddedApkPath + "' for:" + fullApk.getFile())
                        .isNotNull();

                // check for the versionName
                assertThat(embeddedApk).hasVersionName(versionName);
            }
        }
    }
}
