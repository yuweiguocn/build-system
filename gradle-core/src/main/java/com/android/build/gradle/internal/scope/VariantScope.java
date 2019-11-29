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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

/**
 * A scope containing data for a specific variant.
 */
public interface VariantScope extends TransformVariantScope, InstantRunVariantScope {
    @Override
    @NonNull
    GlobalScope getGlobalScope();

    @NonNull
    GradleVariantConfiguration getVariantConfiguration();

    @NonNull
    PublishingSpecs.VariantSpec getPublishingSpec();

    void publishIntermediateArtifact(
            @NonNull BuildableArtifact artifact,
            @NonNull ArtifactType artifactType,
            @NonNull Collection<AndroidArtifacts.PublishedConfigType> configTypes);

    void publishIntermediateArtifact(
            @NonNull Provider<? extends FileSystemLocation> artifact,
            @Nonnull Provider<String> lastProducerTaskName,
            @NonNull ArtifactType artifactType,
            @NonNull Collection<AndroidArtifacts.PublishedConfigType> configTypes);

    @NonNull
    BaseVariantData getVariantData();

    @Nullable
    CodeShrinker getCodeShrinker();

    @NonNull
    List<File> getProguardFiles();

    /**
     * Returns the proguardFiles explicitly specified in the build.gradle. This method differs from
     * getProguardFiles() because getProguardFiles() may include a default proguard file which
     * wasn't specified in the build.gradle file.
     */
    @NonNull
    List<File> getExplicitProguardFiles();

    @NonNull
    List<File> getTestProguardFiles();

    @NonNull
    List<File> getConsumerProguardFiles();

    @NonNull
    List<File> getConsumerProguardFilesForFeatures();

    @Nullable
    PostprocessingFeatures getPostprocessingFeatures();

    boolean useResourceShrinker();

    boolean isCrunchPngs();

    boolean consumesFeatureJars();

    boolean getNeedsMainDexListForBundle();

    @Override
    @NonNull
    InstantRunBuildContext getInstantRunBuildContext();

    boolean isTestOnly();

    @NonNull
    VariantType getType();

    @NonNull
    DexingType getDexingType();

    boolean getNeedsMainDexList();

    @NonNull
    AndroidVersion getMinSdkVersion();

    @NonNull
    TransformManager getTransformManager();

    @Nullable
    File getNdkDebuggableLibraryFolders(@NonNull Abi abi);

    void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath);

    @Nullable
    BaseVariantData getTestedVariantData();

    @NonNull
    File getInstantRunSplitApkOutputFolder();

    @NonNull
    File getDefaultInstantRunApkLocation();

    @NonNull
    FileCollection getJavaClasspath(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType);

    @NonNull
    FileCollection getJavaClasspath(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey);

    @NonNull
    ArtifactCollection getJavaClasspathArtifacts(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey);

    @NonNull
    BuildArtifactsHolder getArtifacts();

    @NonNull
    FileCollection getArtifactFileCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap);

    @NonNull
    FileCollection getArtifactFileCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType);

    @NonNull
    ArtifactCollection getArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType);

    @NonNull
    ArtifactCollection getArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap);

    @NonNull
    FileCollection getLocalPackagedJars();

    @NonNull
    FileCollection getProvidedOnlyClasspath();

    @NonNull
    File getIntermediateJarOutputFolder();

    @NonNull
    File getDefaultMergeResourcesOutputDir();

    @NonNull
    File getCompiledResourcesOutputDir();

    @NonNull
    File getResourceBlameLogDir();

    @NonNull
    File getBuildConfigSourceOutputDir();

    @NonNull
    File getGeneratedResOutputDir();

    @NonNull
    File getGeneratedPngsOutputDir();

    @NonNull
    File getRenderscriptResOutputDir();

    @NonNull
    File getRenderscriptObjOutputDir();

    @NonNull
    File getSourceFoldersJavaResDestinationDir();

    @NonNull
    File getAarClassesJar();

    @NonNull
    File getAarLibsDirectory();

    /**
     * Returns a place to store incremental build data. The {@code name} argument has to be unique
     * per task, ideally generated with {@link
     * com.android.build.gradle.internal.tasks.factory.TaskInformation#getName()}.
     */
    @NonNull
    File getIncrementalDir(String name);

    @NonNull
    File getCoverageReportDir();

    @NonNull
    File getClassOutputForDataBinding();

    @NonNull
    File getGeneratedClassListOutputFileForDataBinding();

    @NonNull
    File getBundleArtifactFolderForDataBinding();

    @NonNull
    File getProcessAndroidResourcesProguardOutputFile();

    @NonNull
    File getFullApkPackagesOutputDirectory();

    @NonNull
    File getMicroApkManifestFile();

    @NonNull
    File getMicroApkResDirectory();

    @NonNull
    File getAarLocation();

    @NonNull
    File getAnnotationProcessorOutputDir();

    @NonNull
    File getManifestOutputDirectory();

    @NonNull
    File getApkLocation();

    @NonNull
    File getMergedClassesJarFile();

    @Override
    @NonNull
    MutableTaskContainer getTaskContainer();

    @Nullable
    InstantRunTaskManager getInstantRunTaskManager();
    void setInstantRunTaskManager(InstantRunTaskManager taskManager);

    @NonNull
    VariantDependencies getVariantDependencies();

    @NonNull
    File getInstantRunResourceApkFolder();

    @NonNull
    File getIntermediateDir(@NonNull InternalArtifactType taskOutputType);

    enum Java8LangSupport {
        INVALID,
        UNUSED,
        D8,
        DESUGAR,
        RETROLAMBDA,
        R8,
    }

    @NonNull
    Java8LangSupport getJava8LangSupportType();

    @NonNull
    DexerTool getDexer();

    @NonNull
    DexMergerTool getDexMerger();

    @NonNull
    ConfigurableFileCollection getTryWithResourceRuntimeSupportJar();

    @NonNull
    File getOutputProguardMappingFile();

    @NonNull
    FileCollection getBootClasspath();

    @NonNull
    InternalArtifactType getManifestArtifactType();

    @NonNull
    FileCollection getSigningConfigFileCollection();
}
