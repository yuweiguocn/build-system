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

package com.android.build.gradle.internal.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.api.SourceKind;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.api.UnitTestVariant;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.model.BuildType;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

public interface BaseTestedVariant extends BaseVariant, TestedVariant {
    static <T extends BaseVariant & TestedVariant> BaseTestedVariant create(T variant) {
        return new BaseTestedVariantImpl<>(variant);
    }

    <T extends BaseVariant & TestedVariant> T getOriginal();

    final class BaseTestedVariantImpl<T extends BaseVariant & TestedVariant>
            implements BaseTestedVariant {
        private final T variant;

        public BaseTestedVariantImpl(T variant) {
            this.variant = variant;
        }

        @Override
        public T getOriginal() {
            return variant;
        }

        @NonNull
        @Override
        public String getName() {
            return variant.getName();
        }

        @NonNull
        @Override
        public String getDescription() {
            return variant.getDescription();
        }

        @NonNull
        @Override
        public String getDirName() {
            return variant.getDirName();
        }

        @NonNull
        @Override
        public String getBaseName() {
            return variant.getBaseName();
        }

        @NonNull
        @Override
        public String getFlavorName() {
            return variant.getFlavorName();
        }

        @NonNull
        @Override
        public DomainObjectCollection<BaseVariantOutput> getOutputs() {
            return variant.getOutputs();
        }

        @NonNull
        @Override
        public BuildType getBuildType() {
            return variant.getBuildType();
        }

        @NonNull
        @Override
        public ProductFlavor getMergedFlavor() {
            return variant.getMergedFlavor();
        }

        @NonNull
        @Override
        public JavaCompileOptions getJavaCompileOptions() {
            return variant.getJavaCompileOptions();
        }

        @NonNull
        @Override
        public List<ProductFlavor> getProductFlavors() {
            return variant.getProductFlavors();
        }

        @NonNull
        @Override
        public List<SourceProvider> getSourceSets() {
            return variant.getSourceSets();
        }

        @NonNull
        @Override
        public List<ConfigurableFileTree> getSourceFolders(@NonNull SourceKind folderType) {
            return variant.getSourceFolders(folderType);
        }

        @NonNull
        @Override
        public Configuration getCompileConfiguration() {
            return variant.getCompileConfiguration();
        }

        @NonNull
        @Override
        public Configuration getAnnotationProcessorConfiguration() {
            return variant.getAnnotationProcessorConfiguration();
        }

        @NonNull
        @Override
        public Configuration getRuntimeConfiguration() {
            return variant.getRuntimeConfiguration();
        }

        @NonNull
        @Override
        public String getApplicationId() {
            return variant.getApplicationId();
        }

        @NonNull
        @Override
        public TextResource getApplicationIdTextResource() {
            return variant.getApplicationIdTextResource();
        }

        @NonNull
        @Override
        public Task getPreBuild() {
            //noinspection deprecation
            return variant.getPreBuild();
        }

        @NonNull
        @Override
        public TaskProvider<Task> getPreBuildProvider() {
            return variant.getPreBuildProvider();
        }

        @NonNull
        @Override
        public Task getCheckManifest() {
            return variant.getCheckManifest();
        }

        @NonNull
        @Override
        public TaskProvider<Task> getCheckManifestProvider() {
            return variant.getCheckManifestProvider();
        }

        @NonNull
        @Override
        public AidlCompile getAidlCompile() {
            //noinspection deprecation
            return variant.getAidlCompile();
        }

        @NonNull
        @Override
        public TaskProvider<AidlCompile> getAidlCompileProvider() {
            return variant.getAidlCompileProvider();
        }

        @NonNull
        @Override
        public RenderscriptCompile getRenderscriptCompile() {
            //noinspection deprecation
            return variant.getRenderscriptCompile();
        }

        @NonNull
        @Override
        public TaskProvider<RenderscriptCompile> getRenderscriptCompileProvider() {
            return variant.getRenderscriptCompileProvider();
        }

        @Nullable
        @Override
        public MergeResources getMergeResources() {
            //noinspection deprecation
            return variant.getMergeResources();
        }

        @Nullable
        @Override
        public TaskProvider<MergeResources> getMergeResourcesProvider() {
            return variant.getMergeResourcesProvider();
        }

        @Nullable
        @Override
        public MergeSourceSetFolders getMergeAssets() {
            //noinspection deprecation
            return variant.getMergeAssets();
        }

        @Nullable
        @Override
        public TaskProvider<MergeSourceSetFolders> getMergeAssetsProvider() {
            return variant.getMergeAssetsProvider();
        }

        @Nullable
        @Override
        public GenerateBuildConfig getGenerateBuildConfig() {
            //noinspection deprecation
            return variant.getGenerateBuildConfig();
        }

        @Nullable
        @Override
        public TaskProvider<GenerateBuildConfig> getGenerateBuildConfigProvider() {
            return variant.getGenerateBuildConfigProvider();
        }

        @NonNull
        @Override
        public JavaCompile getJavaCompile() {
            //noinspection deprecation
            return variant.getJavaCompile();
        }

        @NonNull
        @Override
        public TaskProvider<JavaCompile> getJavaCompileProvider() {
            return variant.getJavaCompileProvider();
        }

        @NonNull
        @Override
        public Task getJavaCompiler() {
            //noinspection deprecation
            return variant.getJavaCompiler();
        }

        @NonNull
        @Override
        public FileCollection getCompileClasspath(Object key) {
            return variant.getCompileClasspath(key);
        }

        @NonNull
        @Override
        public ArtifactCollection getCompileClasspathArtifacts(@Nullable Object key) {
            return variant.getCompileClasspathArtifacts(key);
        }

        @NonNull
        @Override
        public Collection<ExternalNativeBuildTask> getExternalNativeBuildTasks() {
            //noinspection deprecation
            return variant.getExternalNativeBuildTasks();
        }

        @NonNull
        @Override
        public Collection<TaskProvider<ExternalNativeBuildTask>> getExternalNativeBuildProviders() {
            return variant.getExternalNativeBuildProviders();
        }

        @Nullable
        @Override
        public Task getObfuscation() {
            return null;
        }

        @Nullable
        @Override
        public File getMappingFile() {
            return variant.getMappingFile();
        }

        @NonNull
        @Override
        public AbstractCopyTask getProcessJavaResources() {
            //noinspection deprecation
            return variant.getProcessJavaResources();
        }

        @NonNull
        @Override
        public TaskProvider<AbstractCopyTask> getProcessJavaResourcesProvider() {
            return variant.getProcessJavaResourcesProvider();
        }

        @Nullable
        @Override
        public Task getAssemble() {
            //noinspection deprecation
            return variant.getAssemble();
        }

        @Nullable
        @Override
        public TaskProvider<Task> getAssembleProvider() {
            return variant.getAssembleProvider();
        }

        @Override
        public void addJavaSourceFoldersToModel(@NonNull File... sourceFolders) {
            variant.addJavaSourceFoldersToModel(sourceFolders);
        }

        @Override
        public void addJavaSourceFoldersToModel(@NonNull Collection<File> sourceFolders) {
            variant.addJavaSourceFoldersToModel(sourceFolders);
        }

        @Override
        public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... sourceFolders) {
            variant.registerJavaGeneratingTask(task, sourceFolders);
        }

        @Override
        public void registerJavaGeneratingTask(
                @NonNull Task task, @NonNull Collection<File> sourceFolders) {
            variant.registerJavaGeneratingTask(task, sourceFolders);
        }

        @Override
        public void registerExternalAptJavaOutput(@NonNull ConfigurableFileTree folder) {
            variant.registerExternalAptJavaOutput(folder);
        }

        @Override
        public void registerGeneratedResFolders(@NonNull FileCollection folders) {
            variant.registerGeneratedResFolders(folders);
        }

        @Override
        public void registerResGeneratingTask(@NonNull Task task, @NonNull File... resFolders) {
            variant.registerResGeneratingTask(task, resFolders);
        }

        @Override
        public void registerResGeneratingTask(
                @NonNull Task task, @NonNull Collection<File> resFolders) {
            variant.registerResGeneratingTask(task, resFolders);
        }

        @Override
        public Object registerPreJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
            return variant.registerPreJavacGeneratedBytecode(fileCollection);
        }

        @Override
        public void registerPostJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
            variant.registerPostJavacGeneratedBytecode(fileCollection);
        }

        @Override
        public Object registerGeneratedBytecode(@NonNull FileCollection fileCollection) {
            return registerPreJavacGeneratedBytecode(fileCollection);
        }

        @Override
        public void buildConfigField(
                @NonNull String type, @NonNull String name, @NonNull String value) {
            variant.buildConfigField(type, name, value);
        }

        @Override
        public void resValue(@NonNull String type, @NonNull String name, @NonNull String value) {
            variant.resValue(type, name, value);
        }

        @Override
        public void missingDimensionStrategy(
                @NonNull String dimension, @NonNull String requestedValue) {
            variant.missingDimensionStrategy(dimension, requestedValue);
        }

        @Override
        public void missingDimensionStrategy(
                @NonNull String dimension, @NonNull String... requestedValues) {
            variant.missingDimensionStrategy(dimension, requestedValues);
        }

        @Override
        public void missingDimensionStrategy(
                @NonNull String dimension, @NonNull List<String> requestedValues) {
            variant.missingDimensionStrategy(dimension, requestedValues);
        }

        @Override
        public void setOutputsAreSigned(boolean isSigned) {
            variant.setOutputsAreSigned(isSigned);
        }

        @Override
        public boolean getOutputsAreSigned() {
            return variant.getOutputsAreSigned();
        }

        @Override
        public void setTestVariant(@Nullable TestVariant testVariant) {
            variant.setTestVariant(testVariant);
        }

        @Nullable
        @Override
        public TestVariant getTestVariant() {
            return variant.getTestVariant();
        }

        @Nullable
        @Override
        public UnitTestVariant getUnitTestVariant() {
            return variant.getUnitTestVariant();
        }

        @Override
        public void setUnitTestVariant(@Nullable UnitTestVariant testVariant) {
            variant.setUnitTestVariant(testVariant);
        }

        @NonNull
        @Override
        public FileCollection getAllRawAndroidResources() {
            return variant.getAllRawAndroidResources();
        }

        @Override
        public void register(Task task) {
            variant.register(task);
        }
    }
}
