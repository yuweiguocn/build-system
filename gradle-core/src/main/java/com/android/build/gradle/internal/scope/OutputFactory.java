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

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.android.utils.Pair;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Factory for {@link ApkData} instances. Cannot be stored in any model related objects. */
public class OutputFactory {

    static final String UNIVERSAL = "universal";

    private final String projectBaseName;
    private final GradleVariantConfiguration variantConfiguration;
    private final OutputScope.Builder outputScopeBuilder;
    private final Supplier<OutputScope> outputSupplier;

    public OutputFactory(String projectBaseName, GradleVariantConfiguration variantConfiguration) {
        this.projectBaseName = projectBaseName;
        this.variantConfiguration = variantConfiguration;
        this.outputScopeBuilder = new OutputScope.Builder();
        this.outputSupplier = Suppliers.memoize(outputScopeBuilder::build);
    }

    private String getOutputFileName(String baseName) {
        // we only know if it is signed during configuration, if its the base module.
        // Otherwise, don't differentiate between signed and unsigned.
        String suffix =
                (variantConfiguration.isSigningReady()
                                || !variantConfiguration.getType().isBaseModule())
                        ? DOT_ANDROID_PACKAGE
                        : "-unsigned.apk";
        return projectBaseName + "-" + baseName + suffix;
    }

    public ApkData addMainOutput(String defaultFilename) {
        String baseName = variantConfiguration.getBaseName();
        ApkData mainOutput =
                // the main output basename comes from the variant configuration.
                // the main output should not have a dirName set as all the getXXXOutputDirectory
                // in variant scope already include the variant name.
                // TODO : we probably should clean this up, having the getXXXOutputDirectory APIs
                // return the top level folder and have all users use the getDirName() as part of
                // the task output folder configuration.
                new Main(baseName, variantConfiguration.getFullName(), "", defaultFilename);
        outputScopeBuilder.addMainSplit(mainOutput);
        return mainOutput;
    }

    public ApkData addMainApk() {
        return addMainOutput(getOutputFileName(variantConfiguration.getBaseName()));
    }

    public ApkData addUniversalApk() {

        String baseName = variantConfiguration.computeBaseNameWithSplits(UNIVERSAL);
        ApkData mainApk =
                new Universal(
                        baseName,
                        variantConfiguration.computeFullNameWithSplits(UNIVERSAL),
                        getOutputFileName(baseName));
        outputScopeBuilder.addMainSplit(mainApk);
        return mainApk;
    }

    public ApkData addFullSplit(ImmutableList<Pair<OutputFile.FilterType, String>> filters) {
        ImmutableList<FilterData> filtersList =
                ImmutableList.copyOf(
                        filters.stream()
                                .map(
                                        filter ->
                                                new FilterDataImpl(
                                                        filter.getFirst(), filter.getSecond()))
                                .collect(Collectors.toList()));
        String filterName = FullSplit._getFilterName(filtersList);
        String baseName = variantConfiguration.computeBaseNameWithSplits(filterName);
        ApkData apkData =
                new FullSplit(
                        filterName,
                        baseName,
                        variantConfiguration.computeFullNameWithSplits(filterName),
                        getOutputFileName(baseName),
                        filtersList);
        outputScopeBuilder.addSplit(apkData);
        return apkData;
    }

    public ApkData addConfigurationSplit(OutputFile.FilterType filterType, String filterValue) {
        ImmutableList<FilterData> filtersList =
                ImmutableList.of(new FilterDataImpl(filterType, filterValue));
        String filterName = getFilterNameForSplits(filtersList);
        String baseName = variantConfiguration.computeBaseNameWithSplits(filterName);
        return addConfigurationSplit(filtersList, getOutputFileName(baseName), filterValue);
    }

    public ApkData addConfigurationSplit(
            OutputFile.FilterType filterType, String filterValue, String fileName) {
        ImmutableList<FilterData> filtersList =
                ImmutableList.of(new FilterDataImpl(filterType, filterValue));
        String filterDisplayName = getFilterNameForSplits(filtersList);
        return addConfigurationSplit(filtersList, fileName, filterDisplayName);
    }

    @NonNull
    public static String getFilterNameForSplits(Collection<FilterData> filters) {
        return Joiner.on("-")
                .join(filters.stream().map(FilterData::getIdentifier).collect(Collectors.toList()));
    }

    private ApkData addConfigurationSplit(
            ImmutableList<FilterData> filtersList, String fileName, String filterDisplayName) {
        ApkData apkData =
                new ConfigurationSplitApkData(
                        filterDisplayName,
                        variantConfiguration.computeBaseNameWithSplits(filterDisplayName),
                        variantConfiguration.getFullName(),
                        variantConfiguration.getDirName(),
                        fileName,
                        filtersList);
        outputScopeBuilder.addSplit(apkData);
        return apkData;
    }

    public OutputScope getOutput() {
        return outputSupplier.get();
    }

    private static final class Main extends ApkData {

        private final String baseName, fullName, dirName;

        private Main(String baseName, String fullName, String dirName, String fileName) {
            this.baseName = baseName;
            this.fullName = fullName;
            this.dirName = dirName;
            setOutputFileName(fileName);
        }

        @NonNull
        @Override
        public OutputType getType() {
            return OutputType.MAIN;
        }

        @Nullable
        @Override
        public String getFilterName() {
            return null;
        }

        @NonNull
        @Override
        public String getBaseName() {
            return baseName;
        }

        @NonNull
        @Override
        public String getFullName() {
            return fullName;
        }

        @NonNull
        @Override
        public String getDirName() {
            return dirName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), baseName, fullName, dirName);
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
            Main that = (Main) o;
            return Objects.equals(baseName, that.baseName)
                    && Objects.equals(fullName, that.fullName)
                    && Objects.equals(dirName, that.dirName);
        }
    }

    private static class Universal extends ApkData {
        private final String baseName, fullName;

        private Universal(String baseName, String fullName, String fileName) {
            this.baseName = baseName;
            this.fullName = fullName;
            setOutputFileName(fileName);
        }

        @NonNull
        @Override
        public OutputType getType() {
            return OutputType.FULL_SPLIT;
        }

        @Nullable
        @Override
        public String getFilterName() {
            return UNIVERSAL;
        }

        @NonNull
        @Override
        public String getBaseName() {
            return baseName;
        }

        @NonNull
        @Override
        public String getFullName() {
            return fullName;
        }

        @NonNull
        @Override
        public String getDirName() {
            if (getFilters().isEmpty()) {
                return UNIVERSAL;
            }
            StringBuilder sb = new StringBuilder();
            for (FilterData filter : getFilters()) {
                sb.append(filter.getIdentifier()).append(File.separatorChar);
            }
            return sb.toString();
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
            Universal that = (Universal) o;
            return Objects.equals(baseName, that.baseName)
                    && Objects.equals(fullName, that.fullName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), baseName, fullName);
        }
    }

    static class FullSplit extends Universal {
        private final ImmutableList<FilterData> filters;
        private final String filterName;

        private FullSplit(
                String filterName,
                String baseName,
                String fullName,
                String fileName,
                ImmutableList<FilterData> filters) {
            super(baseName, fullName, fileName);
            this.filterName = filterName;
            this.filters = filters;
        }

        private static String _getFilterName(ImmutableList<FilterData> filters) {
            StringBuilder sb = new StringBuilder();
            String densityFilter = ApkData.getFilter(filters, FilterType.DENSITY);
            if (densityFilter != null) {
                sb.append(densityFilter);
            }
            String abiFilter = getFilter(filters, FilterType.ABI);
            if (abiFilter != null) {
                StringHelper.appendCamelCase(sb, abiFilter);
            }
            return sb.toString();
        }

        @NonNull
        @Override
        public OutputType getType() {
            return OutputType.FULL_SPLIT;
        }

        @NonNull
        @Override
        public ImmutableList<FilterData> getFilters() {
            return filters;
        }

        @Nullable
        @Override
        public String getFilterName() {
            return filterName;
        }

        @NonNull
        @Override
        public String getDirName() {
            StringBuilder sb = new StringBuilder();
            for (FilterData filter : getFilters()) {
                sb.append(filter.getIdentifier()).append(File.separatorChar);
            }
            return sb.toString();
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
            FullSplit that = (FullSplit) o;
            return Objects.equals(filterName, that.filterName)
                    && Objects.equals(filters, that.filters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), filterName, filters);
        }
    }

    public static class ConfigurationSplitApkData extends ApkData {

        @NonNull private final String filterName, baseName, fullName, dirName;
        private final ImmutableList<FilterData> filters;

        public ConfigurationSplitApkData(
                @NonNull String filterName,
                @NonNull String baseName,
                @NonNull String fullName,
                @NonNull String dirName,
                String fileName,
                ImmutableList<FilterData> filters) {
            this.filters = filters;
            this.filterName = filterName;
            this.baseName = baseName;
            this.fullName = fullName;
            this.dirName = dirName;
            setOutputFileName(fileName);
        }

        @NonNull
        @Override
        public OutputType getType() {
            return OutputType.SPLIT;
        }

        @Override
        public boolean requiresAapt() {
            return false;
        }

        @NonNull
        @Override
        public String getFilterName() {
            return filterName;
        }

        @NonNull
        @Override
        public ImmutableList<FilterData> getFilters() {
            return filters;
        }

        @NonNull
        @Override
        public String getBaseName() {
            return baseName;
        }

        @NonNull
        @Override
        public String getFullName() {
            return fullName;
        }

        @NonNull
        @Override
        public String getDirName() {
            return dirName;
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
            ConfigurationSplitApkData that = (ConfigurationSplitApkData) o;
            return Objects.equals(baseName, that.baseName)
                    && Objects.equals(fullName, that.fullName)
                    && Objects.equals(dirName, that.dirName)
                    // faster to compare the filterName than filters.
                    && Objects.equals(filterName, that.filterName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), baseName, fullName, dirName, filterName);
        }
    }
}
