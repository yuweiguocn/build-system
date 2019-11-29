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

package com.android.build.gradle.integration.instant;

import com.android.annotations.NonNull;
import com.android.testutils.apk.Apk;
import com.android.tools.ir.client.InstantRunArtifactType;
import java.io.IOException;
import java.nio.file.Path;

/** Specialized {@link Apk} that has an instant run artifact type. */
public class InstantRunApk extends Apk {

    InstantRunArtifactType artifactType;

    public InstantRunApk(@NonNull Path file, @NonNull InstantRunArtifactType artifactType)
            throws IOException {
        super(file);
        this.artifactType = artifactType;
    }

    public InstantRunArtifactType getArtifactType() {
        return artifactType;
    }
}
