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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import static com.android.build.gradle.tasks.ResourceUsageAnalyzer.REPLACE_DELETED_WITH_EMPTY;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static com.google.common.truth.Truth.assertThat;
import static java.io.File.separator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.ResourceUsageAnalyzer;
import com.android.builder.model.AndroidProject;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for shrink. */
@RunWith(Parameterized.class)
public class ShrinkResourcesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("shrink").create();

    @Parameterized.Parameters(name = "shrinker {0} bundle={1}")
    public static Iterable<Object[]> data() {
        return ImmutableList.of(
                new Object[] {CodeShrinker.PROGUARD, ApkPipeline.NO_BUNDLE},
                new Object[] {CodeShrinker.R8, ApkPipeline.NO_BUNDLE},
                new Object[] {CodeShrinker.PROGUARD, ApkPipeline.BUNDLE},
                new Object[] {CodeShrinker.R8, ApkPipeline.BUNDLE});
    }

    @Parameterized.Parameter public CodeShrinker shrinker;

    @Parameterized.Parameter(1)
    public ApkPipeline apkPipeline;

    private enum ApkPipeline {
        NO_BUNDLE("assemble", ""),
        BUNDLE("package", "UniversalApk"),
        ;
        private final String taskPrefix;
        private final String taskSuffix;

        ApkPipeline(String taskPrefix, String taskSuffix) {
            this.taskPrefix = taskPrefix;
            this.taskSuffix = taskSuffix;
        }

        String taskName(String variant) {
            return taskPrefix + variant + taskSuffix;
        }
    }

    private Apk getApk(@NonNull ApkType apkType) throws IOException {
        switch (apkPipeline) {
            case NO_BUNDLE:
                return project.getApk(apkType);
            case BUNDLE:
                return project.getBundleUniversalApk(apkType);
        }
        throw new IllegalStateException();
    }

    private File getCompressed(@NonNull GradleTestProject project) {
        switch (apkPipeline) {
            case NO_BUNDLE:
                return project.getIntermediateFile(
                        "res_stripped", "release", "resources-release-stripped.ap_");
            case BUNDLE:
                return project.getIntermediateFile(
                        "shrunk_linked_res_for_bundle",
                        "release",
                        "shrinkReleaseResources",
                        "shrunk-bundled-res.ap_");
        }
        throw new IllegalStateException();
    }

    private File getUncompressed(@NonNull GradleTestProject project) {
        switch (apkPipeline) {
            case NO_BUNDLE:
                return project.getIntermediateFile(
                        "processed_res",
                        "release",
                        "processReleaseResources",
                        "out",
                        "resources-release.ap_");
            case BUNDLE:
                return project.getIntermediateFile(
                        "linked_res_for_bundle",
                        "release",
                        "bundleReleaseResources",
                        "bundled-res.ap_");
        }
        throw new IllegalStateException();
    }

    private byte[] getIntermediateCompressedXml() {
        switch (apkPipeline) {
            case NO_BUNDLE:
                return ResourceUsageAnalyzer.TINY_BINARY_XML;
            case BUNDLE:
                return ResourceUsageAnalyzer.TINY_PROTO_XML;
        }
        throw new IllegalStateException();
    }

    private String getIntermediateResourceTableName() {
        switch (apkPipeline) {
            case NO_BUNDLE:
                //noinspection SpellCheckingInspection
                return "resources.arsc";
            case BUNDLE:
                return "resources.pb";
        }
        throw new IllegalStateException();
    }

    private String getIntermediateResourceTableNameAndMethod() {
        switch (apkPipeline) {
            case NO_BUNDLE:
                //noinspection SpellCheckingInspection
                return "  stored  resources.arsc";
            case BUNDLE:
                return "deflated  resources.pb";
        }
        throw new IllegalStateException();
    }

    private void useIntermediateResourceTableName(@NonNull List<String> expected) {
        switch (apkPipeline) {
            case NO_BUNDLE:
                return;
            case BUNDLE:
                expected.set(expected.indexOf("resources.arsc"), "resources.pb");
                return;
        }
        throw new IllegalStateException();
    }

    @Test
    public void checkShrinkResources() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release.useProguard = " + (shrinker == CodeShrinker.PROGUARD));

        project.executor()
                .with(BooleanOption.ENABLE_R8, shrinker == CodeShrinker.R8)
                .run(
                        "clean",
                        apkPipeline.taskName("Release"),
                        apkPipeline.taskName("Debug"),
                        apkPipeline.taskName("MinifyDontShrink"));

        File intermediates = project.file("build/" + AndroidProject.FD_INTERMEDIATES);

        // The release target has shrinking enabled.
        // The minifyDontShrink target has proguard but no shrinking enabled.
        // The debug target has neither proguard nor shrinking enabled.

        Apk apkRelease = getApk(ApkType.RELEASE);
        Apk apkDebug = getApk(ApkType.DEBUG);
        Apk apkProguardOnly = getApk(ApkType.of("minifyDontShrink", false));

        assertTrue(apkDebug.toString() + " is not a file", Files.isRegularFile(apkDebug.getFile()));
        assertTrue(
                apkRelease.toString() + " is not a file",
                Files.isRegularFile(apkRelease.getFile()));
        assertTrue(
                apkProguardOnly.toString() + " is not a file",
                Files.isRegularFile(apkProguardOnly.getFile()));

        File compressed = getCompressed(project);
        File uncompressed = getUncompressed(project);
        assertTrue(compressed.toString() + " is not a file", compressed.isFile());
        assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());

        // Check that there is no shrinking in the other two targets:
        assertTrue(
                FileUtils.join(
                                intermediates,
                                "processed_res",
                                "debug",
                                "processDebugResources",
                                "out",
                                "resources-debug.ap_")
                        .exists());
        assertFalse(
                FileUtils.join(
                                intermediates,
                                "res_stripped/debug" + separator + "resources-debug-stripped.ap_")
                        .exists());
        assertTrue(
                FileUtils.join(
                                intermediates,
                                "processed_res",
                                "minifyDontShrink",
                                "processMinifyDontShrinkResources",
                                "out",
                                "resources-minifyDontShrink.ap_")
                        .exists());
        assertFalse(
                new File(
                                intermediates,
                                "res_stripped/minifyDontShrink"
                                        + separator
                                        + "resources-minifyDontShrink-stripped.ap_")
                        .exists());

        List<String> expectedUnstrippedApk =
                ImmutableList.of(
                        "AndroidManifest.xml",
                        "classes.dex",
                        "res/drawable/force_remove.xml",
                        "res/raw/keep.xml",
                        "res/layout/l_used_a.xml",
                        "res/layout/l_used_b2.xml",
                        "res/layout/l_used_c.xml",
                        "res/layout/lib_unused.xml",
                        "res/layout-v17/notification_action.xml",
                        "res/layout-v21/notification_action.xml",
                        "res/layout/notification_action.xml",
                        "res/drawable-v21/notification_action_background.xml",
                        "res/layout-v17/notification_action_tombstone.xml",
                        "res/layout-v21/notification_action_tombstone.xml",
                        "res/layout/notification_action_tombstone.xml",
                        "res/drawable/notification_bg.xml",
                        "res/drawable/notification_bg_low.xml",
                        "res/drawable-hdpi-v4/notification_bg_low_normal.9.png",
                        "res/drawable-mdpi-v4/notification_bg_low_normal.9.png",
                        "res/drawable-xhdpi-v4/notification_bg_low_normal.9.png",
                        "res/drawable-hdpi-v4/notification_bg_low_pressed.9.png",
                        "res/drawable-mdpi-v4/notification_bg_low_pressed.9.png",
                        "res/drawable-xhdpi-v4/notification_bg_low_pressed.9.png",
                        "res/drawable-hdpi-v4/notification_bg_normal.9.png",
                        "res/drawable-mdpi-v4/notification_bg_normal.9.png",
                        "res/drawable-xhdpi-v4/notification_bg_normal.9.png",
                        "res/drawable-hdpi-v4/notification_bg_normal_pressed.9.png",
                        "res/drawable-mdpi-v4/notification_bg_normal_pressed.9.png",
                        "res/drawable-xhdpi-v4/notification_bg_normal_pressed.9.png",
                        "res/drawable/notification_icon_background.xml",
                        "res/layout/notification_media_action.xml",
                        "res/layout/notification_media_cancel_action.xml",
                        "res/layout-v17/notification_template_big_media.xml",
                        "res/layout/notification_template_big_media.xml",
                        "res/layout-v17/notification_template_big_media_custom.xml",
                        "res/layout/notification_template_big_media_custom.xml",
                        "res/layout-v17/notification_template_big_media_narrow.xml",
                        "res/layout/notification_template_big_media_narrow.xml",
                        "res/layout-v17/notification_template_big_media_narrow_custom.xml",
                        "res/layout/notification_template_big_media_narrow_custom.xml",
                        "res/layout-v16/notification_template_custom_big.xml",
                        "res/layout-v17/notification_template_custom_big.xml",
                        "res/layout-v21/notification_template_custom_big.xml",
                        "res/layout-v21/notification_template_icon_group.xml",
                        "res/layout/notification_template_icon_group.xml",
                        "res/layout-v17/notification_template_lines_media.xml",
                        "res/layout/notification_template_lines_media.xml",
                        "res/layout-v17/notification_template_media.xml",
                        "res/layout/notification_template_media.xml",
                        "res/layout-v17/notification_template_media_custom.xml",
                        "res/layout/notification_template_media_custom.xml",
                        "res/layout/notification_template_part_chronometer.xml",
                        "res/layout/notification_template_part_time.xml",
                        "res/drawable/notification_tile_bg.xml",
                        "res/drawable-hdpi-v4/notify_panel_notification_icon_bg.png",
                        "res/drawable-mdpi-v4/notify_panel_notification_icon_bg.png",
                        "res/drawable-xhdpi-v4/notify_panel_notification_icon_bg.png",
                        "res/layout/prefix_3_suffix.xml",
                        "res/layout/prefix_used_1.xml",
                        "res/layout/prefix_used_2.xml",
                        "resources.arsc",
                        "res/layout/unused1.xml",
                        "res/layout/unused2.xml",
                        "res/drawable/unused9.xml",
                        "res/drawable/unused10.xml",
                        "res/drawable/unused11.xml",
                        "res/menu/unused12.xml",
                        "res/layout/unused13.xml",
                        "res/layout/unused14.xml",
                        "res/layout/used1.xml",
                        "res/layout/used2.xml",
                        "res/layout/used3.xml",
                        "res/layout/used4.xml",
                        "res/layout/used5.xml",
                        "res/layout/used6.xml",
                        "res/layout/used7.xml",
                        "res/layout/used8.xml",
                        "res/drawable/used9.xml",
                        "res/drawable/used10.xml",
                        "res/drawable/used11.xml",
                        "res/drawable/used12.xml",
                        "res/menu/used13.xml",
                        "res/layout/used14.xml",
                        "res/drawable/used15.xml",
                        "res/layout/used16.xml",
                        "res/layout/used17.xml",
                        "res/layout/used18.xml",
                        "res/layout/used19.xml",
                        "res/layout/used20.xml",
                        "res/layout/used21.xml");

        List<String> expectedStrippedApkContents =
                ImmutableList.of(
                        "AndroidManifest.xml",
                        "classes.dex",
                        "res/layout/l_used_a.xml",
                        "res/layout/l_used_b2.xml",
                        "res/layout/l_used_c.xml",
                        "res/layout/prefix_3_suffix.xml",
                        "res/layout/prefix_used_1.xml",
                        "res/layout/prefix_used_2.xml",
                        "resources.arsc",
                        "res/layout/used1.xml",
                        "res/layout/used2.xml",
                        "res/layout/used3.xml",
                        "res/layout/used4.xml",
                        "res/layout/used5.xml",
                        "res/layout/used6.xml",
                        "res/layout/used7.xml",
                        "res/layout/used8.xml",
                        "res/drawable/used9.xml",
                        "res/drawable/used10.xml",
                        "res/drawable/used11.xml",
                        "res/drawable/used12.xml",
                        "res/menu/used13.xml",
                        "res/layout/used14.xml",
                        "res/drawable/used15.xml",
                        "res/layout/used16.xml",
                        "res/layout/used17.xml",
                        "res/layout/used18.xml",
                        "res/layout/used19.xml",
                        "res/layout/used20.xml",
                        "res/layout/used21.xml");
        if (REPLACE_DELETED_WITH_EMPTY) {
            // If replacing deleted files with empty files, the file list will include
            // the "unused" files too, though they will be much smaller. This is checked
            // later on in the test.
            expectedStrippedApkContents =
                    ImmutableList.of(
                            "AndroidManifest.xml",
                            "classes.dex",
                            "res/drawable/force_remove.xml",
                            "res/raw/keep.xml",
                            "res/layout/l_used_a.xml",
                            "res/layout/l_used_b2.xml",
                            "res/layout/l_used_c.xml",
                            "res/layout/lib_unused.xml",
                            "res/layout-v17/notification_action.xml",
                            "res/layout-v21/notification_action.xml",
                            "res/layout/notification_action.xml",
                            "res/drawable-v21/notification_action_background.xml",
                            "res/layout-v17/notification_action_tombstone.xml",
                            "res/layout-v21/notification_action_tombstone.xml",
                            "res/layout/notification_action_tombstone.xml",
                            "res/drawable/notification_bg.xml",
                            "res/drawable/notification_bg_low.xml",
                            "res/drawable-hdpi-v4/notification_bg_low_normal.9.png",
                            "res/drawable-mdpi-v4/notification_bg_low_normal.9.png",
                            "res/drawable-xhdpi-v4/notification_bg_low_normal.9.png",
                            "res/drawable-hdpi-v4/notification_bg_low_pressed.9.png",
                            "res/drawable-mdpi-v4/notification_bg_low_pressed.9.png",
                            "res/drawable-xhdpi-v4/notification_bg_low_pressed.9.png",
                            "res/drawable-hdpi-v4/notification_bg_normal.9.png",
                            "res/drawable-mdpi-v4/notification_bg_normal.9.png",
                            "res/drawable-xhdpi-v4/notification_bg_normal.9.png",
                            "res/drawable-hdpi-v4/notification_bg_normal_pressed.9.png",
                            "res/drawable-mdpi-v4/notification_bg_normal_pressed.9.png",
                            "res/drawable-xhdpi-v4/notification_bg_normal_pressed.9.png",
                            "res/drawable/notification_icon_background.xml",
                            "res/layout/notification_media_action.xml",
                            "res/layout/notification_media_cancel_action.xml",
                            "res/layout-v17/notification_template_big_media.xml",
                            "res/layout/notification_template_big_media.xml",
                            "res/layout-v17/notification_template_big_media_custom.xml",
                            "res/layout/notification_template_big_media_custom.xml",
                            "res/layout-v17/notification_template_big_media_narrow.xml",
                            "res/layout/notification_template_big_media_narrow.xml",
                            "res/layout-v17/notification_template_big_media_narrow_custom.xml",
                            "res/layout/notification_template_big_media_narrow_custom.xml",
                            "res/layout-v16/notification_template_custom_big.xml",
                            "res/layout-v17/notification_template_custom_big.xml",
                            "res/layout-v21/notification_template_custom_big.xml",
                            "res/layout-v21/notification_template_icon_group.xml",
                            "res/layout/notification_template_icon_group.xml",
                            "res/layout-v17/notification_template_lines_media.xml",
                            "res/layout/notification_template_lines_media.xml",
                            "res/layout-v17/notification_template_media.xml",
                            "res/layout/notification_template_media.xml",
                            "res/layout-v17/notification_template_media_custom.xml",
                            "res/layout/notification_template_media_custom.xml",
                            "res/layout/notification_template_part_chronometer.xml",
                            "res/layout/notification_template_part_time.xml",
                            "res/drawable/notification_tile_bg.xml",
                            "res/drawable-hdpi-v4/notify_panel_notification_icon_bg.png",
                            "res/drawable-mdpi-v4/notify_panel_notification_icon_bg.png",
                            "res/drawable-xhdpi-v4/notify_panel_notification_icon_bg.png",
                            "res/layout/prefix_3_suffix.xml",
                            "res/layout/prefix_used_1.xml",
                            "res/layout/prefix_used_2.xml",
                            "resources.arsc",
                            "res/layout/unused1.xml",
                            "res/layout/unused2.xml",
                            "res/drawable/unused9.xml",
                            "res/drawable/unused10.xml",
                            "res/drawable/unused11.xml",
                            "res/menu/unused12.xml",
                            "res/layout/unused13.xml",
                            "res/layout/unused14.xml",
                            "res/layout/used1.xml",
                            "res/layout/used2.xml",
                            "res/layout/used3.xml",
                            "res/layout/used4.xml",
                            "res/layout/used5.xml",
                            "res/layout/used6.xml",
                            "res/layout/used7.xml",
                            "res/layout/used8.xml",
                            "res/drawable/used9.xml",
                            "res/drawable/used10.xml",
                            "res/drawable/used11.xml",
                            "res/drawable/used12.xml",
                            "res/menu/used13.xml",
                            "res/layout/used14.xml",
                            "res/drawable/used15.xml",
                            "res/layout/used16.xml",
                            "res/layout/used17.xml",
                            "res/layout/used18.xml",
                            "res/layout/used19.xml",
                            "res/layout/used20.xml",
                            "res/layout/used21.xml");
        }

        // Should not have any unused resources in the compressed list
        if (!REPLACE_DELETED_WITH_EMPTY) {
            assertThat(Joiner.on('\n').join(expectedStrippedApkContents)).doesNotContain("unused");
        }
        // Should have *all* the used resources, currently 1-21
        for (int i = 1; i <= 21; i++) {
            String name = "/used" + i + ".";
            assertTrue(
                    "Missing used" + i + " in " + expectedStrippedApkContents,
                    expectedStrippedApkContents.stream().anyMatch((it) -> it.contains(name)));
        }

        // Check that the uncompressed resources (.ap_) for the release target have everything
        // we expect
        List<String> expectedUncompressed = new ArrayList<>(expectedUnstrippedApk);
        expectedUncompressed.remove("classes.dex");
        useIntermediateResourceTableName(expectedUncompressed);
        assertThat(dumpZipContents(uncompressed))
                .named("uncompressed")
                .containsExactlyElementsIn(expectedUncompressed)
                .inOrder();

        // The debug target should have everything there in the APK
        assertThat(dumpZipContents(apkDebug.getFile()))
                .containsExactlyElementsIn(expectedUnstrippedApk)
                .inOrder();
        assertThat(dumpZipContents(apkProguardOnly.getFile()))
                .containsExactlyElementsIn(expectedUnstrippedApk)
                .inOrder();

        // Make sure force_remove was replaced with a small file if replacing rather than removing
        if (REPLACE_DELETED_WITH_EMPTY) {
            assertThatZip(compressed)
                    .containsFileWithContent(
                            "res/drawable/force_remove.xml", getIntermediateCompressedXml());
        }

        // Check the compressed .ap_:
        List<String> actualCompressed = dumpZipContents(compressed);
        List<String> expectedCompressed = new ArrayList<>(expectedStrippedApkContents);
        expectedCompressed.remove("classes.dex");
        useIntermediateResourceTableName(expectedCompressed);
        assertThat(actualCompressed).containsExactlyElementsIn(expectedCompressed).inOrder();
        if (!REPLACE_DELETED_WITH_EMPTY) {
            assertThat(Joiner.on('\n').join(expectedCompressed)).doesNotContain("unused");
        }
        assertThat(dumpZipContents(apkRelease.getFile()))
                .named("strippedApkContents")
                .containsExactlyElementsIn(expectedStrippedApkContents)
                .inOrder();

        // Bundle handles splits anyway.
        if (apkPipeline == ApkPipeline.NO_BUNDLE) {
            // Check splits -- just sample one of them
            //noinspection SpellCheckingInspection
            compressed =
                    project.file(
                            "abisplits/build/intermediates/res_stripped/release/resources-arm64-v8a-release-stripped.ap_");
            //noinspection SpellCheckingInspection
            uncompressed =
                    project.file(
                            "abisplits/build/intermediates/processed_res/release/processReleaseResources/out/resources-arm64-v8aRelease.ap_");
            assertTrue(compressed.toString() + " is not a file", compressed.isFile());
            assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());
            //noinspection SpellCheckingInspection
            if (REPLACE_DELETED_WITH_EMPTY) {
                assertThat(dumpZipContents(compressed))
                        .containsExactly(
                                "AndroidManifest.xml",
                                "resources.arsc",
                                "res/layout/unused.xml",
                                "res/layout/used.xml")
                        .inOrder();
            } else {
                assertThat(dumpZipContents(compressed))
                        .containsExactly(
                                "AndroidManifest.xml", "resources.arsc", "res/layout/used.xml")
                        .inOrder();
            }

            //noinspection SpellCheckingInspection
            assertThat(dumpZipContents(uncompressed))
                    .containsExactly(
                            "AndroidManifest.xml",
                            "resources.arsc",
                            "res/layout/unused.xml",
                            "res/layout/used.xml")
                    .inOrder();
        }
        // Check WebView string handling (android_res strings etc)

        //noinspection SpellCheckingInspection
        uncompressed = getUncompressed(project.getSubproject("webview"));
        //noinspection SpellCheckingInspection
        compressed = getCompressed(project.getSubproject("webview"));
        assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());
        assertTrue(compressed.toString() + " is not a file", compressed.isFile());

        //noinspection SpellCheckingInspection
        assertThat(dumpZipContents(uncompressed))
                .containsExactly(
                        "AndroidManifest.xml",
                        "res/xml/my_xml.xml",
                        getIntermediateResourceTableName(),
                        "res/raw/unknown",
                        "res/raw/unused_icon.png",
                        "res/raw/unused_index.html",
                        "res/drawable/used1.xml",
                        "res/raw/used_icon.png",
                        "res/raw/used_icon2.png",
                        "res/raw/used_index.html",
                        "res/raw/used_index2.html",
                        "res/raw/used_index3.html",
                        "res/layout/used_layout1.xml",
                        "res/layout/used_layout2.xml",
                        "res/layout/used_layout3.xml",
                        "res/raw/used_script.js",
                        "res/raw/used_styles.css",
                        "res/layout/webview.xml");

        //noinspection SpellCheckingInspection
        if (REPLACE_DELETED_WITH_EMPTY) {
            assertThat(dumpZipContents(compressed))
                    .containsExactly(
                            "AndroidManifest.xml",
                            "res/xml/my_xml.xml",
                            getIntermediateResourceTableName(),
                            "res/raw/unknown",
                            "res/raw/unused_icon.png",
                            "res/raw/unused_index.html",
                            "res/drawable/used1.xml",
                            "res/raw/used_icon.png",
                            "res/raw/used_icon2.png",
                            "res/raw/used_index.html",
                            "res/raw/used_index2.html",
                            "res/raw/used_index3.html",
                            "res/layout/used_layout1.xml",
                            "res/layout/used_layout2.xml",
                            "res/layout/used_layout3.xml",
                            "res/raw/used_script.js",
                            "res/raw/used_styles.css",
                            "res/layout/webview.xml")
                    .inOrder();
        } else {
            assertThat(dumpZipContents(compressed))
                    .containsExactly(
                            "AndroidManifest.xml",
                            getIntermediateResourceTableName(),
                            "res/raw/unknown",
                            "res/drawable/used1.xml",
                            "res/raw/used_icon.png",
                            "res/raw/used_icon2.png",
                            "res/raw/used_index.html",
                            "res/raw/used_index2.html",
                            "res/raw/used_index3.html",
                            "res/layout/used_layout1.xml",
                            "res/layout/used_layout2.xml",
                            "res/layout/used_layout3.xml",
                            "res/raw/used_script.js",
                            "res/raw/used_styles.css",
                            "res/layout/webview.xml")
                    .inOrder();
        }

        // Check stored vs deflated state:
        // This is the state of the original source _ap file:
        assertThat(dumpZipContents(uncompressed, true))
                .containsExactly(
                        getIntermediateResourceTableNameAndMethod(),
                        "deflated  AndroidManifest.xml",
                        "deflated  res/xml/my_xml.xml",
                        "deflated  res/raw/unknown",
                        "  stored  res/raw/unused_icon.png",
                        "deflated  res/raw/unused_index.html",
                        "deflated  res/drawable/used1.xml",
                        "  stored  res/raw/used_icon.png",
                        "  stored  res/raw/used_icon2.png",
                        "deflated  res/raw/used_index.html",
                        "deflated  res/raw/used_index2.html",
                        "deflated  res/raw/used_index3.html",
                        "deflated  res/layout/used_layout1.xml",
                        "deflated  res/layout/used_layout2.xml",
                        "deflated  res/layout/used_layout3.xml",
                        "deflated  res/raw/used_script.js",
                        "deflated  res/raw/used_styles.css",
                        "deflated  res/layout/webview.xml");

        // This is the state of the rewritten ap_ file: the zip states should match

        if (REPLACE_DELETED_WITH_EMPTY) {
            assertThat(dumpZipContents(compressed, true))
                    .containsExactly(
                            getIntermediateResourceTableNameAndMethod(),
                            "deflated  AndroidManifest.xml",
                            "deflated  res/xml/my_xml.xml",
                            "deflated  res/raw/unknown",
                            "  stored  res/raw/unused_icon.png",
                            "deflated  res/raw/unused_index.html",
                            "deflated  res/drawable/used1.xml",
                            "  stored  res/raw/used_icon.png",
                            "  stored  res/raw/used_icon2.png",
                            "deflated  res/raw/used_index.html",
                            "deflated  res/raw/used_index2.html",
                            "deflated  res/raw/used_index3.html",
                            "deflated  res/layout/used_layout1.xml",
                            "deflated  res/layout/used_layout2.xml",
                            "deflated  res/layout/used_layout3.xml",
                            "deflated  res/raw/used_script.js",
                            "deflated  res/raw/used_styles.css",
                            "deflated  res/layout/webview.xml");
        } else {
            assertThat(dumpZipContents(compressed, true))
                    .containsExactly(
                            getIntermediateResourceTableNameAndMethod(),
                            "deflated  AndroidManifest.xml",
                            "deflated  res/raw/unknown",
                            "deflated  res/drawable/used1.xml",
                            "  stored  res/raw/used_icon.png",
                            "  stored  res/raw/used_icon2.png",
                            "deflated  res/raw/used_index.html",
                            "deflated  res/raw/used_index2.html",
                            "deflated  res/raw/used_index3.html",
                            "deflated  res/layout/used_layout1.xml",
                            "deflated  res/layout/used_layout2.xml",
                            "deflated  res/layout/used_layout3.xml",
                            "deflated  res/raw/used_script.js",
                            "deflated  res/raw/used_styles.css",
                            "deflated  res/layout/webview.xml");
        }

        // Make sure the (remaining) binary contents of the files in the compressed APK are
        // identical to the ones in uncompressed:
        FileInputStream fis1 = new FileInputStream(compressed);
        JarInputStream zis1 = new JarInputStream(fis1);
        FileInputStream fis2 = new FileInputStream(uncompressed);
        JarInputStream zis2 = new JarInputStream(fis2);

        ZipEntry entry1 = zis1.getNextEntry();
        ZipEntry entry2 = zis2.getNextEntry();
        while (entry1 != null) {
            String name1 = entry1.getName();
            String name2 = entry2.getName();
            while (!name1.equals(name2)) {
                // uncompressed should contain a superset of all the names in compressed
                entry2 = zis2.getNextJarEntry();
                name2 = entry2.getName();
            }
            assertEquals(name1, name2);
            if (!entry1.isDirectory()) {
                assertEquals(name1, entry1.getMethod(), entry2.getMethod());

                byte[] bytes1 = ByteStreams.toByteArray(zis1);
                byte[] bytes2 = ByteStreams.toByteArray(zis2);

                if (REPLACE_DELETED_WITH_EMPTY) {
                    switch (name1) {
                        case "res/xml/my_xml.xml":
                            assertThat(bytes1)
                                    .named(name1)
                                    .isEqualTo(getIntermediateCompressedXml());
                            break;
                        case "res/raw/unused_icon.png":
                            assertThat(bytes1)
                                    .named(name1)
                                    .isEqualTo(ResourceUsageAnalyzer.TINY_PNG);
                            break;
                        case "res/raw/unused_index.html":
                            assertThat(bytes1).named(name1).isEmpty();
                            break;
                        default:
                            assertThat(bytes1).named(name1).isEqualTo(bytes2);
                            break;
                    }
                } else {
                    assertTrue(name1, Arrays.equals(bytes1, bytes2));
                }
            } else {
                assertTrue(entry2.isDirectory());
            }
            entry1 = zis1.getNextEntry();
            entry2 = zis2.getNextEntry();
        }

        zis1.close();
        zis2.close();

        //noinspection SpellCheckingInspection
        uncompressed = getUncompressed(project.getSubproject("keep"));
        //noinspection SpellCheckingInspection
        compressed = getCompressed(project.getSubproject("keep"));
        assertTrue(uncompressed.toString() + " is not a file", uncompressed.isFile());
        assertTrue(compressed.toString() + " is not a file", compressed.isFile());

        //noinspection SpellCheckingInspection
        assertThat(dumpZipContents(uncompressed))
                .containsExactly(
                        "AndroidManifest.xml",
                        "res/raw/keep.xml",
                        getIntermediateResourceTableName(),
                        "res/layout/unused1.xml",
                        "res/layout/unused2.xml",
                        "res/layout/used1.xml")
                .inOrder();

        //noinspection SpellCheckingInspection
        if (REPLACE_DELETED_WITH_EMPTY) {
            assertThat(dumpZipContents(compressed))
                    .containsExactly(
                            "AndroidManifest.xml",
                            "res/raw/keep.xml",
                            getIntermediateResourceTableName(),
                            "res/layout/unused1.xml",
                            "res/layout/unused2.xml",
                            "res/layout/used1.xml")
                    .inOrder();
        } else {
            assertThat(dumpZipContents(compressed))
                    .containsExactly(
                            "AndroidManifest.xml",
                            getIntermediateResourceTableName(),
                            "res/layout/used1.xml")
                    .inOrder();
        }
    }

    private static List<String> getZipPaths(File zipFile, boolean includeMethod)
            throws IOException {
        List<String> lines = Lists.newArrayList();

        Closer closer = Closer.create();

        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName();
                if (includeMethod) {
                    String method;
                    switch (entry.getMethod()) {
                        case ZipEntry.STORED:
                            method = "  stored";
                            break;
                        case ZipEntry.DEFLATED:
                            method = "deflated";
                            break;
                        default:
                            method = " unknown";
                            break;
                    }
                    path = method + "  " + path;
                }
                lines.add(path);
            }
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        return lines;
    }

    private static List<String> dumpZipContents(File zipFile) throws IOException {
        return dumpZipContents(zipFile, false);
    }

    private static List<String> dumpZipContents(Path zipFile) throws IOException {
        return dumpZipContents(zipFile.toFile(), false);
    }

    private static List<String> dumpZipContents(File zipFile, final boolean includeMethod)
            throws IOException {
        List<String> lines = getZipPaths(zipFile, includeMethod);

        // Remove META-INF statements
        lines.removeIf(s -> s.startsWith("META-INF/"));

        // Remove resource files generated by the Bundle Tool.
        lines.removeIf(s -> s.matches("res/xml/splits(\\d+).xml"));

        // Sort by base name (and numeric sort such that unused10 comes after unused9)
        final Pattern pattern = Pattern.compile("(.*[^\\d])(\\d+)(\\..+)?");
        lines.sort(
                (line1, line2) -> {
                    String name1 = line1.substring(line1.lastIndexOf('/') + 1);
                    String name2 = line2.substring(line2.lastIndexOf('/') + 1);
                    int delta = name1.compareTo(name2);
                    if (delta != 0) {
                        // Try to do numeric sort
                        Matcher match1 = pattern.matcher(name1);
                        if (match1.matches()) {
                            Matcher match2 = pattern.matcher(name2);
                            //noinspection ConstantConditions
                            if (match2.matches() && match1.group(1).equals(match2.group(1))) {
                                //noinspection ConstantConditions
                                int num1 = Integer.parseInt(match1.group(2));
                                //noinspection ConstantConditions
                                int num2 = Integer.parseInt(match2.group(2));
                                if (num1 != num2) {
                                    return num1 - num2;
                                }
                            }
                        }
                        return delta;
                    }

                    if (includeMethod) {
                        line1 = line1.substring(10);
                        line2 = line2.substring(10);
                    }
                    return line1.compareTo(line2);
                });

        return ImmutableList.copyOf(lines);
    }
}
