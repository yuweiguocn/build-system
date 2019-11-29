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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.InstantRunArtifact;
import com.google.wireless.android.sdk.stats.InstantRunStatus;

public class InstantRunAnalyticsHelper {

    /** Generate a scrubbed proto of the instant run build context for analytics. */
    @NonNull
    public static InstantRunStatus generateAnalyticsProto(
            @NonNull InstantRunBuildContext buildContext) {
        InstantRunStatus.Builder builder = InstantRunStatus.newBuilder();

        builder.setBuildMode(convert(buildContext.getBuildMode()));
        builder.setPatchingPolicy(convert(buildContext.getPatchingPolicy()));
        builder.setVerifierStatus(convert(buildContext.getVerifierResult()));

        InstantRunBuildContext.Build last = buildContext.getLastBuild();
        if (last != null) {
            for (InstantRunBuildContext.Artifact artifact : last.getArtifacts()) {
                builder.addArtifact(
                        InstantRunArtifact.newBuilder().setType(convert(artifact.getType())));
            }
        }
        return builder.build();
    }


    @VisibleForTesting
    @NonNull
    static InstantRunStatus.BuildMode convert(@NonNull InstantRunBuildMode mode) {
        switch (mode) {
            case HOT_WARM:
                return InstantRunStatus.BuildMode.HOT_WARM;
            case COLD:
                return InstantRunStatus.BuildMode.COLD;
            case FULL:
                return InstantRunStatus.BuildMode.FULL;
            default:
                return InstantRunStatus.BuildMode.UNKNOWN_BUILD_MODE;
        }
    }

    @VisibleForTesting
    @NonNull
    static InstantRunStatus.PatchingPolicy convert(@Nullable InstantRunPatchingPolicy policy) {
        if (policy == null) {
            return InstantRunStatus.PatchingPolicy.UNKNOWN_PATCHING_POLICY;
        }
        switch (policy) {
            case MULTI_APK:
                return InstantRunStatus.PatchingPolicy.MULTI_APK;
            case MULTI_APK_SEPARATE_RESOURCES:
                return InstantRunStatus.PatchingPolicy.MULTI_APK_SEPARATE_RESOURCES;
            default:
                return InstantRunStatus.PatchingPolicy.UNKNOWN_PATCHING_POLICY;
        }
    }

    @VisibleForTesting
    @NonNull
    static InstantRunStatus.VerifierStatus convert(@NonNull InstantRunVerifierStatus status) {
        try {
            return InstantRunStatus.VerifierStatus.valueOf(status.toString());
        } catch (IllegalArgumentException ignored) {
            return InstantRunStatus.VerifierStatus.UNKNOWN_VERIFIER_STATUS;
        }
    }

    @VisibleForTesting
    @NonNull
    static InstantRunArtifact.Type convert(@NonNull FileType type) {
        switch (type) {
            case MAIN:
                return InstantRunArtifact.Type.MAIN;
            case SPLIT_MAIN:
                return InstantRunArtifact.Type.SPLIT_MAIN;
            case RELOAD_DEX:
                return InstantRunArtifact.Type.RELOAD_DEX;
            case SPLIT:
                return InstantRunArtifact.Type.SPLIT;
            case RESOURCES:
                return InstantRunArtifact.Type.RESOURCES;
            default:
                throw new RuntimeException("Cannot convert " + type);
        }

    }

    private InstantRunAnalyticsHelper() {
        // Utility class
    }
}
