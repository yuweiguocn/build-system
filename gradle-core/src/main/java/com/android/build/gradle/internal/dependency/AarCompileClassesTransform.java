/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import java.io.File;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/** Transform that returns the content of an extracted AAR folder. */
public class AarCompileClassesTransform extends ArtifactTransform {
    private final boolean autoNamespaceDependencies;

    @Inject
    public AarCompileClassesTransform(boolean autoNamespaceDependencies) {
        this.autoNamespaceDependencies = autoNamespaceDependencies;
    }

    @Override
    @NonNull
    public List<File> transform(@NonNull File input) {
        // Due to kotlin inlining, the implementations of the namespaced jars on the compile
        // classpath also need to be auto-namespaced.
        if (AarTransformUtil.shouldBeAutoNamespaced(input, autoNamespaceDependencies)) {
            return Collections.emptyList();
        }
        // Use the api.jar if present...
        File apiJar = new File(input, "api.jar");
        if (apiJar.exists()) {
            return Collections.singletonList(apiJar);
        }
        // ...otherwise, fall back to the runtime jars.
        return AarTransformUtil.getJars(input);
    }
}
