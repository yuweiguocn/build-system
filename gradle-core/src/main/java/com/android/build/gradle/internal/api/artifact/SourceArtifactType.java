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

package com.android.build.gradle.internal.api.artifact;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.ArtifactType;
import org.gradle.api.Incubating;

/** [ArtifactType] for source set. */
@Incubating
public enum SourceArtifactType implements ArtifactType {
    JAVA_SOURCES,
    JAVA_RESOURCES,
    ASSETS,
    ANDROID_RESOURCES,
    AIDL,
    RENDERSCRIPT,
    JNI,
    JNI_LIBS,
    SHADERS;

    @NonNull
    @Override
    public Kind kind() {
        return Kind.DIRECTORY;
    }
}
