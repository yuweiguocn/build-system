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

package com.android.build.gradle.internal.variant;

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.gradle.api.NamedDomainObjectContainer;

/** An implementation of VariantFactory for a project that generates AppBundles. */
public class InstantAppVariantFactory extends BaseVariantFactory {

    public InstantAppVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig extension) {
        super(globalScope, extension);
    }

    @NonNull
    @Override
    public BaseVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull TaskManager taskManager,
            @NonNull Recorder recorder) {
        InstantAppVariantData variant =
                new InstantAppVariantData(
                        globalScope,
                        extension,
                        taskManager,
                        variantConfiguration,
                        recorder);
        variant.getOutputFactory().addMainApk();
        return variant;
    }

    @Override
    @Nullable
    public Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData) {
        return null;
    }

    @NonNull
    @Override
    public Collection<VariantType> getVariantConfigurationTypes() {
        return ImmutableList.of(VariantTypeImpl.INSTANTAPP);
    }

    @Override
    public boolean hasTestScope() {
        return false;
    }

    @Override
    public void validateModel(@NonNull VariantModel model) {
        // No additional checks for InstantAppVariantFactory, so just return.
    }

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    }
}
