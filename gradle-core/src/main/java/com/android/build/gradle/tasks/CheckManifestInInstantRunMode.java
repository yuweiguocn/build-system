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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.gradle.api.file.Directory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

/** Checks that the manifest file has not changed since the last instant run build. */
public class CheckManifestInInstantRunMode extends AndroidVariantTask {

    private static final Logger LOG = Logging.getLogger(CheckManifestInInstantRunMode.class);

    private InstantRunBuildContext buildContext;
    private File manifestCheckerDir;
    private Provider<Directory> instantRunManifests;
    private BuildableArtifact processedRes;
    private InternalArtifactType resInputType;

    @Input
    public InternalArtifactType getResourcesInputType() {
        return resInputType;
    }

    @InputFiles
    public Provider<Directory> getInstantRunManifests() {
        return instantRunManifests;
    }

    @InputFiles
    public BuildableArtifact getProcessedRes() {
        return processedRes;
    }

    @TaskAction
    public void checkManifestChanges() throws IOException {

        // If we are NOT instant run mode, this is an error, this task should not be running.
        if (!buildContext.isInInstantRunMode() || !instantRunManifests.isPresent()) {
            LOG.warn("CheckManifestInInstantRunMode configured in non instant run build,"
                    + " please file a bug.");
            return;
        }
        File manifestsFolder = instantRunManifests.get().getAsFile();

        if (!manifestsFolder.exists()) {
            String message =
                    "No instant run specific merged manifests in InstantRun mode, "
                            + "please file a bug and disable InstantRun.";
            LOG.error(message);
            throw new RuntimeException(message);
        }

        // always do both, we should make sure that we are not keeping stale data for the previous
        // instance.
        // Cannot call .getLastValue() since it is not declared as an Input which
        // would call .get() before the task run.

        BuildElements processedResOutputs = ExistingBuildElements.from(resInputType, processedRes);

        BuildElements buildOutputs =
                ExistingBuildElements.from(
                        InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS, manifestsFolder);

        if (buildOutputs.size() > 1) {
            String message =
                    "Full Split are not supported in InstantRun mode, "
                            + "please disable InstantRun";
            LOG.error(message);
            throw new RuntimeException(message);
        }

        for (BuildOutput buildOutput : buildOutputs) {
            ApkData apkInfo = buildOutput.getApkData();
            File mergedManifest = buildOutput.getOutputFile();

            LOG.info("CheckManifestInInstantRunMode : Merged manifest %1$s", mergedManifest);
            runManifestChangeVerifier(buildContext, manifestCheckerDir, mergedManifest);

            // Change THIS to not assume MAIN, time to add some API to the split scope that will
            // get MAIN, or UNIVERSAL or in case of FULL SPLIT, use the commented code to select
            // the right one. then change the code above to use the same logic to get the manifest
            // file.
            BuildOutput processedResOutput = processedResOutputs.element(apkInfo);
            if (processedResOutput == null) {
                throw new RuntimeException(
                        "Cannot find processed resources for "
                                + apkInfo
                                + " split in "
                                + Joiner.on(",").join(processedResOutputs.getElements()));
            }
            File resourcesApk = processedResOutput.getOutputFile();

            // Cannot call .getLastValue() since it is not declared as an Input which
            // would call .get() before the task run.
            LOG.info("CheckManifestInInstantRunMode : Resource APK %1$s", resourcesApk);
            if (resourcesApk.exists()) {
                runManifestBinaryChangeVerifier(buildContext, manifestCheckerDir, resourcesApk);
            }
        }
    }

    @VisibleForTesting
    static void runManifestChangeVerifier(
            InstantRunBuildContext buildContext,
            File instantRunSupportDir,
            @NonNull File manifestFileToPackage)
            throws IOException {
        File previousManifestFile = new File(instantRunSupportDir, "manifest.xml");

        if (previousManifestFile.exists()) {
            String currentManifest =
                    Files.asCharSource(manifestFileToPackage, Charsets.UTF_8).read();
            String previousManifest =
                    Files.asCharSource(previousManifestFile, Charsets.UTF_8).read();
            if (!currentManifest.equals(previousManifest)) {
                // TODO: Deeper comparison, call out just a version change.
                buildContext.setVerifierStatus(
                        InstantRunVerifierStatus.MANIFEST_FILE_CHANGE);
                Files.copy(manifestFileToPackage, previousManifestFile);
            }
        } else {
            Files.createParentDirs(previousManifestFile);
            Files.copy(manifestFileToPackage, previousManifestFile);
            // we don't have a back up of the manifest file, better be safe and force the APK build.
            buildContext.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
        }
    }

    @VisibleForTesting
    static void runManifestBinaryChangeVerifier(
            InstantRunBuildContext buildContext,
            File instantRunSupportDir,
            @NonNull File resOutBaseNameFile)
            throws IOException {
        // get the new manifest file CRC
        String currentIterationCRC = null;
        try (JarFile jarFile = new JarFile(resOutBaseNameFile)) {
            ZipEntry entry = jarFile.getEntry(SdkConstants.ANDROID_MANIFEST_XML);
            if (entry != null) {
                currentIterationCRC = String.valueOf(entry.getCrc());
            }
        }

        File crcFile = new File(instantRunSupportDir, "manifest.crc");
        // check the manifest file binary format.
        if (crcFile.exists() && currentIterationCRC != null) {
            // compare its content with the new binary file crc.
            String previousIterationCRC =
                    Files.asCharSource(crcFile, Charsets.UTF_8).readFirstLine();
            if (!currentIterationCRC.equals(previousIterationCRC)) {
                buildContext.setVerifierStatus(
                        InstantRunVerifierStatus.BINARY_MANIFEST_FILE_CHANGE);
            }
        } else {
            // we don't have a back up of the crc file, better be safe and force the APK build.
            buildContext.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
        }

        if (currentIterationCRC != null) {
            // write the new manifest file CRC.
            Files.createParentDirs(crcFile);
            Files.asCharSink(crcFile, Charsets.UTF_8).write(currentIterationCRC);
        }
    }

    public static class CreationAction extends TaskCreationAction<CheckManifestInInstantRunMode> {

        @NonNull protected final VariantScope variantScope;

        public CreationAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("checkManifestChanges");
        }

        @NonNull
        @Override
        public Class<CheckManifestInInstantRunMode> getType() {
            return CheckManifestInInstantRunMode.class;
        }

        @Override
        public void configure(@NonNull CheckManifestInInstantRunMode task) {

            task.instantRunManifests =
                    variantScope
                            .getArtifacts()
                            .getFinalProduct(InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS);
            task.resInputType =
                    variantScope.getInstantRunBuildContext().useSeparateApkForResources()
                            ? InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES
                            : InternalArtifactType.PROCESSED_RES;
            task.processedRes =
                    variantScope.getArtifacts().getFinalArtifactFiles(task.resInputType);
            task.buildContext = variantScope.getInstantRunBuildContext();
            task.manifestCheckerDir = variantScope.getManifestCheckerDir();
            task.setVariantName(variantScope.getFullVariantName());
        }
    }
}
