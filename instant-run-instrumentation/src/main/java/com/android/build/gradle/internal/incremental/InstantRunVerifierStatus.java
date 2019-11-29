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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableMap;

/** Changes to a class that cannot be hot swapped with the current InstantRun runtime. */
public enum InstantRunVerifierStatus {

    // There were no changes.
    NO_CHANGES(InstantRunBuildMode.HOT_WARM),

    // changes are compatible with current InstantRun features.
    COMPATIBLE(InstantRunBuildMode.HOT_WARM),

    // the verifier did not run successfully.
    NOT_RUN,

    // InstantRun disabled on element like a method, class or package.
    INSTANT_RUN_DISABLED,

    // Any inability to run the verifier on a file will be tagged as such
    INSTANT_RUN_FAILURE,

    // A new class was added.
    CLASS_ADDED,

    // changes in the hierarchy
    PARENT_CLASS_CHANGED,
    IMPLEMENTED_INTERFACES_CHANGE,

    // class related changes.
    CLASS_ANNOTATION_CHANGE,
    STATIC_INITIALIZER_CHANGE,

    // changes in constructors,
    CONSTRUCTOR_SIGNATURE_CHANGE,
    SYNTHETIC_CONSTRUCTOR_CHANGE,

    // changes in method
    METHOD_SIGNATURE_CHANGE,
    METHOD_ANNOTATION_CHANGE,
    METHOD_DELETED,
    METHOD_ADDED,
    ABSTRACT_METHOD_CHANGE,

    // changes in fields.
    FIELD_ADDED,
    FIELD_REMOVED,
    // change of field type or kind (static | instance)
    FIELD_TYPE_CHANGE,

    R_CLASS_CHANGE,

    // reflection use
    REFLECTION_USED,

    JAVA_RESOURCES_CHANGED,

    BUILD_NOT_INCREMENTAL, // some of the transforms were not incremental.
    DEPENDENCY_CHANGED,

    /**
     * The merged xml manifest file changed.
     *
     * <p>Changes to resource ids referenced by the binary manifest will be {@link
     * #BINARY_MANIFEST_FILE_CHANGE}.
     */
    MANIFEST_FILE_CHANGE,

    // the binary manifest file changed, probably due to references to resources which ID changed
    // since last build.
    BINARY_MANIFEST_FILE_CHANGE,

    COLD_SWAP_REQUESTED,

    FULL_BUILD_REQUESTED(InstantRunBuildMode.FULL),

    INITIAL_BUILD(InstantRunBuildMode.FULL);

    private final ImmutableMap<InstantRunPatchingPolicy, InstantRunBuildMode> buildMode;

    InstantRunVerifierStatus() {
        this(InstantRunBuildMode.COLD);
    }

    InstantRunVerifierStatus(
            @NonNull InstantRunBuildMode multiApkBuildMode) {
        buildMode =
                ImmutableMap.of(
                        InstantRunPatchingPolicy.MULTI_APK, multiApkBuildMode,
                        InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES, multiApkBuildMode);
    }

    public InstantRunBuildMode getInstantRunBuildModeForPatchingPolicy(
            @NonNull InstantRunPatchingPolicy patchingPolicy) {
        return buildMode.get(patchingPolicy);
    }
}
