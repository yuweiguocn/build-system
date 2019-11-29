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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilder;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for instant apps modules with pure ABI config splits */
public class FeatureAndAbiPureSplitsTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder().fromTestProject("projectWithFeaturesAndSplitABIs").create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void buildAndCheckModel() throws Exception {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        // Build all the things.
        sProject.executor().run("clean", "assembleDebug");

        checkModel(sProject.model());
    }

    private static void checkModel(ModelBuilder model) throws IOException {
        Map<String, AndroidProject> projectModels =
                model.ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModelMap();
        AndroidProject instantAppProject = projectModels.get(":bundle");
        assertThat(instantAppProject).isNotNull();
        assertThat(instantAppProject.getVariants()).hasSize(2);
        System.out.println(instantAppProject.getVariants());

        Map<String, ProjectBuildOutput> projectOutputModels =
                model.fetchMulti(ProjectBuildOutput.class);
        assertThat(projectOutputModels).hasSize(4);
        assertThat(projectOutputModels).doesNotContainKey(":bundle");

        Map<String, InstantAppProjectBuildOutput> models =
                model.fetchMulti(InstantAppProjectBuildOutput.class);
        assertThat(models).hasSize(1);

        InstantAppProjectBuildOutput instantAppModule = models.get(":bundle");
        assertThat(instantAppModule).isNotNull();
        assertThat(instantAppModule.getInstantAppVariantsBuildOutput()).hasSize(1);
        InstantAppVariantBuildOutput debug =
                getDebugVariant(instantAppModule.getInstantAppVariantsBuildOutput());
        assertThat(debug.getApplicationId()).isEqualTo("com.example.android.multiproject");
        assertThat(debug.getOutput().getOutputFile().getName()).isEqualTo("bundle-debug.zip");
        assertThat(debug.getFeatureOutputs()).hasSize(7);

        List<String> expectedFileNames =
                ImmutableList.of(
                        "baseFeature-debug.apk",
                        "baseFeature-x86-debug.apk",
                        "baseFeature-armeabi-v7a-debug.apk",
                        "feature_a-debug.apk",
                        "feature_a-x86-debug.apk",
                        "feature_a-armeabi-v7a-debug.apk",
                        "feature_a-hdpi-debug.apk");
        List<String> foundFileNames = new ArrayList<>();
        debug.getFeatureOutputs()
                .forEach(outputFile -> foundFileNames.add(outputFile.getOutputFile().getName()));
        assertThat(foundFileNames).containsExactlyElementsIn(expectedFileNames);

        List<String> expectedSplitNames =
                ImmutableList.of(
                        "config.x86",
                        "config.armeabi_v7a",
                        "feature_a.config.x86",
                        "feature_a.config.armeabi_v7a",
                        "feature_a.config.hdpi");
        List<String> foundSplitNames = new ArrayList<>();
        debug.getFeatureOutputs()
                .forEach(
                        outputFile -> {
                            List<String> manifestContent =
                                    ApkSubject.getManifestContent(
                                            outputFile.getOutputFile().toPath());
                            String applicationId = "";
                            String configForSplit = "";
                            String targetABI = "";
                            String split = "";
                            for (String line : manifestContent) {
                                if (line.contains("package=")) {
                                    applicationId = getQuotedValue(line);
                                }
                                if (line.contains("configForSplit=")) {
                                    configForSplit = getQuotedValue(line);
                                }
                                if (line.contains("targetABI=")) {
                                    targetABI = getQuotedValue(line);
                                }
                                if (line.contains("split=")) {
                                    split = getQuotedValue(line);
                                }
                            }
                            assertThat(applicationId).isEqualTo("com.example.android.multiproject");
                            if (!Strings.isNullOrEmpty(split) && split.contains("config")) {
                                String splitName =
                                        Strings.isNullOrEmpty(configForSplit)
                                                ? "config."
                                                : configForSplit + ".config.";
                                splitName +=
                                        Strings.isNullOrEmpty(targetABI)
                                                ? "hdpi"
                                                : targetABI.replace("-", "_");
                                assertThat(splitName).isEqualTo(split);
                                foundSplitNames.add(splitName);
                            }
                        });
        assertThat(foundSplitNames).containsExactlyElementsIn(expectedSplitNames);
    }

    @Test
    public void buildSigned() throws Exception {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        // Add signing configuration to the release variant.
        String signingConfig =
                "\n"
                        + "android {\n"
                        + "    signingConfigs {\n"
                        + "        myConfig {\n"
                        + "            storeFile file(\"../debug.keystore\")\n"
                        + "            storePassword \"android\"\n"
                        + "            keyAlias \"androiddebugkey\"\n"
                        + "            keyPassword \"android\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            signingConfig signingConfigs.myConfig\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n";
        TestFileUtils.appendToFile(
                sProject.getSubproject(":baseFeature").getBuildFile(), signingConfig);
        TestFileUtils.appendToFile(
                sProject.getSubproject(":feature_a").getBuildFile(), signingConfig);

        // Build the instantapp.
        sProject.executor().run("clean", ":bundle:assembleRelease");

        Map<String, InstantAppProjectBuildOutput> models =
                sProject.model().fetchMulti(InstantAppProjectBuildOutput.class);
        assertThat(models).hasSize(1);

        InstantAppProjectBuildOutput instantAppModule = models.get(":bundle");
        assertThat(instantAppModule).isNotNull();
        assertThat(instantAppModule.getInstantAppVariantsBuildOutput()).hasSize(1);
        InstantAppVariantBuildOutput release =
                getReleaseVariant(instantAppModule.getInstantAppVariantsBuildOutput());
        assertThat(release.getApplicationId()).isEqualTo("com.example.android.multiproject");
        assertThat(release.getOutput().getOutputFile().getName()).isEqualTo("bundle-release.zip");
        assertThat(release.getFeatureOutputs()).hasSize(7);

        List<String> expectedFileNames =
                ImmutableList.of(
                        "baseFeature-release.apk",
                        "baseFeature-x86-release.apk",
                        "baseFeature-armeabi-v7a-release.apk",
                        "feature_a-release.apk",
                        "feature_a-x86-release.apk",
                        "feature_a-armeabi-v7a-release.apk",
                        "feature_a-hdpi-release.apk");
        List<String> foundFileNames = new ArrayList<>();
        release.getFeatureOutputs()
                .forEach(outputFile -> foundFileNames.add(outputFile.getOutputFile().getName()));
        assertThat(foundFileNames).containsExactlyElementsIn(expectedFileNames);
    }

    private static String getQuotedValue(String line) {
        int afterQuote = line.indexOf('"') + 1;
        return line.substring(afterQuote, line.indexOf('"', afterQuote));
    }

    private static InstantAppVariantBuildOutput getDebugVariant(
            Collection<InstantAppVariantBuildOutput> outputs) {
        return outputs.stream()
                .filter(output -> output.getName().equals("debug"))
                .findFirst()
                .get();
    }

    private static InstantAppVariantBuildOutput getReleaseVariant(
            Collection<InstantAppVariantBuildOutput> outputs) {
        return outputs.stream()
                .filter(output -> output.getName().equals("release"))
                .findFirst()
                .get();
    }

    @Test
    public void testSplitConfigurationWarning() throws Exception {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        TestFileUtils.searchAndReplace(
                sProject.getSubproject(":feature_a").getBuildFile(),
                "generatePureSplits true",
                "generatePureSplits false");

        sProject.executor().run("clean", "assembleDebug");

        ModelBuilder modelBuilder = sProject.model().ignoreSyncIssues();

        AndroidProject model =
                modelBuilder.fetchAndroidProjects().getOnlyModelMap().get(":feature_a");
        List<SyncIssue> syncIssues =
                model.getSyncIssues()
                        .stream()
                        .filter(issue -> issue.getType() != SyncIssue.TYPE_PLUGIN_OBSOLETE)
                        .collect(Collectors.toList());
        assertThat(syncIssues).named("Sync Issues").hasSize(1);
        assertThat(Iterables.getOnlyElement(syncIssues).getMessage())
                .contains(
                        "Configuration APKs targeting different device configurations are "
                                + "automatically built when splits are enabled for a feature module.");

        checkModel(modelBuilder);
    }
}
