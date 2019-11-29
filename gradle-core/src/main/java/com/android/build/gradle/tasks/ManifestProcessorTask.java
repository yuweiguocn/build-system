/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.io.File;
import java.util.Map;
import javax.annotation.Nonnull;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * A task that processes the manifest
 */
public abstract class ManifestProcessorTask extends IncrementalTask {

    public ManifestProcessorTask(@NonNull ObjectFactory objectFactory) {
        manifestOutputDirectory = objectFactory.directoryProperty();
        instantAppManifestOutputDirectory = objectFactory.directoryProperty();
        instantRunManifestOutputDirectory = objectFactory.directoryProperty();
        aaptFriendlyManifestOutputDirectory = objectFactory.directoryProperty();
    }

    @SuppressWarnings("unused")
    @Nonnull
    private final Provider<Directory> manifestOutputDirectory;

    @Nonnull private final Provider<Directory> aaptFriendlyManifestOutputDirectory;

    @Nonnull private final Provider<Directory> instantRunManifestOutputDirectory;

    private File metadataFeatureManifestOutputDirectory;

    private File bundleManifestOutputDirectory;

    @Nonnull private final Provider<Directory> instantAppManifestOutputDirectory;

    private File reportFile;

    @SuppressWarnings("unused")
    protected BuildableArtifact checkManifestResult;

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public BuildableArtifact getCheckManifestResult() {
        return checkManifestResult;
    }

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @Nullable
    @Internal
    public abstract File getAaptFriendlyManifestOutputFile();

    /** The processed Manifests files folder. */
    @NonNull
    @OutputDirectory
    public Provider<Directory> getManifestOutputDirectory() {
        return manifestOutputDirectory;
    }

    @OutputDirectory
    @Optional
    @NonNull
    public Provider<Directory> getInstantRunManifestOutputDirectory() {
        return instantRunManifestOutputDirectory;
    }

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @OutputDirectory
    @Optional
    @NonNull
    public Provider<Directory> getAaptFriendlyManifestOutputDirectory() {
        return aaptFriendlyManifestOutputDirectory;
    }

    /**
     * The bundle manifest which is consumed by the bundletool (as opposed to the one packaged with
     * the apk when built directly).
     */
    @OutputDirectory
    @Optional
    public File getBundleManifestOutputDirectory() {
        return bundleManifestOutputDirectory;
    }

    protected void setBundleManifestOutputDirectory(File bundleManifestOutputDirectory) {
        this.bundleManifestOutputDirectory = bundleManifestOutputDirectory;
    }

    /**
     * The feature manifest which is consumed by its base feature (as opposed to the one packaged
     * with the feature APK). This manifest, unlike the one packaged with the APK, does not specify
     * a minSdkVersion. This is used by by both normal features and dynamic-features.
     */
    @OutputDirectory
    @Optional
    public File getMetadataFeatureManifestOutputDirectory() {
        return metadataFeatureManifestOutputDirectory;
    }

    protected void setMetadataFeatureManifestOutputDirectory(
            File metadataFeatureManifestOutputDirectory) {
        this.metadataFeatureManifestOutputDirectory = metadataFeatureManifestOutputDirectory;
    }

    /** The instant app manifest which is used if we are deploying the app as an instant app. */
    @OutputDirectory
    @Optional
    @NonNull
    public Provider<Directory> getInstantAppManifestOutputDirectory() {
        return instantAppManifestOutputDirectory;
    }

    @OutputFile
    @Optional
    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }


    /**
     * Serialize a map key+value pairs into a comma separated list. Map elements are sorted to
     * ensure stability between instances.
     *
     * @param mapToSerialize the map to serialize.
     */
    protected static String serializeMap(Map<String, Object> mapToSerialize) {
        final Joiner keyValueJoiner = Joiner.on(":");
        // transform the map on a list of key:value items, sort it and concatenate it.
        return Joiner.on(",").join(
                Ordering.natural().sortedCopy(Iterables.transform(
                        mapToSerialize.entrySet(),
                        (input) -> keyValueJoiner.join(input.getKey(), input.getValue()))));
    }
}
