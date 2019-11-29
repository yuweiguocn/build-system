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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.options.Option;
import java.util.Set;

public class NoOpDeprecationReporter implements DeprecationReporter {

    @Override
    public void reportDeprecatedUsage(
            @NonNull String newDslElement,
            @NonNull String oldDslElement,
            @NonNull DeprecationTarget deprecationTarget) {
        // do nothing
    }

    @Override
    public void reportObsoleteUsage(
            @NonNull String oldDslElement, @NonNull DeprecationTarget deprecationTarget) {
        // do nothing
    }

    @Override
    public void reportDeprecatedUsage(
            @NonNull String newDslElement,
            @NonNull String oldDslElement,
            @NonNull String url,
            @NonNull DeprecationTarget deprecationTarget) {
        // do nothing
    }

    @Override
    public void reportObsoleteUsage(
            @NonNull String oldDslElement,
            @NonNull String url,
            @NonNull DeprecationTarget deprecationTarget) {
        // do nothing
    }

    @Override
    public void reportDeprecatedApi(
            @NonNull String newApiElement,
            @NonNull String oldApiElement,
            @NonNull String url,
            @NonNull DeprecationTarget deprecationTarget) {
        // do nothing
    }

    @Override
    public void reportRenamedConfiguration(
            @NonNull String newConfiguration,
            @NonNull String oldConfiguration,
            @NonNull DeprecationTarget deprecationTarget,
            @Nullable String url) {
        // do nothing
    }

    @Override
    public void reportDeprecatedConfiguration(
            @NonNull String newDslElement,
            @NonNull String oldConfiguration,
            @NonNull DeprecationTarget deprecationTarget) {
        // do nothing
    }

    @Override
    public void reportDeprecatedValue(
            @NonNull String dslElement,
            @NonNull String oldValue,
            @Nullable String newValue,
            @Nullable String url,
            @NonNull DeprecationTarget deprecationTarget) {
        // do nothing.
    }

    @Override
    public void reportDeprecatedOption(
            @NonNull String option,
            @Nullable String value,
            @NonNull DeprecationTarget deprecationTarget) {
        // do nothing
    }

    @Override
    public void reportDeprecatedOptions(@NonNull Set<? extends Option<?>> options) {
        // do nothing
    }

    @Override
    public void reportExperimentalOption(@NonNull Option<?> option, @NonNull String value) {
        // do nothing
    }
}
