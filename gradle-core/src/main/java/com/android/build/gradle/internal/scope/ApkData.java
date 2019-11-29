/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * FIX ME : wrong name but convenient until I can clean up existing classes/namespace.
 *
 * <p>This split represents a Variant output, which can be a main (base) split, a full split, a
 * configuration pure splits. Each variant has one to many of such outputs depending on its
 * configuration.
 *
 * <p>this is used to model outputs of a variant during configuration and it is sometimes altered at
 * execution when new pure splits are discovered.
 */
public abstract class ApkData implements VariantOutput, Comparable<ApkData>, Serializable {

    private static final Comparator<ApkData> COMPARATOR =
            Comparator.nullsLast(
                    Comparator.comparing(ApkData::getType)
                            .thenComparingInt(ApkData::getVersionCode)
                            .thenComparing(
                                    ApkData::getOutputFileName,
                                    Comparator.nullsLast(String::compareTo))
                            .thenComparing(
                                    ApkData::getVersionName,
                                    Comparator.nullsLast(String::compareTo))
                            .thenComparing(ApkData::isEnabled));

    // TODO : move it to a subclass, we cannot override versions for SPLIT
    private transient Supplier<String> versionName = () -> null;
    private transient IntSupplier versionCode = () -> 0;
    private AtomicBoolean enabled = new AtomicBoolean(true);
    private String outputFileName;


    public ApkData() {}

    public static ApkData of(
            @NonNull OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode) {
        return of(outputType, filters, versionCode, null, null, null, "", "", true);
    }

    public static ApkData of(
            @NonNull OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode,
            @Nullable String versionName,
            @Nullable String filterName,
            @Nullable String outputFileName,
            @NonNull String fullName,
            @NonNull String baseName,
            boolean enabled) {
        return new DefaultApkData(
                outputType,
                filters,
                versionCode,
                versionName,
                filterName,
                outputFileName,
                fullName,
                baseName,
                enabled);
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return getFilters().stream().map(FilterData::getFilterType).collect(Collectors.toList());
    }

    // FIX-ME: we can have more than one value, especially for languages...
    // so far, we will return things like "fr,fr-rCA" for a single value.
    @Nullable
    public FilterData getFilter(@NonNull FilterType filterType) {
        for (FilterData filter : getFilters()) {
            if (VariantOutput.FilterType.valueOf(filter.getFilterType()) == filterType) {
                return filter;
            }
        }
        return null;
    }

    @Nullable
    public String getFilter(String filterType) {
        return ApkData.getFilter(getFilters(), FilterType.valueOf(filterType));
    }

    public boolean requiresAapt() {
        return true;
    }

    @NonNull
    public abstract String getBaseName();

    @NonNull
    public abstract String getFullName();

    @NonNull
    public abstract OutputType getType();

    /**
     * Returns a directory name relative to a variant specific location to save split specific
     * output files or null to use the variant specific folder.
     *
     * @return a directory name of null.
     */
    @NonNull
    public abstract String getDirName();

    public void setVersionCode(IntSupplier versionCode) {
        this.versionCode = versionCode;
    }

    public void setVersionName(Supplier<String> versionName) {
        this.versionName = versionName;
    }

    public void setOutputFileName(@NonNull String outputFileName) {
        this.outputFileName = outputFileName;
    }

    @Override
    public int getVersionCode() {
        return versionCode.getAsInt();
    }

    @Nullable
    public String getVersionName() {
        return versionName.get();
    }

    @Nullable
    public String getOutputFileName() {
        return outputFileName;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", getType())
                .add("fullName", getFullName())
                .add("filters", getFilters())
                .add("versionCode", getVersionCode())
                .add("versionName", getVersionName())
                .toString();
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        throw new UnsupportedOperationException(
                "getMainOutputFile is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @NonNull
    @Override
    public Collection<? extends OutputFile> getOutputs() {
        throw new UnsupportedOperationException(
                "getOutputs is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @NonNull
    @Override
    public String getOutputType() {
        return getType().name();
    }

    // FIX-ME: we can have more than one value, especially for languages...
    // so far, we will return things like "fr,fr-rCA" for a single value.
    @Nullable
    public static String getFilter(
            Collection<FilterData> filters, OutputFile.FilterType filterType) {

        for (FilterData filter : filters) {
            if (VariantOutput.FilterType.valueOf(filter.getFilterType()) == filterType) {
                return filter.getIdentifier();
            }
        }
        return null;
    }

    public void disable() {
        enabled.set(false);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApkData that = (ApkData) o;
        return getVersionCode() == that.getVersionCode()
                && Objects.equals(outputFileName, that.outputFileName)
                && Objects.equals(getVersionName(), that.getVersionName())
                && Objects.equals(enabled.get(), that.enabled.get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getVersionCode(), enabled.get(), getVersionName(), outputFileName);
    }

    @Override
    public int compareTo(ApkData other) {
        return COMPARATOR.compare(this, other);
    }

    @Nullable
    public abstract String getFilterName();


    // We use this since we cannot serialize the suppliers for the respective fields, so we serialize
    // a "snapshot" of those value sand when the object is deserialized we use these fields to create
    // new suppliers.
    // Although now the suppliers will be static, it's expected that by the time the class was
    // serialized those values shouldn't change anymore.
    private String serializedVersionName = null;
    private int serializedVersionCode = 0;

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        serializedVersionName = versionName.get();
        serializedVersionCode = versionCode.getAsInt();
        out.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        versionName = () -> serializedVersionName;
        versionCode = () -> serializedVersionCode;
    }

}
