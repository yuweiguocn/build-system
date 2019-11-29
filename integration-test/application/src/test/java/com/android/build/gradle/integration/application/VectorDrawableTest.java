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

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertWithMessage;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.searchRegexAndReplace;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the PNG generation feature.
 *
 * The "v4" is added by resource merger to all dpi qualifiers, to make it clear dpi qualifiers are
 * supported since API 4.
 */
public class VectorDrawableTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("vectorDrawables")
            .create();

    @BeforeClass
    public static void checkBuildTools() {
        AssumeUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void vectorFileIsMovedAndPngsAreGenerated() throws Exception {
        project.executor().run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).containsResource("drawable-anydpi-v24/gradient.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-hdpi-v22/gradient.png");
        assertThat(apk).containsResource("drawable-v22/no_need.xml");
        assertThat(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-xhdpi-v22/gradient.png");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v22/gradient.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThat(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-nodpi-v22/gradient.xml");
        assertThat(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-v22/gradient.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v22/gradient.xml");
        assertThat(apk).doesNotContainResource("drawable/heart.xml");
        assertThat(apk).doesNotContainResource("drawable/gradient.xml");

        // Check HDPI. Test project contains the hdpi png, it should be used instead of the
        // generated one.
        File generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-hdpi/special_heart.png");
        assertThat(generatedPng).doesNotExist();
        File pngToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/drawable-hdpi_special_heart.png.flat");
        assertThat(pngToUse).exists();

        // Check XHDPI.
        generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-xhdpi/special_heart.png");
        pngToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/drawable-xhdpi_special_heart.png.flat");
        assertThat(pngToUse).exists();
        assertThat(generatedPng).exists();


        // Check interactions with other qualifiers.
        apk = project.getApk("debug");
        assertThat(apk).containsResource("drawable-anydpi-v21/modern_heart.xml");
        assertThat(apk).containsResource("drawable-fr-anydpi-v21/french_heart.xml");
        assertThat(apk).containsResource("drawable-fr-anydpi-v21/french_heart.xml");
        assertThat(apk).containsResource("drawable-fr-hdpi-v4/french_heart.png");
        assertThat(apk).containsResource("drawable-hdpi-v16/modern_heart.png");
        assertThat(apk).doesNotContainResource("drawable-fr-hdpi-v21/french_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-fr-xhdpi-v21/french_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-fr/french_heart.png");
        assertThat(apk).doesNotContainResource("drawable-fr/french_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v16/modern_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/french_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/modern_heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/modern_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/french_heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/modern_heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi/french_heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi/modern_heart.png");
        assertThat(apk).doesNotContainResource("drawable-v16/modern_heart.png");
        assertThat(apk).doesNotContainResource("drawable-v16/modern_heart.xml");
    }

    @Test
    public void incrementalBuildAddXml() throws Exception {
        project.executor().run("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);

        // Sanity check:
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/heart_copy.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/heart_copy.png");

        File intermediatesXml =
                project.file(
                        "build/intermediates/res/merged/debug/"
                                + "drawable-anydpi-v21_heart.arsc.flat");

        File intermediatesHdpiPng =
                project.file("build/intermediates/res/merged/debug/drawable-hdpi_heart.png.flat");

        long xmlTimestamp = intermediatesXml.lastModified();
        long pngTimestamp = intermediatesHdpiPng.lastModified();

        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");
        File heartXmlCopy = new File(project.getTestDir(), "src/main/res/drawable/heart_copy.xml");
        Files.copy(heartXml, heartXmlCopy);

        TestUtils.waitForFileSystemTick();
        project.executor().run("assembleDebug");
        assertThat(intermediatesXml).wasModifiedAt(xmlTimestamp);
        assertThat(intermediatesHdpiPng).wasModifiedAt(pngTimestamp);
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-anydpi-v21/heart_copy.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart_copy.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/heart_copy.png");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart_copy.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart_copy.xml");
        assertThat(apk).doesNotContainResource("drawable/heart_copy.xml");
    }

    @Test
    public void incrementalBuildDeleteXml() throws Exception {
        project.executor().run("assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable_icon.png.flat");

        long timestamp = intermediatesIconPng.lastModified();

        FileUtils.delete(new File(project.getTestDir(), "src/main/res/drawable/heart.xml"));

        TestUtils.waitForFileSystemTick();
        project.executor().run("assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi/heart.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi/heart.png");
        assertThat(apk).doesNotContainResource("drawable/heart.xml");

        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void incrementalBuildDeletePng() throws Exception {
        project.executor().run("assembleDebug");
        File intermediatesXml =
                project.file(
                        "build/intermediates/res/merged/debug/"
                                + "drawable-anydpi-v21_heart.arsc.flat");

        long xmlTimestamp = intermediatesXml.lastModified();

        File generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-hdpi/special_heart.png");
        File originalPng = new File(
                project.getTestDir(),
                "src/main/res/drawable-hdpi/special_heart.png");
        File pngToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/"
                                + "drawable-hdpi_special_heart.png.flat");

        assertThat(generatedPng).doesNotExist();
        assertThat(pngToUse).exists();

        FileUtils.delete(originalPng);

        TestUtils.waitForFileSystemTick();
        project.executor().run("assembleDebug");
        assertThat(pngToUse).exists();

        assertThat(intermediatesXml).wasModifiedAt(xmlTimestamp);
    }

    @Test
    public void incrementalBuildAddPng() throws Exception {
        project.executor().run("assembleDebug");
        File intermediatesXml =
                project.file(
                        "build/intermediates/res/merged/debug/"
                                + "drawable-anydpi-v21_heart.xml.flat");

        assertThat(intermediatesXml).exists();
        long xmlTimestamp = intermediatesXml.lastModified();

        File generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-xhdpi/special_heart.png");
        File pngToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/"
                                + "drawable-xhdpi_special_heart.png.flat");
        assertThat(pngToUse).exists();
        assertThat(generatedPng).exists();


        // Create a PNG file for XHDPI. It should be used instead of the generated one.
        File hdpiPng = new File(project.getTestDir(),
                "src/main/res/drawable-hdpi/special_heart.png");
        File xhdpiPng = new File(project.getTestDir(),
                "src/main/res/drawable-xhdpi/special_heart.png");
        Files.createParentDirs(xhdpiPng);
        Files.copy(hdpiPng, xhdpiPng);

        TestUtils.waitForFileSystemTick();
        project.executor().run("assembleDebug");
        assertThat(pngToUse).exists();
        assertThat(generatedPng).exists();

        assertThat(intermediatesXml).wasModifiedAt(xmlTimestamp);
    }

    @Test
    public void incrementalBuildModifyXml() throws Exception {
        project.executor().run("assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable_icon.png.flat");

        long timestamp = intermediatesIconPng.lastModified();

        File heartPngToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/drawable-hdpi_heart.png.flat");
        File iconPngToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/drawable_icon.png.flat");

        String oldHashCode = FileUtils.sha1(heartPngToUse);
        long heartPngModified = heartPngToUse.lastModified();
        long iconPngModified = iconPngToUse.lastModified();

        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");
        String content = Files.toString(heartXml, UTF_8);
        // Change the heart to blue.
        Files.asCharSink(heartXml, UTF_8).write(content.replace("ff0000", "0000ff"));

        TestUtils.waitForFileSystemTick();
        project.executor().run("assembleDebug");

        assertThat(iconPngToUse.lastModified()).isEqualTo(iconPngModified);
        assertThat(heartPngToUse.lastModified()).isNotEqualTo(heartPngModified);
        assertWithMessage("XML file change not reflected in PNG.")
                .that(FileUtils.sha1(heartPngToUse))
                .isNotEqualTo(oldHashCode);

        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void incrementalBuildReplaceVectorDrawableWithBitmapAlias() throws Exception {
        project.executor().run("assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable_icon.png.flat");

        long timestamp = intermediatesIconPng.lastModified();

        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");
        Files.asCharSink(heartXml, UTF_8)
                .write(
                        "<bitmap xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                                + "android:src=\"@drawable/icon\" />");

        TestUtils.waitForFileSystemTick();
        project.executor().run("assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable/heart.xml");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi/heart.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi/heart.png");

        File heartXmlToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/drawable_heart.xml.flat");
        assertThat(heartXmlToUse).exists();
        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void incrementalBuildReplaceBitmapAliasWithVectorDrawable() throws Exception {
        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");

        String vectorDrawable = Files.toString(heartXml, UTF_8);

        Files.asCharSink(heartXml, UTF_8)
                .write(
                        "<bitmap xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                                + "android:src=\"@drawable/icon\" />");

        project.executor().run("clean", "assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable_icon.png.flat");

        long timestamp = intermediatesIconPng.lastModified();

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable/heart.xml");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi/heart.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi/heart.png");

        File heartXmlToUse =
                new File(
                        project.getTestDir(),
                        "build/intermediates/res/merged/debug/drawable_heart.xml.flat");
        assertThat(heartXmlToUse).exists();

        Files.asCharSink(heartXml, UTF_8).write(vectorDrawable);
        TestUtils.waitForFileSystemTick();
        project.executor().run("assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable/heart.xml");

        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void defaultDensitiesWork() throws Exception {
        // Remove the lines that configure generated densities.
        searchRegexAndReplace(
                project.getBuildFile(), "generatedDensities.*" + System.lineSeparator(), "");

        project.executor().run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).containsResource("drawable-xxxhdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-xxhdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-mdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-ldpi-v4/heart.png");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-ldpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable/heart.xml");
    }

    @Test
    public void nothingIsDoneWhenMinSdk21AndAbove() throws Exception {
        searchRegexAndReplace(project.getBuildFile(), "minSdkVersion \\d+", "minSdkVersion 21");
        project.executor().run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);

        assertThat(apk).containsResource("drawable-hdpi-v4/special_heart.png");
        assertThat(apk).containsResource("drawable-v16/modern_heart.xml");
        assertThat(apk).containsResource("drawable-v22/no_need.xml");
        assertThat(apk).containsResource("drawable/heart.xml");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).containsResource("drawable/special_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v16/modern_heart.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v22/no_need.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
    }

    @Test
    public void disablingPngGeneration() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.vectorDrawables.generatedDensities = []");

        project.executor().run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertPngGenerationDisabled(apk);
    }

    @Test
    public void disablingPngGeneration_oldDsl() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.generatedDensities = []");

        project.executor().run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertPngGenerationDisabled(apk);
    }

    @Test
    public void incrementalBuildDisablingPngGeneration() throws Exception {

        project.executor().run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-v22/no_need.xml");
        assertThat(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThat(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable/heart.xml");

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.vectorDrawables.useSupportLibrary = true");

        project.executor().run("assembleDebug");
        assertPngGenerationDisabled(project.getApk(GradleTestProject.ApkType.DEBUG));
    }

    @Test
    public void incrementalBuildChangingDensities() throws Exception {

        project.executor().run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-v22/no_need.xml");
        assertThat(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThat(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable/heart.xml");

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.vectorDrawables.generatedDensities = ['hdpi']");

        project.executor().run("assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable-v22/no_need.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThat(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable/heart.xml");
    }

    @Test
    @SuppressWarnings("ConstantConditions") // If getVariant returns null, the test just fails.
    public void model() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "  flavorDimensions 'foo'\n"
                        + "  productFlavors {\n"
                        + "    pngs\n"
                        + "    vectors { vectorDrawables.useSupportLibrary = true }\n"
                        + "    hdpiOnly { vectorDrawables.generatedDensities = ['hdpi'] }\n"
                        + "  }\n"
                        + "}\n");

        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();

        VectorDrawablesOptions defaultConfigOptions =
                model.getDefaultConfig().getProductFlavor().getVectorDrawables();

        assertThat(defaultConfigOptions.getUseSupportLibrary()).isFalse();
        assertThat(defaultConfigOptions.getGeneratedDensities()).containsExactly("hdpi", "xhdpi");

        VectorDrawablesOptions pngsDebug =
                AndroidProjectUtils.getVariantByName(model, "pngsDebug")
                        .getMergedFlavor()
                        .getVectorDrawables();

        assertThat(pngsDebug.getUseSupportLibrary()).isFalse();
        assertThat(pngsDebug.getGeneratedDensities()).containsExactly("hdpi", "xhdpi");

        VectorDrawablesOptions vectorsDebug =
                AndroidProjectUtils.getVariantByName(model, "vectorsDebug")
                        .getMergedFlavor()
                        .getVectorDrawables();

        assertThat(vectorsDebug.getUseSupportLibrary()).isTrue();

        VectorDrawablesOptions hdpiOnlyDebug =
                AndroidProjectUtils.getVariantByName(model, "hdpiOnlyDebug")
                        .getMergedFlavor()
                        .getVectorDrawables();

        assertThat(hdpiOnlyDebug.getUseSupportLibrary()).isFalse();
        assertThat(hdpiOnlyDebug.getGeneratedDensities()).containsExactly("hdpi");
    }

    @Test
    public void resourceReferences() throws Exception {
        Files.asCharSink(
                        new File(project.getTestDir(), "src/main/res/drawable/heart.xml"),
                        StandardCharsets.UTF_8)
                .write(
                        "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "        android:width=\"24dp\"\n"
                                + "        android:height=\"24dp\"\n"
                                + "        android:viewportWidth=\"24.0\"\n"
                                + "        android:viewportHeight=\"24.0\">\n"
                                + "    <path\n"
                                + "            android:fillColor=\"#FF000000\"\n"
                                + "            android:pathData=\"@string/pathDataAsString\"/>\n"
                                + "</vector>\n");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        // Make sure we print out something useful.
        assertThat(result.getFailureMessage())
                .contains("vector drawable support was added in Android 5.0 (API level 21)");
        assertThat(result.getFailureMessage()).contains("vector-asset-studio.html");
    }

    private static void assertPngGenerationDisabled(Apk apk) throws Exception {
        assertThat(apk).containsResource("drawable/heart.xml");
        assertThat(apk).containsResource("drawable/icon.png");
        assertThat(apk).containsResource("drawable-v22/no_need.xml");
        assertThat(apk).doesNotContainResource("drawable-anydpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThat(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThat(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
    }

}
