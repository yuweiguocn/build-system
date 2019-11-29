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
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.VariantType;
import com.google.common.collect.Sets;
import java.util.Set;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * Base class for transforms that consume ProGuard configuration files.
 *
 * We use this type to configure ProGuard and the built-in shrinker consistently, using the same
 * code.
 */
public abstract class ProguardConfigurable extends Transform {
    private final ConfigurableFileCollection configurationFiles;

    protected final VariantType variantType;

    private final boolean includeFeaturesInScopes;

    ProguardConfigurable(@NonNull VariantScope scope) {
        this(
                scope.getGlobalScope().getProject().files(),
                scope.getVariantData().getType(),
                scope.consumesFeatureJars());
    }

    ProguardConfigurable(
            @NonNull ConfigurableFileCollection configurationFiles,
            @NonNull VariantType type,
            boolean includeFeaturesInScopes) {
        this.configurationFiles = configurationFiles;
        this.variantType = type;
        this.includeFeaturesInScopes = includeFeaturesInScopes;
    }

    public void setConfigurationFiles(FileCollection configFiles) {
        configurationFiles.from(configFiles);
    }

    FileCollection getAllConfigurationFiles() {
        return configurationFiles;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        if (variantType.isAar()) {
            return TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS;
        } else if (includeFeaturesInScopes) {
            return TransformManager.SCOPE_FULL_WITH_FEATURES;
        }
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        Set<Scope> set = Sets.newHashSetWithExpectedSize(5);
        if (variantType.isAar()) {
            set.add(Scope.SUB_PROJECTS);
            set.add(Scope.EXTERNAL_LIBRARIES);
        }

        if (variantType.isTestComponent()) {
            set.add(Scope.TESTED_CODE);
        }

        set.add(Scope.PROVIDED_ONLY);

        return Sets.immutableEnumSet(set);
    }

    public abstract void keep(@NonNull String keep);

    public abstract void keepattributes();

    public abstract void dontwarn(@NonNull String dontwarn);

    public abstract void setActions(@NonNull PostprocessingFeatures actions);
}
