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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.sdk.TargetInfo;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link InstantRunSliceSplitApkBuilder}
 */
public class InstantRunSplitApkBuilderTest {

    @Mock Logger logger;
    @Mock Project project;
    @Mock InstantRunBuildContext buildContext;
    @Mock AndroidBuilder androidBuilder;
    @Mock Aapt aapt;
    @Mock FileCollection coreSigningConfig;
    @Mock BuildableArtifact mainResources;
    @Mock FileCollection mainResourcesFiles;
    @Mock FileTree mainResourcesApkFileTree;
    @Mock BuildableArtifact splitApkResources;
    @Mock FileTree coreSigningConfigFileTree;

    @Mock TargetInfo targetInfo;
    @Mock BuildToolInfo buildTools;
    @Mock BuildableArtifact apkList;
    @Mock IAndroidTarget target;

    @Rule public TemporaryFolder outputDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder supportDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder apkListDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder signingConfigDirectory = new TemporaryFolder();

    InstantRunSliceSplitApkBuilder instantRunSliceSplitApkBuilder;
    File instantRunFolder;
    File aaptTempFolder;

    final ApkData apkInfo = ApkData.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 12345);

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(androidBuilder.getTargetInfo()).thenReturn(targetInfo);
        when(targetInfo.getBuildTools()).thenReturn(buildTools);
        when(androidBuilder.getTarget()).thenReturn(target);
        when(target.getPath(IAndroidTarget.ANDROID_JAR)).thenReturn("fake android.jar");
        when(mainResources.get()).thenReturn(mainResourcesFiles);
        when(mainResourcesFiles.getAsFileTree()).thenReturn(mainResourcesApkFileTree);
        when(coreSigningConfig.getAsFileTree()).thenReturn(coreSigningConfigFileTree);
        File signingConfigFile = signingConfigDirectory.newFile("signing-config.json");
        when(coreSigningConfigFileTree.getFiles())
                .thenReturn(new HashSet<>(Arrays.asList(signingConfigFile)));
        when(coreSigningConfigFileTree.getSingleFile()).thenReturn(signingConfigFile);

        File apkListFile = apkListDirectory.newFile("apk-list.json");
        FileUtils.write(
                apkListFile, ExistingBuildElements.persistApkList(ImmutableList.of(apkInfo)));
        when(apkList.iterator()).thenReturn(ImmutableList.of(apkListFile).iterator());

        instantRunFolder = supportDirectory.newFolder("instant-run");
        aaptTempFolder = supportDirectory.newFolder("aapt-temp");
        instantRunSliceSplitApkBuilder =
                new InstantRunSliceSplitApkBuilder(
                        logger,
                        project,
                        buildContext,
                        androidBuilder,
                        null,
                        () -> "com.foo.test",
                        coreSigningConfig,
                        new AaptOptions(null, false, null),
                        outputDirectory.getRoot(),
                        instantRunFolder,
                        mainResources,
                        mainResources,
                        apkList,
                        splitApkResources,
                        apkInfo) {
                    @Override
                    protected Aapt getLinker() {
                        return aapt;
                    }
                };
    }

    @Test
    public void testParameterInputs() {
        when(androidBuilder.getBuildToolInfo()).thenReturn(buildTools);
        when(buildTools.getRevision()).thenReturn(Revision.parseRevision("27.0.0"));

        Map<String, Object> parameterInputs = instantRunSliceSplitApkBuilder.getParameterInputs();
        assertThat(parameterInputs).containsEntry("applicationId", "com.foo.test");
        assertThat(parameterInputs).containsEntry("aaptVersion", "27.0.0");
        assertThat(parameterInputs).hasSize(2);
    }

    @Test
    public void testGeneratedManifest() throws Exception {

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        File folder = new File(instantRunFolder, "folderName");
        assertThat(folder.isDirectory()).isTrue();
        assertThat(folder.getName()).isEqualTo("folderName");
        File[] folderFiles = folder.listFiles();
        assertThat(folderFiles).hasLength(1);
        assertThat(folderFiles[0].getName()).isEqualTo("AndroidManifest.xml");
        String androidManifest = Files.toString(folderFiles[0], Charsets.UTF_8);
        assertThat(androidManifest).contains("package=\"com.foo.test\"");
        assertThat(androidManifest).contains("android:versionCode=\"12345\"");
        assertThat(androidManifest).contains("split=\"lib_folderName_apk\"");
    }

    @Test
    public void testApFileGeneration() throws Exception {

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        ArgumentCaptor<AaptPackageConfig.Builder> aaptConfigCaptor =
                ArgumentCaptor.forClass(AaptPackageConfig.Builder.class);

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        Mockito.verify(androidBuilder)
                .processResources(any(Aapt.class), aaptConfigCaptor.capture());

        AaptPackageConfig.Builder builder = aaptConfigCaptor.getValue();
        builder.setAndroidTarget(androidBuilder.getTarget());
        AaptPackageConfig build = builder.build();
        File resourceOutputApk = build.getResourceOutputApk();
        assertThat(resourceOutputApk.getName()).isEqualTo("resources_ap");
        assertThat(build.getVariantType()).isEqualTo(VariantTypeImpl.BASE_APK);
        assertThat(build.getDebuggable()).isTrue();
    }

    @Test
    public void testGenerateSplitApk() throws Exception {

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        File mainResourcesApk = new File("mainResources" + SdkConstants.DOT_RES);
        when(mainResourcesApkFileTree.getFiles()).thenReturn(ImmutableSet.of(mainResourcesApk));
        ArgumentCaptor<AaptPackageConfig.Builder> aaptConfigCaptor =
                ArgumentCaptor.forClass(AaptPackageConfig.Builder.class);

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        Mockito.verify(androidBuilder)
                .processResources(any(Aapt.class), aaptConfigCaptor.capture());

        AaptPackageConfig.Builder builder = aaptConfigCaptor.getValue();
        builder.setAndroidTarget(androidBuilder.getTarget());
        AaptPackageConfig build = builder.build();
        File resourceOutputApk = build.getResourceOutputApk();
        assertThat(build.getImports()).hasSize(1);
        assertThat(build.getImports()).containsExactly(mainResourcesApk);
        assertThat(resourceOutputApk.getName()).isEqualTo("resources_ap");

        ArgumentCaptor<File> outApkLocation = ArgumentCaptor.forClass(File.class);
        Mockito.verify(androidBuilder)
                .packageCodeSplitApk(
                        eq(resourceOutputApk),
                        eq(dexFiles.getDexFiles()),
                        eq(null),
                        outApkLocation.capture(),
                        any(File.class),
                        any(ApkCreatorFactory.class));

        assertThat(outApkLocation.getValue().getName()).isEqualTo(dexFiles.encodeName() + ".apk");
        Mockito.verify(buildContext).addChangedFile(eq(FileType.SPLIT),
                eq(outApkLocation.getValue()));
    }

    @Test
    public void testNoVersionGeneration() throws Exception {

        File apkListFile = apkListDirectory.newFile("apk-list2.json");
        ApkData apkInfo = ApkData.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), -1);

        FileUtils.write(apkListFile, ExistingBuildElements.persistApkList(ImmutableList.of()));
        when(apkList.iterator()).thenReturn(ImmutableList.of(apkListFile).iterator());

        InstantRunSliceSplitApkBuilder instantRunSliceSplitApkBuilder =
                new InstantRunSliceSplitApkBuilder(
                        logger,
                        project,
                        buildContext,
                        androidBuilder,
                        null,
                        () -> "com.foo.test",
                        coreSigningConfig,
                        new AaptOptions(null, false, null),
                        outputDirectory.getRoot(),
                        instantRunFolder,
                        mainResources,
                        mainResources,
                        apkList,
                        splitApkResources,
                        apkInfo) {
                    @Override
                    protected Aapt getLinker() {
                        return aapt;
                    }
                };

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        File folder = new File(instantRunFolder, "folderName");
        assertThat(folder.isDirectory()).isTrue();
        assertThat(folder.getName()).isEqualTo("folderName");
        File[] folderFiles = folder.listFiles();
        assertThat(folderFiles).hasLength(1);
        assertThat(folderFiles[0].getName()).isEqualTo("AndroidManifest.xml");
        String androidManifest = Files.toString(folderFiles[0], Charsets.UTF_8);
        assertThat(androidManifest).doesNotContain("android:versionCode");
        assertThat(androidManifest).doesNotContain("android:versionName");
    }
}
