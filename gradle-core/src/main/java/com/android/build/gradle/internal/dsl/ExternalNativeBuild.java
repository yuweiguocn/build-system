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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;

/**
 * DSL object to configure external native builds using <a href="https://cmake.org/">CMake</a> or <a
 * href="https://developer.android.com/ndk/guides/build.html">ndk-build</a>.
 *
 * <pre>
 * android {
 *     externalNativeBuild {
 *         // Encapsulates your CMake build configurations.
 *         // For ndk-build, instead use the ndkBuild block.
 *         cmake {
 *             // Specifies a path to your CMake build script that's
 *             // relative to the build.gradle file.
 *             path "CMakeLists.txt"
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>To learn more about including external native builds to your Android Studio projects, read <a
 * href="https://developer.android.com/studio/projects/add-native-code.html">Add C and C++ Code to
 * Your Project</a>.
 */
public class ExternalNativeBuild implements CoreExternalNativeBuild {
    private NdkBuildOptions ndkBuild;
    private CmakeOptions cmake;

    @Inject
    public ExternalNativeBuild(@NonNull ObjectFactory objectFactory, @NonNull Project project) {
        ndkBuild = objectFactory.newInstance(NdkBuildOptions.class, project);
        cmake = objectFactory.newInstance(CmakeOptions.class, project);
    }

    /**
     * Encapsulates ndk-build options.
     *
     * <p>For more information, see {@link NdkBuildOptions}.
     */
    @NonNull
    @Override
    public NdkBuildOptions getNdkBuild() {
        return this.ndkBuild;
    }

    /**
     * Encapsulates per-variant configurations for your external ndk-build project, such as the path
     * to your <code>Android.mk</code> build script and build output directory.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * NdkBuildOptions}.
     */
    public NdkBuildOptions ndkBuild(Action<NdkBuildOptions> action) {
        action.execute(ndkBuild);
        return this.ndkBuild;
    }

    /**
     * Encapsulates CMake build options.
     *
     * <p>For more information, see {@link CmakeOptions}.
     */
    @NonNull
    @Override
    public CmakeOptions getCmake() {
        return cmake;
    }

    /**
     * Encapsulates per-variant configurations for your external ndk-build project, such as the path
     * to your <code>CMakeLists.txt</code> build script and build output directory.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * CmakeOptions}.
     */
    public CmakeOptions cmake(Action<CmakeOptions> action) {
        action.execute(cmake);
        return this.cmake;
    }
}
