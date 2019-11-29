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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.model.CoreNdkBuildOptions;
import java.io.File;
import javax.inject.Inject;
import org.gradle.api.Project;

/**
 * DSL object for per-module ndk-build configurations, such as the path to your <code>Android.mk
 * </code> build script and external native build output directory.
 *
 * <p>To include ndk-build projects in your Gradle build, you need to use Android Studio 2.2 and
 * higher with Android plugin for Gradle 2.2.0 and higher. To learn more about Android Studio's
 * support for external native builds, read <a
 * href="https://developer.android.com/studio/projects/add-native-code.html">Add C and C++ Code to
 * Your Project</a>.
 *
 * <p>If you want to instead build your native libraries using CMake, see {@link
 * com.android.build.gradle.internal.dsl.CmakeOptions}.
 */
public class NdkBuildOptions implements CoreNdkBuildOptions {
    @NonNull
    private final Project project;

    @Nullable
    private File path;

    @Nullable private File buildStagingDirectory;

    @Inject
    public NdkBuildOptions(@NonNull Project project) {
        this.project = project;
    }

    /**
     * Specifies the relative path to your <code>Android.mk</code> build script.
     *
     * <p>For example, if your ndk-build script is in the same folder as your module-level <code>
     * build.gradle</code> file, you simply pass the following:
     *
     * <pre>
     * android {
     *     externalNativeBuild {
     *         ndkBuild {
     *             // Tells Gradle to find the root ndk-build script in the same
     *             // directory as the module's build.gradle file. Gradle requires this
     *             // build script to add your ndk-build project as a build dependency and
     *             // pull your native sources into your Android project.
     *             path "Android.mk"
     *         }
     *     }
     * }
     * </pre>
     *
     * @since 2.2.0
     */
    @Nullable
    @Override
    public File getPath() {
        return this.path;
    }

    public void setPath(@NonNull Object path) {
        this.path = project.file(path);
    }

    @Override
    public void setPath(@NonNull File path) {
        this.path = path;
    }

    /**
     * Specifies the path to your external native build output directory.
     *
     * <p>If you do not specify a value for this property, the Android plugin uses the <code>
     * &lt;project_dir&gt;/&lt;module&gt;/.externalNativeBuild/</code> directory by default.
     *
     * <p>If you specify a path that does not exist, the Android plugin creates it for you. Relative
     * paths are relative to the <code>build.gradle</code> file, as shown below:
     *
     * <pre>
     * android {
     *     externalNativeBuild {
     *         ndkBuild {
     *             // Tells Gradle to put outputs from external native
     *             // builds in the path specified below.
     *             buildStagingDirectory "./outputs/ndk-build"
     *         }
     *     }
     * }
     * </pre>
     *
     * <p>If you specify a path that's a subdirectory of your project's temporary <code>build/
     * </code> directory, you get a build error. That's because files in this directory do not
     * persist through clean builds. So, you should either keep using the default <code>
     * &lt;project_dir&gt;/&lt;module&gt;/.externalNativeBuild/</code> directory or specify a path
     * outside the temporary build directory.
     *
     * @since 3.0.0
     */
    @Nullable
    @Override
    public File getBuildStagingDirectory() {
        return buildStagingDirectory;
    }

    @Override
    public void setBuildStagingDirectory(@NonNull File buildStagingDirectory) {
        this.buildStagingDirectory = project.file(buildStagingDirectory);
    }

    public void setBuildStagingDirectory(@Nullable Object buildStagingDirectory) {
        this.buildStagingDirectory = project.file(buildStagingDirectory);
    }
}
