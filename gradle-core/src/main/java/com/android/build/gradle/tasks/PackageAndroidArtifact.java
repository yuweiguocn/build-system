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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildElementsTransformParams;
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData.InputSet;
import com.android.build.gradle.internal.tasks.SigningConfigMetadata;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.packaging.PackagingUtils;
import com.android.builder.utils.FileCache;
import com.android.builder.utils.ZipEntryUtils;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.android.tools.build.apkzlib.zip.compress.Zip64NotSupportedException;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Abstract task to package an Android artifact. */
public abstract class PackageAndroidArtifact extends IncrementalTask {

    public static final String INSTANT_RUN_PACKAGES_PREFIX = "instant-run";

    // ----- PUBLIC TASK API -----

    // Path sensitivity here is absolute due to http://b/72085541
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public Provider<Directory> getManifests() {
        return manifests;
    }

    // Path sensitivity here is absolute due to http://b/72085541
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public BuildableArtifact getResourceFiles() {
        return resourceFiles;
    }

    @Input
    @NonNull
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(@Nullable Set<String> abiFilters) {
        this.abiFilters = abiFilters != null ? abiFilters : ImmutableSet.of();
    }

    // ----- PRIVATE TASK API -----

    protected InternalArtifactType manifestType;

    @Input
    public InternalArtifactType getManifestType() {
        return manifestType;
    }

    // Path sensitivity here is absolute due to http://b/72085541
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getJavaResourceFiles() {
        return javaResourceFiles;
    }

    // Path sensitivity here is absolute due to http://b/72085541
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getJniFolders() {
        return jniFolders;
    }

    protected BuildableArtifact resourceFiles;

    protected FileCollection dexFolders;

    @Nullable protected FileCollection featureDexFolder;

    protected BuildableArtifact assets;

    // Path sensitivity here is absolute due to http://b/72085541
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getDexFolders() {
        return dexFolders;
    }

    // Path sensitivity here is absolute due to http://b/72085541
    @InputFiles
    @Optional
    @Nullable
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getFeatureDexFolder() {
        return featureDexFolder;
    }

    // Path sensitivity here is absolute due to http://b/72085541
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public BuildableArtifact getAssets() {
        return assets;
    }

    /** list of folders and/or jars that contain the merged java resources. */
    protected FileCollection javaResourceFiles;
    protected FileCollection jniFolders;

    private Set<String> abiFilters;

    private boolean debugBuild;
    private boolean jniDebugBuild;

    private FileCollection signingConfig;

    protected Supplier<AndroidVersion> minSdkVersion;

    protected Supplier<InstantRunBuildContext> instantRunContext;

    protected Provider<Directory> manifests;

    @Nullable protected Collection<String> aaptOptionsNoCompress;

    protected FileType instantRunFileType;

    protected OutputScope outputScope;

    protected String projectBaseName;

    @Nullable protected String buildTargetAbi;

    @Nullable protected String buildTargetDensity;

    protected File outputDirectory;

    @Nullable protected OutputFileProvider outputFileProvider;

    private final WorkerExecutorFacade workers;

    public PackageAndroidArtifact(WorkerExecutorFacade workers) {
        this.workers = workers;
    }

    @Input
    public String getProjectBaseName() {
        return projectBaseName;
    }

    protected FileCache fileCache;

    protected BuildableArtifact apkList;

    /** Desired output format. */
    protected IncrementalPackagerBuilder.ApkFormat apkFormat;

    @Input
    public String getApkFormat() {
        return apkFormat.name();
    }

    /**
     * Name of directory, inside the intermediate directory, where zip caches are kept.
     */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";
    private static final String ZIP_64_COPY_DIR = "zip64-copy";

    @Input
    public boolean getJniDebugBuild() {
        return jniDebugBuild;
    }

    public void setJniDebugBuild(boolean jniDebugBuild) {
        this.jniDebugBuild = jniDebugBuild;
    }

    @Input
    public boolean getDebugBuild() {
        return debugBuild;
    }

    public void setDebugBuild(boolean debugBuild) {
        this.debugBuild = debugBuild;
    }

    /**
     * Retrieves the signing config file collection. It is necessary to make this an optional input
     * for instant run packaging, which explicitly sets this to a null file collection.
     */
    @InputFiles
    @Optional
    public FileCollection getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(FileCollection signingConfig) {
        this.signingConfig = signingConfig;
    }

    @Input
    public int getMinSdkVersion() {
        return this.minSdkVersion.get().getApiLevel();
    }

    @Input
    public Boolean isInInstantRunMode() {
        return instantRunContext.get().isInInstantRunMode();
    }

    /*
     * We don't really use this. But this forces a full build if the native packaging mode changes.
     */
    @Input
    public List<String> getNativeLibrariesPackagingModeName() {
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
        manifests
                .get()
                .getAsFileTree()
                .getFiles()
                .forEach(
                        manifest -> {
                            if (manifest.isFile()
                                    && manifest.getName()
                                            .equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                                listBuilder.add(
                                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                                        manifest,
                                                        () -> true,
                                                        getBuilder().getIssueReporter())
                                                .toString());
                            }
                        });
        return listBuilder.build();
    }

    @NonNull
    @Input
    public Collection<String> getNoCompressExtensions() {
        return aaptOptionsNoCompress != null ? aaptOptionsNoCompress : Collections.emptyList();
    }

    interface OutputFileProvider {
        @NonNull
        File getOutputFile(@NonNull ApkData apkData);
    }

    InternalArtifactType taskInputType;

    @Input
    public InternalArtifactType getTaskInputType() {
        return taskInputType;
    }

    @Input
    @Optional
    @Nullable
    public String getBuildTargetAbi() {
        return buildTargetAbi;
    }

    @Input
    @Optional
    @Nullable
    public String getBuildTargetDensity() {
        return buildTargetDensity;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Returns the paths to generated APKs as @Input to this task, so that when the output file name
     * is changed (e.g., by the users), the task will be re-executed in non-incremental mode.
     */
    @Input
    public Collection<String> getApkNames() {
        // this task does not handle packaging of the configuration splits.
        return outputScope
                .getApkDatas()
                .stream()
                .filter(apkData -> apkData.getType() != VariantOutput.OutputType.SPLIT)
                .map(ApkData::getOutputFileName)
                .collect(Collectors.toList());
    }

    @InputFiles
    public BuildableArtifact getApkList() {
        return apkList;
    }

    private static BuildOutput computeBuildOutputFile(
            ApkData apkInfo,
            OutputFileProvider outputFileProvider,
            File outputDirectory,
            InternalArtifactType expectedOutputType) {
        File outputFile =
                outputFileProvider != null
                        ? outputFileProvider.getOutputFile(apkInfo)
                        : new File(outputDirectory, apkInfo.getOutputFileName());
        return new BuildOutput(expectedOutputType, apkInfo, outputFile);
    }


    protected abstract InternalArtifactType getInternalArtifactType();

    @NonNull
    static Set<File> getAndroidResources(@Nullable File processedResources) {

        return processedResources != null ? ImmutableSet.of(processedResources) : ImmutableSet.of();
    }

    @Override
    protected void doFullTaskAction() {

        // check that we don't have colliding output file names
        checkFileNameUniqueness();
        List<File> inputList = new ArrayList<>();
        List<File> outputList = new ArrayList<>();

        ExistingBuildElements.from(getTaskInputType(), resourceFiles)
                .transform(
                        workers,
                        FullActionSplitterRunnable.class,
                        (ApkData apkInfo, File inputFile) -> {
                            SplitterParams params = new SplitterParams(apkInfo, inputFile, this);
                            inputList.add(inputFile);
                            outputList.add(params.getOutput());
                            return params;
                        })
                .into(getInternalArtifactType(), outputDirectory);

        for (int i = 0; i < inputList.size(); i++) {
            instantRunContext.get().addChangedFile(instantRunFileType, outputList.get(i));
            recordMetrics(outputList.get(i), inputList.get(i));
        }
    }

    private void checkFileNameUniqueness() {
        BuildElements buildElements = ExistingBuildElements.from(getTaskInputType(), resourceFiles);
        checkFileNameUniqueness(buildElements);
    }

    @VisibleForTesting
    static void checkFileNameUniqueness(BuildElements buildElements) {

        Collection<File> fileOutputs =
                buildElements.stream().map(BuildOutput::getOutputFile).collect(Collectors.toList());

        java.util.Optional<String> repeatingFileNameOptional =
                fileOutputs
                        .stream()
                        .filter(fileOutput -> Collections.frequency(fileOutputs, fileOutput) > 1)
                        .map(File::getName)
                        .findFirst();
        if (repeatingFileNameOptional.isPresent()) {
            String repeatingFileName = repeatingFileNameOptional.get();
            List<String> conflictingApks =
                    buildElements
                            .stream()
                            .filter(
                                    buildOutput ->
                                            buildOutput
                                                    .getOutputFile()
                                                    .getName()
                                                    .equals(repeatingFileName))
                            .map(
                                    buildOutput -> {
                                        ApkData apkInfo = buildOutput.getApkData();
                                        if (apkInfo.getFilters().isEmpty()) {
                                            return apkInfo.getType().toString();
                                        } else {
                                            return Joiner.on("-").join(apkInfo.getFilters());
                                        }
                                    })
                            .collect(Collectors.toList());

            throw new RuntimeException(
                    String.format(
                            "Several variant outputs are configured to use "
                                    + "the same file name \"%1$s\", filters : %2$s",
                            repeatingFileName, Joiner.on(":").join(conflictingApks)));
        }
    }

    private static class FullActionSplitterRunnable extends BuildElementsTransformRunnable {

        @Inject
        public FullActionSplitterRunnable(@NonNull SplitterParams params) {
            super(params);
        }

        @Override
        public void run() {
            try {
                SplitterParams params = (SplitterParams) getParams();

                FileUtils.mkdirs(params.getOutput().getParentFile());

                File incrementalDirForSplit =
                        new File(params.incrementalFolder, params.apkInfo.getFullName());

                /*
                 * Clear the intermediate build directory. We don't know if anything is in there and
                 * since this is a full build, we don't want to get any interference from previous state.
                 */
                if (incrementalDirForSplit.exists()) {
                    FileUtils.deleteDirectoryContents(incrementalDirForSplit);
                } else {
                    FileUtils.mkdirs(incrementalDirForSplit);
                }

                File cacheByPathDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
                FileUtils.mkdirs(cacheByPathDir);
                FileCacheByPath cacheByPath = new FileCacheByPath(cacheByPathDir);

                /*
                 * Clear the cache to make sure we have do not do an incremental build.
                 */
                cacheByPath.clear();

                Set<File> androidResources = getAndroidResources(params.processedResources);

                /*
                 * Additionally, make sure we have no previous package, if it exists.
                 */
                FileUtils.deleteRecursivelyIfExists(params.getOutput());

                final ImmutableMap<RelativeFile, FileStatus> updatedDex;
                final ImmutableMap<RelativeFile, FileStatus> updatedJavaResources;
                if (params.featureDexFiles == null || params.featureDexFiles.isEmpty()) {
                    updatedDex =
                            IncrementalRelativeFileSets.fromZipsAndDirectories(params.dexFiles);
                    updatedJavaResources =
                            getJavaResourcesChanges(
                                    params.javaResourceFiles, params.incrementalFolder);
                } else {
                    // We reach this code if we're in a feature module and minification is enabled in the
                    // base module. In this case, we want to use the classes.dex file from the base
                    // module's DexSplitterTransform.
                    checkNotNull(params.featureDexFiles);
                    updatedDex =
                            IncrementalRelativeFileSets.fromZipsAndDirectories(
                                    params.featureDexFiles);
                    // For now, java resources are in the base apk, so we exclude them here (b/77546738)
                    updatedJavaResources = ImmutableMap.of();
                }
                ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(params.assetsFiles);
                ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
                ImmutableMap<RelativeFile, FileStatus> updatedJniResources =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(params.jniFiles);

                BuildElements manifestOutputs =
                        ExistingBuildElements.from(params.manifestType, params.manifestDirectory);
                doTask(
                        incrementalDirForSplit,
                        params.getOutput(),
                        cacheByPath,
                        manifestOutputs,
                        updatedDex,
                        updatedJavaResources,
                        updatedAssets,
                        updatedAndroidResources,
                        updatedJniResources,
                        params,
                        false);

                /*
                 * Update the known files.
                 */
                KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalDirForSplit);
                saveData.setInputSet(updatedDex.keySet(), InputSet.DEX);
                saveData.setInputSet(updatedJavaResources.keySet(), InputSet.JAVA_RESOURCE);
                saveData.setInputSet(updatedAssets.keySet(), InputSet.ASSET);
                saveData.setInputSet(updatedAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
                saveData.setInputSet(updatedJniResources.keySet(), InputSet.NATIVE_RESOURCE);
                saveData.saveCurrentData();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class SplitterParams extends BuildElementsTransformParams {
        @NonNull ApkData apkInfo;
        @NonNull File processedResources;
        @NonNull protected final File outputFile;
        @NonNull protected final File incrementalFolder;
        @NonNull protected final Set<File> dexFiles;
        @Nullable protected final Set<File> featureDexFiles;
        @NonNull protected final Set<File> assetsFiles;
        @NonNull protected final Set<File> jniFiles;
        @NonNull protected final Set<File> javaResourceFiles;
        @NonNull protected final InternalArtifactType manifestType;
        @NonNull protected final IncrementalPackagerBuilder.ApkFormat apkFormat;
        @Nullable protected final File signingConfig;
        @NonNull protected final Set<String> abiFilters;
        @NonNull protected final FileType instantRunFileType;
        @NonNull protected final File manifestDirectory;
        @Nullable protected final Collection<String> aaptOptionsNoCompress;
        @Nullable protected final String createdBy;
        protected final int minSdkVersion;
        protected final boolean isInInstantRunMode;
        protected final boolean isDebuggableBuild;
        protected final boolean isJniDebuggableBuild;
        protected final boolean keepTimestampsInApk;

        /**
         * This should only be used in instant run mode in incremental task action where parameters
         * will not be serialized
         */
        @Nullable protected transient InstantRunBuildContext instantRunBuildContext;

        SplitterParams(
                @NonNull ApkData apkInfo,
                @NonNull File processedResources,
                PackageAndroidArtifact task) {
            this.apkInfo = apkInfo;
            this.processedResources = processedResources;

            outputFile =
                    computeBuildOutputFile(
                                    apkInfo,
                                    task.outputFileProvider,
                                    task.outputDirectory,
                                    task.getInternalArtifactType())
                            .getOutputFile();

            incrementalFolder = task.getIncrementalFolder();
            dexFiles = task.getDexFolders().getFiles();
            featureDexFiles =
                    task.getFeatureDexFolder() == null
                            ? null
                            : task.getFeatureDexFolder().getFiles();
            assetsFiles = task.getAssets().getFiles();
            jniFiles = task.getJniFolders().getFiles();
            javaResourceFiles = task.getJavaResourceFiles().getFiles();
            manifestType = task.getManifestType();
            apkFormat = task.apkFormat;
            signingConfig = SigningConfigMetadata.Companion.getOutputFile(task.signingConfig);
            abiFilters = task.abiFilters;
            instantRunFileType = task.instantRunFileType;
            manifestDirectory = task.getManifests().get().getAsFile();
            aaptOptionsNoCompress = task.aaptOptionsNoCompress;
            createdBy = task.getBuilder().getCreatedBy();
            minSdkVersion = task.getMinSdkVersion();
            isInInstantRunMode = task.isInInstantRunMode();
            isDebuggableBuild = task.getDebugBuild();
            isJniDebuggableBuild = task.getJniDebugBuild();
            keepTimestampsInApk = AndroidGradleOptions.keepTimestampsInApk(task.getProject());
        }

        @NonNull
        @Override
        public File getOutput() {
            return outputFile;
        }
    }

    abstract void recordMetrics(File outputFile, File resourcesApFile);

    /**
     * Copy the input zip file (probably a Zip64) content into a new Zip in the destination folder
     * stripping out all .class files.
     *
     * @param destinationFolder the destination folder to use, the output jar will have the same
     *     name as the input zip file.
     * @param zip64File the input zip file.
     * @return the path to the stripped Zip file.
     * @throws IOException if the copying failed.
     */
    @VisibleForTesting
    static File copyJavaResourcesOnly(File destinationFolder, File zip64File) throws IOException {
        File cacheDir = new File(destinationFolder, ZIP_64_COPY_DIR);
        File copiedZip = new File(cacheDir, zip64File.getName());
        FileUtils.mkdirs(copiedZip.getParentFile());

        try (ZipFile inFile = new ZipFile(zip64File);
                ZipOutputStream outFile =
                        new ZipOutputStream(
                                new BufferedOutputStream(new FileOutputStream(copiedZip)))) {

            Enumeration<? extends ZipEntry> entries = inFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.getName().endsWith(SdkConstants.DOT_CLASS)
                        && ZipEntryUtils.isValidZipEntryName(zipEntry)) {
                    outFile.putNextEntry(new ZipEntry(zipEntry.getName()));
                    try {
                        ByteStreams.copy(
                                new BufferedInputStream(inFile.getInputStream(zipEntry)), outFile);
                    } finally {
                        outFile.closeEntry();
                    }
                }
            }
        }
        return copiedZip;
    }

    private static ImmutableMap<RelativeFile, FileStatus> getJavaResourcesChanges(
            Iterable<File> javaResourceFiles, File incrementalFolder) throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> updatedJavaResourcesBuilder =
                ImmutableMap.builder();
        for (File javaResourceFile : javaResourceFiles) {
            try {
                updatedJavaResourcesBuilder.putAll(
                        javaResourceFile.isFile()
                                ? IncrementalRelativeFileSets.fromZip(javaResourceFile)
                                : IncrementalRelativeFileSets.fromDirectory(javaResourceFile));
            } catch (Zip64NotSupportedException e) {
                updatedJavaResourcesBuilder.putAll(
                        IncrementalRelativeFileSets.fromZip(
                                copyJavaResourcesOnly(incrementalFolder, javaResourceFile)));
            }
        }
        return updatedJavaResourcesBuilder.build();
    }

    /**
     * Packages the application incrementally. In case of instant run packaging, this is not a
     * perfectly incremental task as some files are always rewritten even if no change has occurred.
     *
     * @param outputFile expected output package file
     * @param changedDex incremental dex packaging data
     * @param changedJavaResources incremental java resources
     * @param changedAssets incremental assets
     * @param changedAndroidResources incremental Android resource
     * @param changedNLibs incremental native libraries changed
     * @throws IOException failed to package the APK
     */
    private static void doTask(
            @NonNull File incrementalDirForSplit,
            @NonNull File outputFile,
            @NonNull FileCacheByPath cacheByPath,
            @NonNull BuildElements manifestOutputs,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs,
            @NonNull SplitterParams params,
            boolean isIncremental)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        if (params.isInInstantRunMode) {
            changedDex =
                    ImmutableMap.copyOf(
                            Maps.filterKeys(
                                    changedDex,
                                    Predicates.compose(
                                            Predicates.in(params.dexFiles),
                                            RelativeFile::getBase)));
        }
        final ImmutableMap<RelativeFile, FileStatus> dexFilesToPackage = changedDex;

        String filter = null;
        FilterData abiFilter = params.apkInfo.getFilter(OutputFile.FilterType.ABI);
        if (abiFilter != null) {
            filter = abiFilter.getIdentifier();
        }

        // find the manifest file for this split.
        BuildOutput manifestForSplit = manifestOutputs.element(params.apkInfo);

        if (manifestForSplit == null) {
            throw new RuntimeException(
                    "Found a .ap_ for split "
                            + params.apkInfo
                            + " but no "
                            + params.manifestType
                            + " associated manifest file");
        }
        FileUtils.mkdirs(outputFile.getParentFile());
        // we are executing a task right now, so we can parse the manifest.
        BooleanSupplier isInExecutionPhase = () -> true;
        SigningOptions.Validation validation =
                isIncremental
                        ? SigningOptions.Validation.ASSUME_VALID
                        : SigningOptions.Validation.ASSUME_INVALID;

        try (IncrementalPackager packager =
                new IncrementalPackagerBuilder(params.apkFormat)
                        .withOutputFile(outputFile)
                        .withSigning(
                                SigningConfigMetadata.Companion.load(params.signingConfig),
                                validation)
                        .withCreatedBy(params.createdBy)
                        .withMinSdk(params.minSdkVersion)
                        // TODO: allow extra metadata to be saved in the split scope to avoid
                        // reparsing
                        // these manifest files.
                        .withNativeLibraryPackagingMode(
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                        manifestForSplit.getOutputFile(), isInExecutionPhase, null))
                        .withNoCompressPredicate(
                                PackagingUtils.getNoCompressPredicate(
                                        params.aaptOptionsNoCompress,
                                        manifestForSplit.getOutputFile(),
                                        isInExecutionPhase,
                                        null))
                        .withIntermediateDir(incrementalDirForSplit)
                        .withKeepTimestampsInApk(params.keepTimestampsInApk)
                        .withDebuggableBuild(params.isDebuggableBuild)
                        .withAcceptedAbis(
                                filter == null ? params.abiFilters : ImmutableSet.of(filter))
                        .withJniDebuggableBuild(params.isJniDebuggableBuild)
                        .build()) {
            packager.updateDex(dexFilesToPackage);
            packager.updateJavaResources(changedJavaResources);
            packager.updateAssets(changedAssets);
            packager.updateAndroidResources(changedAndroidResources);
            packager.updateNativeLibraries(changedNLibs);
            // Only report APK as built if it has actually changed.
            if (packager.hasPendingChangesWithWait()) {
                // FIX-ME : below would not work in multi apk situations. There is code somewhere
                // to ensure we only build ONE multi APK for the target device, make sure it is still
                // active.
                if (params.instantRunBuildContext != null) {
                    params.instantRunBuildContext.addChangedFile(
                            params.instantRunFileType, outputFile);
                }
            }
        }

        /*
         * Save all used zips in the cache.
         */
        Stream.concat(
                        dexFilesToPackage.keySet().stream(),
                        Stream.concat(
                                changedJavaResources.keySet().stream(),
                                Stream.concat(
                                        changedAndroidResources.keySet().stream(),
                                        changedNLibs.keySet().stream())))
                .map(RelativeFile::getBase)
                .filter(File::isFile)
                .distinct()
                .forEach(
                        (File f) -> {
                            try {
                                cacheByPath.add(f);
                            } catch (IOException e) {
                                throw new IOExceptionWrapper(e);
                            }
                        });
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        checkNotNull(changedInputs, "changedInputs == null");

        // in instant run mode we don't use worker API because we need to report which files changed
        // in the incremental build to the instantRunBuildContext and that can't be done from the
        // isolated worker items.
        if (isInInstantRunMode()) {
            ExistingBuildElements.from(getTaskInputType(), resourceFiles)
                    .transform(
                            (apkInfo, inputFile) -> {
                                IncrementalSplitterParams params =
                                        new IncrementalSplitterParams(
                                                apkInfo, inputFile, changedInputs, this);
                                new IncrementalSplitterRunnable(params).run();
                                return params.getOutput();
                            })
                    .into(getInternalArtifactType(), outputDirectory);
        } else {
            ExistingBuildElements.from(getTaskInputType(), resourceFiles)
                    .transform(
                            workers,
                            IncrementalSplitterRunnable.class,
                            (apkInfo, inputFile) ->
                                    new IncrementalSplitterParams(
                                            apkInfo, inputFile, changedInputs, this))
                    .into(getInternalArtifactType(), outputDirectory);
        }
    }

    private static class IncrementalSplitterRunnable extends BuildElementsTransformRunnable {

        @Inject
        public IncrementalSplitterRunnable(@NonNull IncrementalSplitterParams params) {
            super(params);
        }

        @Override
        public void run() {
            IncrementalSplitterParams params = (IncrementalSplitterParams) getParams();
            try {
                Set<File> androidResources = getAndroidResources(params.processedResources);

                File incrementalDirForSplit =
                        new File(params.incrementalFolder, params.apkInfo.getFullName());

                File cacheByPathDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
                if (!cacheByPathDir.exists()) {
                    FileUtils.mkdirs(cacheByPathDir);
                }
                FileCacheByPath cacheByPath = new FileCacheByPath(cacheByPathDir);

                KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalDirForSplit);

                Set<Runnable> cacheUpdates = new HashSet<>();

                final Set<File> dexFiles;
                final Set<File> javaResourceFiles;
                if (params.featureDexFiles == null || params.featureDexFiles.isEmpty()) {
                    dexFiles = params.dexFiles;
                    javaResourceFiles = params.javaResourceFiles;
                } else {
                    // We reach this code if we're in a feature module and minification is enabled in the
                    // base module. In this case, we want to use the classes.dex file from the base
                    // module's DexSplitterTransform.
                    checkNotNull(params.featureDexFiles);
                    dexFiles = params.featureDexFiles;
                    // For now, java resources are in the base apk, so we exclude them here (b/77546738)
                    javaResourceFiles = ImmutableSet.of();
                }

                ImmutableMap<RelativeFile, FileStatus> changedDexFiles =
                        KnownFilesSaveData.getChangedInputs(
                                params.changedInputs,
                                saveData,
                                InputSet.DEX,
                                dexFiles,
                                cacheByPath,
                                cacheUpdates);

                ImmutableMap<RelativeFile, FileStatus> changedJavaResources;
                try {
                    changedJavaResources =
                            KnownFilesSaveData.getChangedInputs(
                                    params.changedInputs,
                                    saveData,
                                    InputSet.JAVA_RESOURCE,
                                    javaResourceFiles,
                                    cacheByPath,
                                    cacheUpdates);
                } catch (Zip64NotSupportedException e) {
                    // copy all changedInputs into a smaller jar and rerun.
                    ImmutableMap.Builder<File, FileStatus> copiedInputs = ImmutableMap.builder();
                    for (Map.Entry<File, FileStatus> fileFileStatusEntry :
                            params.changedInputs.entrySet()) {
                        copiedInputs.put(
                                copyJavaResourcesOnly(
                                        params.incrementalFolder, fileFileStatusEntry.getKey()),
                                fileFileStatusEntry.getValue());
                    }
                    changedJavaResources =
                            KnownFilesSaveData.getChangedInputs(
                                    copiedInputs.build(),
                                    saveData,
                                    InputSet.JAVA_RESOURCE,
                                    params.javaResourceFiles,
                                    cacheByPath,
                                    cacheUpdates);
                }

                ImmutableMap<RelativeFile, FileStatus> changedAssets =
                        KnownFilesSaveData.getChangedInputs(
                                params.changedInputs,
                                saveData,
                                InputSet.ASSET,
                                params.assetsFiles,
                                cacheByPath,
                                cacheUpdates);

                ImmutableMap<RelativeFile, FileStatus> changedAndroidResources =
                        KnownFilesSaveData.getChangedInputs(
                                params.changedInputs,
                                saveData,
                                InputSet.ANDROID_RESOURCE,
                                androidResources,
                                cacheByPath,
                                cacheUpdates);

                ImmutableMap<RelativeFile, FileStatus> changedNLibs =
                        KnownFilesSaveData.getChangedInputs(
                                params.changedInputs,
                                saveData,
                                InputSet.NATIVE_RESOURCE,
                                params.jniFiles,
                                cacheByPath,
                                cacheUpdates);

                BuildElements manifestOutputs =
                        ExistingBuildElements.from(params.manifestType, params.manifestDirectory);

                doTask(
                        incrementalDirForSplit,
                        params.getOutput(),
                        cacheByPath,
                        manifestOutputs,
                        changedDexFiles,
                        changedJavaResources,
                        changedAssets,
                        changedAndroidResources,
                        changedNLibs,
                        params,
                        true);

                /*
                 * Update the cache
                 */
                cacheUpdates.forEach(Runnable::run);

                /*
                 * Update the save data keep files.
                 */
                ImmutableMap<RelativeFile, FileStatus> allDex =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(params.dexFiles);
                ImmutableMap<RelativeFile, FileStatus> allJavaResources =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(
                                params.javaResourceFiles);
                ImmutableMap<RelativeFile, FileStatus> allAssets =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(params.assetsFiles);
                ImmutableMap<RelativeFile, FileStatus> allAndroidResources =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
                ImmutableMap<RelativeFile, FileStatus> allJniResources =
                        IncrementalRelativeFileSets.fromZipsAndDirectories(params.jniFiles);

                saveData.setInputSet(allDex.keySet(), InputSet.DEX);
                saveData.setInputSet(allJavaResources.keySet(), InputSet.JAVA_RESOURCE);
                saveData.setInputSet(allAssets.keySet(), InputSet.ASSET);
                saveData.setInputSet(allAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
                saveData.setInputSet(allJniResources.keySet(), InputSet.NATIVE_RESOURCE);
                saveData.saveCurrentData();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class IncrementalSplitterParams extends SplitterParams {
        private final Map<File, FileStatus> changedInputs;

        IncrementalSplitterParams(
                @NonNull ApkData apkInfo,
                @NonNull File processedResources,
                Map<File, FileStatus> changedInputs,
                PackageAndroidArtifact task) {
            super(apkInfo, processedResources, task);
            this.changedInputs = changedInputs;
            this.instantRunBuildContext = task.instantRunContext.get();
        }
    }

    // ----- CreationAction -----

    public abstract static class CreationAction<T extends PackageAndroidArtifact>
            extends VariantTaskCreationAction<T> {

        protected final Project project;
        @NonNull protected final Provider<Directory> manifests;
        @NonNull protected final InternalArtifactType inputResourceFilesType;
        @NonNull protected final File outputDirectory;
        @NonNull protected final OutputScope outputScope;
        @Nullable private final FileCache fileCache;
        @NonNull private final InternalArtifactType manifestType;

        public CreationAction(
                @NonNull VariantScope variantScope,
                @NonNull File outputDirectory,
                @NonNull InternalArtifactType inputResourceFilesType,
                @NonNull Provider<Directory> manifests,
                @NonNull InternalArtifactType manifestType,
                @Nullable FileCache fileCache,
                @NonNull OutputScope outputScope) {
            super(variantScope);
            this.project = variantScope.getGlobalScope().getProject();
            this.inputResourceFilesType = inputResourceFilesType;
            this.manifests = manifests;
            this.outputDirectory = outputDirectory;
            this.outputScope = outputScope;
            this.manifestType = manifestType;
            this.fileCache = fileCache;
        }

        @Override
        public void configure(@NonNull final T packageAndroidArtifact) {
            super.configure(packageAndroidArtifact);
            VariantScope variantScope = getVariantScope();

            GlobalScope globalScope = variantScope.getGlobalScope();
            GradleVariantConfiguration variantConfiguration =
                    variantScope.getVariantConfiguration();

            packageAndroidArtifact.instantRunFileType = FileType.MAIN;
            packageAndroidArtifact.taskInputType = inputResourceFilesType;
            packageAndroidArtifact.minSdkVersion =
                    TaskInputHelper.memoize(variantScope::getMinSdkVersion);
            packageAndroidArtifact.instantRunContext =
                    TaskInputHelper.memoize(variantScope::getInstantRunBuildContext);

            packageAndroidArtifact.resourceFiles =
                    variantScope.getArtifacts().getFinalArtifactFiles(inputResourceFilesType);
            packageAndroidArtifact.outputDirectory = outputDirectory;
            packageAndroidArtifact.setIncrementalFolder(
                    new File(
                            variantScope.getIncrementalDir(packageAndroidArtifact.getName()),
                            "tmp"));
            packageAndroidArtifact.outputScope = outputScope;

            packageAndroidArtifact.fileCache = fileCache;
            packageAndroidArtifact.aaptOptionsNoCompress =
                    DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions())
                            .getNoCompress();

            packageAndroidArtifact.manifests = manifests;

            packageAndroidArtifact.dexFolders = getDexFolders();
            packageAndroidArtifact.featureDexFolder = getFeatureDexFolder();
            packageAndroidArtifact.javaResourceFiles = getJavaResources();

            packageAndroidArtifact.assets =
                    variantScope.getArtifacts().getFinalArtifactFiles(MERGED_ASSETS);
            packageAndroidArtifact.setAbiFilters(variantConfiguration.getSupportedAbis());
            packageAndroidArtifact.setJniDebugBuild(
                    variantConfiguration.getBuildType().isJniDebuggable());
            packageAndroidArtifact.setDebugBuild(
                    variantConfiguration.getBuildType().isDebuggable());

            ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();
            packageAndroidArtifact.projectBaseName = globalScope.getProjectBaseName();
            packageAndroidArtifact.manifestType = manifestType;
            packageAndroidArtifact.buildTargetAbi =
                    globalScope.getExtension().getSplits().getAbi().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                            : null;
            packageAndroidArtifact.buildTargetDensity =
                    globalScope.getExtension().getSplits().getDensity().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)
                            : null;

            packageAndroidArtifact.apkFormat =
                    projectOptions.get(BooleanOption.DEPLOYMENT_USES_DIRECTORY)
                            ? IncrementalPackagerBuilder.ApkFormat.DIRECTORY
                            : projectOptions.get(BooleanOption.DEPLOYMENT_PROVIDES_LIST_OF_CHANGES)
                                    ? IncrementalPackagerBuilder.ApkFormat.FILE_WITH_LIST_OF_CHANGES
                                    : IncrementalPackagerBuilder.ApkFormat.FILE;
            finalConfigure(packageAndroidArtifact);
        }

        protected void finalConfigure(T task) {
            VariantScope variantScope = getVariantScope();

            GlobalScope globalScope = variantScope.getGlobalScope();
            GradleVariantConfiguration variantConfiguration =
                    variantScope.getVariantConfiguration();
            task.instantRunFileType = FileType.MAIN;

            task.dexFolders = getDexFolders();
            task.featureDexFolder = getFeatureDexFolder();
            task.javaResourceFiles = getJavaResources();

            if (variantScope.getVariantData().getMultiOutputPolicy()
                    == MultiOutputPolicy.MULTI_APK) {
                task.jniFolders = getJniFolders();
            } else {
                Set<String> filters =
                        AbiSplitOptions.getAbiFilters(
                                globalScope.getExtension().getSplits().getAbiFilters());

                task.jniFolders = filters.isEmpty() ? getJniFolders() : project.files();
            }

            task.apkList =
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.APK_LIST);

            task.setSigningConfig(variantScope.getSigningConfigFileCollection());
        }

        @NonNull
        public FileCollection getDexFolders() {
            return getVariantScope()
                    .getTransformManager()
                    .getPipelineOutputAsFileCollection(StreamFilter.DEX);
        }

        @NonNull
        public FileCollection getJavaResources() {
            return getVariantScope()
                    .getTransformManager()
                    .getPipelineOutputAsFileCollection(StreamFilter.RESOURCES);
        }

        @NonNull
        public FileCollection getJniFolders() {
            return getVariantScope()
                    .getTransformManager()
                    .getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS);
        }

        @Nullable
        public FileCollection getFeatureDexFolder() {
            if (!getVariantScope().getType().isFeatureSplit()) {
                return null;
            }
            return getVariantScope()
                    .getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.FEATURE_DEX,
                            ImmutableMap.of(MODULE_PATH, project.getPath()));
        }
    }
}
