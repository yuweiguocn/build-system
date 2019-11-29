/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.ArtifactType;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Supplier of {@link BuildOutput} for built artifacts. */
public class BuildOutputsSupplier implements BuildOutputSupplier<Collection<EarlySyncBuildOutput>> {

    @NonNull private final List<File> outputFolders;
    @NonNull private final List<ArtifactType> outputTypes;

    public BuildOutputsSupplier(
            @NonNull List<ArtifactType> outputTypes, @NonNull List<File> outputFolders) {
        this.outputFolders = outputFolders;
        this.outputTypes = outputTypes;
    }

    @Override
    @NonNull
    public Collection<EarlySyncBuildOutput> get() {
        ImmutableList.Builder<EarlySyncBuildOutput> outputs = ImmutableList.builder();
        outputFolders.forEach(
                outputFolder -> {
                    if (!outputFolder.exists()) {
                        return;
                    }
                    Collection<EarlySyncBuildOutput> previous =
                            EarlySyncBuildOutput.load(outputFolder)
                                    .stream()
                                    .filter(
                                            buildOutput ->
                                                    outputTypes.contains(buildOutput.getType()))
                                    .collect(Collectors.toList());

                    if (previous.isEmpty()) {
                        outputTypes.forEach(
                                taskOutputType -> {
                                    // take the FileCollection content as face value.
                                    // FIX ME : we should do better than this, maybe make sure output.gson
                                    // is always produced for those items.
                                    File[] files = outputFolder.listFiles();
                                    if (files != null && files.length > 0) {
                                        for (File file : files) {
                                            processFile(taskOutputType, file, outputs);
                                        }
                                    }
                                });
                    } else {
                        outputs.addAll(previous);
                    }
                });
        return outputs.build();
    }

    @Override
    public File guessOutputFile(String relativeFileName) {
        return outputFolders.isEmpty()
                ? new File(relativeFileName)
                : new File(outputFolders.get(0), relativeFileName);
    }

    private static void processFile(
            ArtifactType taskOutputType,
            File file,
            ImmutableList.Builder<EarlySyncBuildOutput> outputs) {
        if (taskOutputType == InternalArtifactType.MERGED_MANIFESTS) {
            if (file.getName().equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                outputs.add(
                        new EarlySyncBuildOutput(
                                taskOutputType,
                                VariantOutput.OutputType.MAIN,
                                ImmutableList.of(),
                                0,
                                file));
            }
        } else {
            VariantOutput.OutputType fileOutputType =
                    taskOutputType == InternalArtifactType.AAR
                                    || taskOutputType == InternalArtifactType.APK
                            ? VariantOutput.OutputType.MAIN
                            : VariantOutput.OutputType.SPLIT;
            outputs.add(
                    new EarlySyncBuildOutput(
                            taskOutputType, fileOutputType, ImmutableList.of(), 0, file));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(outputFolders, outputTypes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildOutputsSupplier that = (BuildOutputsSupplier) o;
        return Objects.equals(outputFolders, that.outputFolders)
                && Objects.equals(outputTypes, that.outputTypes);
    }
}
