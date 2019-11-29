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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.MessageReceiver;
import com.google.common.base.Preconditions;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public class DexArchiveBuilderTransformBuilder {
    private Supplier<List<File>> androidJarClasspath;
    private DexOptions dexOptions;
    private MessageReceiver messageReceiver;
    private FileCache userLevelCache;
    private int minSdkVersion;
    private DexerTool dexer;
    private boolean useGradleWorkers;
    private Integer inBufferSize;
    private Integer outBufferSize;
    private boolean isDebuggable;
    private VariantScope.Java8LangSupport java8LangSupportType;
    private String projectVariant;
    private Integer numberOfBuckets;
    private boolean includeFeaturesInScopes;
    private boolean isInstantRun;
    private boolean enableDexingArtifactTransform;

    @NonNull
    public DexArchiveBuilderTransformBuilder setAndroidJarClasspath(
            @NonNull Supplier<List<File>> androidJarClasspath) {
        this.androidJarClasspath = androidJarClasspath;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setDexOptions(@NonNull DexOptions dexOptions) {
        this.dexOptions = dexOptions;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setMessageReceiver(
            @NonNull MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setUserLevelCache(@Nullable FileCache userLevelCache) {
        this.userLevelCache = userLevelCache;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setDexer(@NonNull DexerTool dexer) {
        this.dexer = dexer;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setUseGradleWorkers(boolean useGradleWorkers) {
        this.useGradleWorkers = useGradleWorkers;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setInBufferSize(@Nullable Integer inBufferSize) {
        this.inBufferSize = inBufferSize;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setOutBufferSize(@Nullable Integer outBufferSize) {
        this.outBufferSize = outBufferSize;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setIsDebuggable(boolean isDebuggable) {
        this.isDebuggable = isDebuggable;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setJava8LangSupportType(
            @NonNull VariantScope.Java8LangSupport java8LangSupportType) {
        this.java8LangSupportType = java8LangSupportType;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setProjectVariant(@NonNull String projectVariant) {
        this.projectVariant = projectVariant;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setNumberOfBuckets(@Nullable Integer numberOfBuckets) {
        this.numberOfBuckets = numberOfBuckets;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setIncludeFeaturesInScope(
            boolean includeFeaturesInScopes) {
        this.includeFeaturesInScopes = includeFeaturesInScopes;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setIsInstantRun(boolean isInstantRun) {
        this.isInstantRun = isInstantRun;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransformBuilder setEnableDexingArtifactTransform(
            boolean enableDexingArtifactTransform) {
        this.enableDexingArtifactTransform = enableDexingArtifactTransform;
        return this;
    }

    @NonNull
    public DexArchiveBuilderTransform createDexArchiveBuilderTransform() {
        Preconditions.checkNotNull(androidJarClasspath);
        Preconditions.checkNotNull(dexOptions);
        Preconditions.checkNotNull(messageReceiver);
        Preconditions.checkNotNull(dexer);
        Preconditions.checkNotNull(java8LangSupportType);
        Preconditions.checkNotNull(projectVariant);
        return new DexArchiveBuilderTransform(
                androidJarClasspath,
                dexOptions,
                messageReceiver,
                userLevelCache,
                minSdkVersion,
                dexer,
                useGradleWorkers,
                inBufferSize,
                outBufferSize,
                isDebuggable,
                java8LangSupportType,
                projectVariant,
                numberOfBuckets,
                includeFeaturesInScopes,
                isInstantRun,
                enableDexingArtifactTransform);
    }
}
