/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.android.utils.StringHelper;

/**
 * Data about a variant that produce a test APK
 */
public class TestVariantData extends ApkVariantData {

    @NonNull
    private final TestedVariantData testedVariantData;

    public TestVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig androidConfig,
            @NonNull TaskManager taskManager,
            @NonNull GradleVariantConfiguration config,
            @NonNull TestedVariantData testedVariantData,
            @NonNull Recorder recorder) {
        super(globalScope, androidConfig, taskManager, config, recorder);
        this.testedVariantData = testedVariantData;

        // create default output
        getOutputFactory().addMainApk();
    }

    @NonNull
    public TestedVariantData getTestedVariantData() {
        return testedVariantData;
    }

    @Override
    @NonNull
    public String getDescription() {
        String prefix;
        VariantType variantType = getType();
        if (variantType.isApk()) {
            prefix = "android (on device) tests";
        } else {
            prefix = "unit tests";
        }

        final GradleVariantConfiguration config = getVariantConfiguration();

        if (config.hasFlavors()) {
            StringBuilder sb = new StringBuilder(50);
            sb.append(prefix);
            sb.append(" for the ");
            StringHelper.appendCapitalized(sb, config.getFlavorName());
            StringHelper.appendCapitalized(sb, config.getBuildType().getName());
            sb.append(" build");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(50);
            sb.append(prefix);
            sb.append(" for the ");
            StringHelper.appendCapitalized(sb, config.getBuildType().getName());
            sb.append(" build");
            return sb.toString();
        }
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        if (testedVariantData.getVariantConfiguration().getType().isHybrid()) {
            return StringHelper.appendCapitalized(
                    prefix,
                    getVariantConfiguration().getFullName(),
                    TaskManager.FEATURE_SUFFIX,
                    suffix);
        } else {
            return super.getTaskName(prefix, suffix);
        }
    }
}
