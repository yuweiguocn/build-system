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

import static java.nio.file.Files.deleteIfExists;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.tasks.SigningConfigMetadata;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.BlockingResourceLinker;
import com.android.builder.internal.aapt.CloseableBlockingResourceLinker;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.ide.common.signing.KeytoolException;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.FileUtils;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

/** Common behavior for creating instant run related split APKs. */
public abstract class InstantRunSplitApkBuilder extends Transform {

    @NonNull
    protected final Logger logger;
    @NonNull
    protected final Project project;
    @NonNull protected final AndroidBuilder androidBuilder;
    @Nullable private FileCollection aapt2FromMaven;
    @NonNull protected final InstantRunBuildContext buildContext;
    @NonNull
    protected final File outputDirectory;
    @Nullable protected final FileCollection signingConf;
    @NonNull private final Supplier<String> applicationIdSupplier;
    @NonNull
    private final AaptOptions aaptOptions;
    @NonNull protected final File supportDirectory;
    // there is no need to make the resources a dependency of this transform
    // as we only use it to successfully compile the split manifest file. Any change to the
    // manifest that should force regenerating our split manifest is captured by the resource
    // dependency below.
    @NonNull protected final BuildableArtifact resources;
    // the resources containing the main manifest, which could be the same as above depending if
    // a separate APK for resources is used or not.
    // we are only interested in manifest binary changes, therefore, it is only needed as a
    // secondary input so we don't repackage all of our slices when only the resources change.
    @NonNull protected final BuildableArtifact resourcesWithMainManifest;

    @NonNull private final BuildableArtifact apkList;
    @NonNull protected final ApkData mainApk;

    public InstantRunSplitApkBuilder(
            @NonNull Logger logger,
            @NonNull Project project,
            @NonNull InstantRunBuildContext buildContext,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCollection aapt2FromMaven,
            @NonNull Supplier<String> applicationIdSupplier,
            @Nullable FileCollection signingConf,
            @NonNull AaptOptions aaptOptions,
            @NonNull File outputDirectory,
            @NonNull File supportDirectory,
            @NonNull BuildableArtifact resources,
            @NonNull BuildableArtifact resourcesWithMainManifest,
            @NonNull BuildableArtifact apkList,
            @NonNull ApkData mainApk) {
        this.logger = logger;
        this.project = project;
        this.buildContext = buildContext;
        this.androidBuilder = androidBuilder;
        this.aapt2FromMaven = aapt2FromMaven;
        this.applicationIdSupplier = applicationIdSupplier;
        this.signingConf = signingConf;
        this.aaptOptions = aaptOptions;
        this.outputDirectory = outputDirectory;
        this.supportDirectory = supportDirectory;
        this.resources = resources;
        this.resourcesWithMainManifest = resourcesWithMainManifest;
        this.apkList = apkList;
        this.mainApk = mainApk;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> list = ImmutableList.builder();
        if (aapt2FromMaven != null) {
            list.add(SecondaryFile.nonIncremental(aapt2FromMaven));
        }
        if (signingConf != null) {
            list.add(SecondaryFile.nonIncremental(signingConf));
        }
        resourcesWithMainManifest
                .get()
                .getAsFileTree()
                .getFiles()
                .stream()
                .map(SplitSecondaryFile::new)
                .forEach(list::add);
        list.add(SecondaryFile.nonIncremental(apkList));
        return list.build();
    }

    /**
     * Use a specialization of the {@link SecondaryFile} to achieve conditional dependency.
     *
     * <p>This transform theoretically depends on the resources bundle as the split manifest file
     * generated can contain resource references : android:versionName="@string/app_name" However,
     * we don't want to rebuild all the split APKs when only android resources change. Furthermore,
     * we do want to rebuild all the split APKs when the main manifest file changed.
     *
     * <p>This version will therefore return false from {@link #supportsIncrementalBuild()} when a
     * binary manifest file change has been detected (forcing a non incremental transform call) or
     * true otherwise.
     */
    private class SplitSecondaryFile extends SecondaryFile {

        public SplitSecondaryFile(@NonNull File secondaryInputFile) {
            super(secondaryInputFile, true);
        }

        @Override
        public boolean supportsIncrementalBuild() {
            // if our verifier status indicates that MANIFEST_FILE_CHANGE, we should re-create
            // all of our splits, request a non incremental mode.
            return !buildContext.hasVerifierStatusBeenSet(
                    InstantRunVerifierStatus.MANIFEST_FILE_CHANGE);
        }
    }

    @NonNull
    @Override
    public final Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> builder =
                ImmutableMap.<String, Object>builder()
                        .put("applicationId", applicationIdSupplier.get())
                        .put(
                                "aaptVersion",
                                androidBuilder.getBuildToolInfo().getRevision().toString());
        return builder.build();
    }

    protected static class DexFiles {
        private final ImmutableSet<File> dexFiles;
        private final String dexFolderName;

        protected DexFiles(@NonNull File[] dexFiles, @NonNull String dexFolderName) {
            this(ImmutableSet.copyOf(dexFiles), dexFolderName);
        }

        protected DexFiles(@NonNull ImmutableSet<File> dexFiles, @NonNull String dexFolderName) {
            this.dexFiles = dexFiles;
            this.dexFolderName = dexFolderName;
        }

        protected String encodeName() {
            return dexFolderName.replace('-', '_');
        }

        protected ImmutableSet<File> getDexFiles() {
            return dexFiles;
        }
    }

    @NonNull
    protected File generateSplitApk(@NonNull ApkData apkData, @NonNull DexFiles dexFiles)
            throws IOException, KeytoolException, PackagerException, ProcessException,
                    TransformException {

        String uniqueName = dexFiles.encodeName();
        final File alignedOutput = new File(outputDirectory, uniqueName + ".apk");
        Files.createParentDirs(alignedOutput);

        try (CloseableBlockingResourceLinker aapt = getLinker()) {
            File resPackageFile =
                    generateSplitApkResourcesAp(
                            logger,
                            aapt,
                            applicationIdSupplier,
                            apkData,
                            supportDirectory,
                            aaptOptions,
                            androidBuilder,
                            uniqueName,
                            resources);

            // packageCodeSplitApk uses a temporary directory for incremental runs. Since we don't
            // do incremental builds here, make sure it gets an empty directory.
            File tempDir = new File(supportDirectory, "package_" + uniqueName);
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new TransformException(
                        "Cannot create temporary folder " + tempDir.getAbsolutePath());
            }

            FileUtils.cleanOutputDir(tempDir);

            androidBuilder.packageCodeSplitApk(
                    resPackageFile,
                    dexFiles.dexFiles,
                    SigningConfigMetadata.Companion.load(signingConf),
                    alignedOutput,
                    tempDir,
                    ApkCreatorFactories.fromProjectProperties(project, true));

            buildContext.addChangedFile(FileType.SPLIT, alignedOutput);
            deleteIfExists(resPackageFile.toPath());
        }

        return alignedOutput;
    }

    @NonNull
    public static File generateSplitApkManifest(
            @NonNull File apkSupportDir,
            @NonNull String splitName,
            @NonNull Supplier<String> packageIdSupplier,
            @Nullable String versionName,
            int versionCode,
            @Nullable String minSdkVersion)
            throws IOException {

        String versionNameToUse = versionName;
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(versionCode);
        }
        StringBuilder escapedVersionNameToUse = new StringBuilder();
        // We need to escape the version name in case the users want to use special characters, for
        // example apostrophes.
        XmlUtils.appendXmlAttributeValue(escapedVersionNameToUse, versionNameToUse);

        File androidManifest = new File(apkSupportDir, SdkConstants.ANDROID_MANIFEST_XML);
        try (BufferedWriter fileWriter =
                new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(androidManifest), "UTF-8"))) {
            fileWriter
                    .append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                    .append(
                            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                    .append("      package=\"")
                    .append(packageIdSupplier.get())
                    .append("\"\n");
            if (versionCode != VersionQualifier.DEFAULT_VERSION) {
                fileWriter
                        .append("      android:versionCode=\"")
                        .append(String.valueOf(versionCode))
                        .append("\"\n")
                        .append("      android:versionName=\"")
                        .append(escapedVersionNameToUse)
                        .append("\"\n");
            }
            fileWriter.append("      split=\"lib_").append(splitName).append("_apk\">\n");
            if (minSdkVersion != null) {
                fileWriter
                        .append("\t<uses-sdk android:minSdkVersion=\"")
                        .append(minSdkVersion)
                        .append("\"/>\n");
            }
            fileWriter.append("</manifest>\n").flush();
        }
        return androidManifest;
    }

    /**
     * Generate a split APK resources, only containing a minimum AndroidManifest.xml to be a legal
     * split APK but has not resources attached. The returned resources_ap file returned can be used
     * to build a legal split APK.
     */
    @NonNull
    public static File generateSplitApkResourcesAp(
            @NonNull Logger logger,
            @NonNull BlockingResourceLinker aapt,
            @NonNull Supplier<String> applicationIdSupplier,
            @NonNull ApkData apkData,
            @NonNull File supportDirectory,
            @NonNull AaptOptions aaptOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull String uniqueName,
            @NonNull BuildableArtifact imports)
            throws IOException, ProcessException {

        File apkSupportDir = new File(supportDirectory, uniqueName);
        if (!apkSupportDir.exists() && !apkSupportDir.mkdirs()) {
            logger.error("Cannot create apk support dir {}", apkSupportDir.getAbsoluteFile());
        }
        File androidManifest =
                generateSplitApkManifest(
                        apkSupportDir,
                        uniqueName,
                        applicationIdSupplier,
                        apkData.getVersionName(),
                        apkData.getVersionCode(),
                        null);

        return generateSplitApkResourcesAp(
                logger,
                aapt,
                androidManifest,
                supportDirectory,
                aaptOptions,
                androidBuilder,
                imports,
                uniqueName);
    }

    /**
     * Generate the compile resouces_ap file that contains the resources for this split plus the
     * split definition.
     */
    @NonNull
    public static File generateSplitApkResourcesAp(
            @NonNull Logger logger,
            @NonNull BlockingResourceLinker aapt,
            @NonNull File androidManifest,
            @NonNull File supportDirectory,
            @NonNull AaptOptions aaptOptions,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable BuildableArtifact imports,
            @NonNull String uniqueName)
            throws IOException, ProcessException {

        File apkSupportDir = new File(supportDirectory, uniqueName);
        if (!apkSupportDir.exists() && !apkSupportDir.mkdirs()) {
            logger.error("Cannot create apk support dir {}", apkSupportDir.getAbsoluteFile());
        }

        File resFilePackageFile = new File(apkSupportDir, "resources_ap");

        List<File> importedAPKs =
                imports != null
                        ? imports.get()
                                .getAsFileTree()
                                .getFiles()
                                .stream()
                                .filter(file -> file.getName().endsWith(SdkConstants.EXT_RES))
                                .collect(Collectors.toList())
                        : ImmutableList.of();

        AaptPackageConfig.Builder aaptConfig =
                new AaptPackageConfig.Builder()
                        .setManifestFile(androidManifest)
                        .setOptions(aaptOptions)
                        .setDebuggable(true)
                        .setVariantType(VariantTypeImpl.BASE_APK)
                        .setImports(ImmutableList.copyOf(importedAPKs))
                        .setResourceOutputApk(resFilePackageFile);

        androidBuilder.processResources(aapt, aaptConfig);

        return resFilePackageFile;
    }

    /**
     * Generate the compile resouces_ap file that contains the resources for this split plus the
     * split definition.
     */
    public static void generateSplitApkResourcesAp(
            @NonNull Logger logger,
            @NonNull BlockingResourceLinker aapt,
            @NonNull File androidManifest,
            @NonNull File resFilePackageFile,
            @NonNull AaptOptions aaptOptions,
            @NonNull IAndroidTarget androidTarget,
            @NonNull Set<File> importsFiles)
            throws IOException, ProcessException {
        List<File> importedAPKs =
                importsFiles
                        .stream()
                        .filter(file -> file.getName().endsWith(SdkConstants.EXT_RES))
                        .collect(Collectors.toList());

        AaptPackageConfig.Builder aaptConfig =
                new AaptPackageConfig.Builder()
                        .setManifestFile(androidManifest)
                        .setOptions(aaptOptions)
                        .setDebuggable(true)
                        .setVariantType(VariantTypeImpl.BASE_APK)
                        .setImports(ImmutableList.copyOf(importedAPKs))
                        .setResourceOutputApk(resFilePackageFile);

        AndroidBuilder.processResources(aapt, aaptConfig, androidTarget, new LoggerWrapper(logger));
    }

    protected CloseableBlockingResourceLinker getLinker() {
        return getLinker(getAapt2ServiceKey(aapt2FromMaven, androidBuilder));
    }

    @NonNull
    public static Aapt2ServiceKey getAapt2ServiceKey(
            @Nullable FileCollection aapt2FromMaven, @NonNull AndroidBuilder androidBuilder) {
        return Aapt2DaemonManagerService.registerAaptService(
                aapt2FromMaven, androidBuilder.getBuildToolInfo(), androidBuilder.getLogger());
    }

    @NonNull
    public static CloseableBlockingResourceLinker getLinker(
            @NonNull Aapt2ServiceKey aapt2ServiceKey) {
        return Aapt2DaemonManagerService.getAaptDaemon(aapt2ServiceKey);
    }
}
