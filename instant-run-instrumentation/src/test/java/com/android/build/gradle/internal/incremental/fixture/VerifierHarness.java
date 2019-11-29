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

package com.android.build.gradle.internal.incremental.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.InstantRunVerifier;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.io.IOException;

/**
 * Facilities shared by all tests testing the {@link InstantRunVerifier}
 */
public class VerifierHarness {

    private static final ILogger LOGGER = new StdLogger(StdLogger.Level.INFO);

    public VerifierHarness(boolean tracing) {
    }

    public InstantRunVerifierStatus verify(@NonNull Class clazz, @Nullable String patchLevel)
            throws IOException {
        String fqcn = clazz.getName();
        byte[] original = ClassEnhancementUtil.getUninstrumentedBaseClass(fqcn);
        byte[] patch;
        if (patchLevel != null) {
            patch = ClassEnhancementUtil.getUninstrumentedPatchClass(patchLevel, fqcn);
        } else {
            patch = original;
        }

        return InstantRunVerifier.run(() -> original, () -> patch, LOGGER);
    }
}
