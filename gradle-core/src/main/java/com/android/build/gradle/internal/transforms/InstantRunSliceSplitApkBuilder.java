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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.tasks.SigningConfigMetadata;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

/**
 * Tasks to generate M+ style pure splits APKs with dex files.
 */
public class InstantRunSliceSplitApkBuilder extends InstantRunSplitApkBuilder {

    private final WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

    private final BuildableArtifact splitApkResources;

    public InstantRunSliceSplitApkBuilder(
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
            @NonNull BuildableArtifact splitApkResourceFiles,
            @NonNull ApkData mainApk) {
        super(
                logger,
                project,
                buildContext,
                androidBuilder,
                aapt2FromMaven,
                applicationIdSupplier,
                signingConf,
                aaptOptions,
                outputDirectory,
                supportDirectory,
                resources,
                resourcesWithMainManifest,
                apkList,
                mainApk);
        this.splitApkResources = splitApkResourceFiles;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> list = ImmutableList.builder();
        list.addAll(super.getSecondaryFiles());
        list.add(SecondaryFile.incremental(splitApkResources.get()));
        return list.build();
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunSlicesApk";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.SUB_PROJECTS);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {

        // this will hold the list of split APKs to build.
        List<DexFiles> splitsToBuild = new ArrayList<>();
        if (transformInvocation.isIncremental()) {
            for (TransformInput transformInput : transformInvocation.getInputs()) {
                for (JarInput jarInput : transformInput.getJarInputs()) {
                    logger.error("InstantRunDependenciesApkBuilder received a jar file "
                            + jarInput.getFile().getAbsolutePath());
                }

                for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                    Map<File, Set<Status>> sliceStatuses =
                            directoryInput
                                    .getChangedFiles()
                                    .entrySet()
                                    .stream()
                                    .collect(
                                            Collectors.groupingBy(
                                                    fileStatus ->
                                                            fileStatus.getKey().getParentFile(),
                                                    Collectors.mapping(
                                                            Map.Entry::getValue,
                                                            Collectors.toSet())));

                    for (Map.Entry<File, Set<Status>> slices : sliceStatuses.entrySet()) {
                        if (slices.getValue().equals(EnumSet.of(Status.REMOVED))) {
                            DexFiles dexFile =
                                    new DexFiles(ImmutableSet.of(), slices.getKey().getName());

                            String outputFileName = dexFile.encodeName() + "_unaligned.apk";
                            FileUtils.deleteIfExists(new File(outputDirectory, outputFileName));
                            outputFileName = dexFile.encodeName() + ".apk";
                            FileUtils.deleteIfExists(new File(outputDirectory, outputFileName));
                            break;
                        } else if (!slices.getValue().equals(EnumSet.of(Status.NOTCHANGED))) {
                            File[] dexFiles = slices.getKey().listFiles();
                            if (dexFiles != null) {
                                try {
                                    splitsToBuild.add(
                                            new DexFiles(dexFiles, directoryInput.getName()));
                                } catch (Exception e) {
                                    throw new TransformException(e);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } else {
            FileUtils.cleanOutputDir(outputDirectory);
            for (TransformInput transformInput : transformInvocation.getInputs()) {
                for (JarInput jarInput : transformInput.getJarInputs()) {
                    logger.error("InstantRunDependenciesApkBuilder received a jar file "
                            + jarInput.getFile().getAbsolutePath());
                }
                for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                    File[] files = directoryInput.getFile().listFiles();
                    if (files == null) {
                        continue;
                    }
                    try {
                        splitsToBuild.add(
                                new DexFiles(ImmutableSet.copyOf(files), directoryInput.getName()));
                    } catch (Exception e) {
                        throw new TransformException(e);
                    }
                }
            }
        }

        // now build the APKs in parallel
        splitsToBuild.forEach(
                split -> {
                    try {
                        executor.execute(
                                () -> {
                                    String uniqueName = split.encodeName();
                                    final File alignedOutput =
                                            new File(outputDirectory, uniqueName + ".apk");
                                    Files.createParentDirs(alignedOutput);

                                    File splitApkResources =
                                            Iterators.getOnlyElement(
                                                    this.splitApkResources.getFiles().iterator());
                                    File splitApkResource = new File(splitApkResources, uniqueName);

                                    File resPackageFile =
                                            new File(splitApkResource, "resources_ap");

                                    if (!resPackageFile.exists()) {
                                        throw new FileNotFoundException(
                                                resPackageFile.getAbsolutePath());
                                    }
                                    generateSplitApk(
                                            uniqueName, resPackageFile, split, alignedOutput);

                                    buildContext.addChangedFile(FileType.SPLIT, alignedOutput);

                                    return alignedOutput;
                                });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        executor.waitForTasksWithQuickFail(true /* cancelRemaining */);
    }

    void generateSplitApk(String uniqueName, File resPackageFile, DexFiles split, File outputFile)
            throws TransformException, KeytoolException, IOException, PackagerException {

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
                split.getDexFiles(),
                SigningConfigMetadata.Companion.load(signingConf),
                outputFile,
                tempDir,
                ApkCreatorFactories.fromProjectProperties(project, true));
    }
}
