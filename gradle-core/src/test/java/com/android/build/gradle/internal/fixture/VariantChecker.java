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
import com.android.build.gradle.api.TestVariant;
import java.util.Collection;
import java.util.Set;

public interface VariantChecker {
    @NonNull
    Set<TestVariant> getTestVariants();

    @NonNull
    Set<BaseTestedVariant> getVariants();

    @NonNull
    String getReleaseJavacTaskName();

    void checkTestedVariant(
            @NonNull String variantName,
            @NonNull String testedVariantName,
            @NonNull Collection<BaseTestedVariant> variants,
            @NonNull Set<TestVariant> testVariants);

    void checkNonTestedVariant(
            @NonNull String variantName, @NonNull Set<BaseTestedVariant> variants);
}
