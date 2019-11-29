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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Information about expected Outputs from a build.
 *
 * <p>This will either contain:
 *
 * <ul>
 *   <li>A single main APK
 *   <li>Multiple full APKs when the {@code multiOutputPolicy} is {@link
 *       MultiOutputPolicy#MULTI_APK}
 *   <li>A single main APK with multiple split APKs (when the {@code multiOutputPolicy} is {@link
 *       MultiOutputPolicy#SPLITS}
 * </ul>
 */
public class OutputScope implements Serializable {

    @NonNull private final ImmutableList<ApkData> sortedApkDatas;

    private OutputScope(@NonNull ImmutableList<ApkData> sortedApkDatas) {
        this.sortedApkDatas = sortedApkDatas;
    }

    /**
     * Returns the enabled splits for this variant. A split can be disabled due to build
     * optimization.
     *
     * @return list of splits to process for this variant.
     */
    @NonNull
    public List<ApkData> getApkDatas() {
        return sortedApkDatas.stream().filter(ApkData::isEnabled).collect(Collectors.toList());
    }

    @NonNull
    public ApkData getMainSplit() {

        // no ABI specified, look for the main split.
        Optional<ApkData> splitsByType =
                sortedApkDatas
                        .stream()
                        .filter(apkInfo -> apkInfo.getType() == OutputFile.OutputType.MAIN)
                        .findFirst();

        if (splitsByType.isPresent()) {
            return splitsByType.get();
        }
        // can't find the main split, look for the universal full split.
        Optional<ApkData> universal =
                getApkDatas()
                        .stream()
                        .filter(split -> OutputFactory.UNIVERSAL.equals(split.getFilterName()))
                        .findFirst();
        if (universal.isPresent()) {
            return universal.get();
        }
        // ok look for the first enabled full split, it will do.
        Optional<ApkData> firstFullSplit =
                getApkDatas()
                        .stream()
                        .filter(split -> split.getType() == OutputFile.OutputType.FULL_SPLIT)
                        .findFirst();
        if (firstFullSplit.isPresent()) {
            return firstFullSplit.get();
        }
        throw new RuntimeException(
                String.format(
                        "Cannot determine main APK output from %1$s",
                        Joiner.on(":").join(sortedApkDatas)));
    }

    @NonNull
    public List<ApkData> getSplitsByType(OutputFile.OutputType outputType) {
        return sortedApkDatas
                .stream()
                .filter(split -> split.getType() == outputType)
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        OutputScope that = (OutputScope) o;
        return Objects.equals(sortedApkDatas, that.sortedApkDatas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sortedApkDatas);
    }

    public static class Builder {
        @NonNull private final List<ApkData> apkDatas = new ArrayList<>();

        public void addSplit(@NonNull ApkData apkData) {
            apkDatas.add(apkData);
        }

        public void addMainSplit(@NonNull ApkData apkData) {
            if (hasMainSplits()) {
                throw new RuntimeException(
                        "Cannot add "
                                + apkData
                                + " in a scope that already"
                                + " has "
                                + apkDatas.stream()
                                        .filter(
                                                output ->
                                                        output.getType()
                                                                == VariantOutput.OutputType.MAIN)
                                        .map(ApkData::toString)
                                        .collect(Collectors.joining(",")));
            }
            addSplit(apkData);
        }

        private boolean hasMainSplits() {
            return apkDatas.stream().anyMatch(s -> s.getType() == VariantOutput.OutputType.MAIN);
        }

        public OutputScope build() {
            return new OutputScope(ImmutableList.sortedCopyOf(apkDatas));
        }
    }
}
