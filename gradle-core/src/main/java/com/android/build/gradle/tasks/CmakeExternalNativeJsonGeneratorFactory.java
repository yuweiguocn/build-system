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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.builder.core.AndroidBuilder;
import com.android.repository.Revision;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.util.Set;

/** Factory class to create Cmake strategy object based on Cmake version. */
class CmakeExternalNativeJsonGeneratorFactory {
    /**
     * Creates a Cmake strategy object for the given cmake revision. We currently only support Cmake
     * versions 3.6+.
     */
    public static ExternalNativeJsonGenerator createCmakeStrategy(
            @NonNull JsonGenerationVariantConfiguration config,
            @NonNull Set<String> configurationFailures,
            @NonNull Revision cmakeRevision,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File cmakeInstallFolder,
            @NonNull GradleBuildVariant.Builder stats) {

        stats.setNativeCmakeVersion(cmakeRevision.toShortString());

        // Custom Cmake shipped with Android studio has a fixed version, we'll just use that exact
        // version to check.
        if (cmakeRevision.equals(
                Revision.parseRevision(
                        ExternalNativeBuildTaskUtils.CUSTOM_FORK_CMAKE_VERSION,
                        Revision.Precision.MICRO))) {
            return new CmakeAndroidNinjaExternalNativeJsonGenerator(
                    config, configurationFailures, androidBuilder, cmakeInstallFolder, stats);
        }

        if (cmakeRevision.getMajor() < 3
                || (cmakeRevision.getMajor() == 3 && cmakeRevision.getMinor() <= 6)) {
            throw new RuntimeException(
                    "Unexpected/unsupported CMake version "
                            + cmakeRevision.toString()
                            + ". Try 3.7.0 or later.");
        }

        return new CmakeServerExternalNativeJsonGenerator(
                config, configurationFailures, androidBuilder, cmakeInstallFolder, stats);
    }
}
