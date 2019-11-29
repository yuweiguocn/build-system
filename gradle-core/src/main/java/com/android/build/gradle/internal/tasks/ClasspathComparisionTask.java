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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Abstract class used to compare two configurations, and to report differences in versions of
 * artifacts. This is useful to warn user about potential issues that could arise from such
 * differences. E.g. for application, differences in runtime and compile classpath could result in
 * runtime failure.
 */
abstract class ClasspathComparisionTask extends AndroidVariantTask {

    protected ArtifactCollection runtimeClasspath;
    protected ArtifactCollection compileClasspath;
    // fake output dir so that the task doesn't run unless an input has changed.
    protected File fakeOutputDirectory;

    // even though the files are jars, we don't care about changes to the files, only if files
    // are removed or added. We need to find a better way to declare this.
    @CompileClasspath
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getRuntimeClasspath() {
        return runtimeClasspath.getArtifactFiles();
    }

    // even though the files are jars, we don't care about changes to the files, only if files
    // are removed or added. We need to find a better way to declare this.
    @CompileClasspath
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getCompileClasspath() {
        return compileClasspath.getArtifactFiles();
    }

    @OutputDirectory
    public File getFakeOutputDirectory() {
        return fakeOutputDirectory;
    }

    abstract void onDifferentVersionsFound(
            @NonNull String group,
            @NonNull String module,
            @NonNull String runtimeVersion,
            @NonNull String compileVersion);

    void compareClasspaths() {
        Set<ResolvedArtifactResult> runtimeArtifacts = runtimeClasspath.getArtifacts();
        Set<ResolvedArtifactResult> compileArtifacts = compileClasspath.getArtifacts();

        // Store a map of groupId -> (artifactId -> versions)
        Map<String, Map<String, String>> runtimeIds =
                Maps.newHashMapWithExpectedSize(runtimeArtifacts.size());

        for (ResolvedArtifactResult artifact : runtimeArtifacts) {
            // only care about external dependencies to compare versions.
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId =
                        (ModuleComponentIdentifier) componentIdentifier;

                // get the sub-map, creating it if needed.
                Map<String, String> subMap =
                        runtimeIds.computeIfAbsent(moduleId.getGroup(), s -> new HashMap<>());

                subMap.put(moduleId.getModule(), moduleId.getVersion());
            }
        }

        for (ResolvedArtifactResult artifact : compileArtifacts) {
            // only care about external dependencies to compare versions.
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId =
                        (ModuleComponentIdentifier) componentIdentifier;

                Map<String, String> subMap = runtimeIds.get(moduleId.getGroup());
                if (subMap == null) {
                    continue;
                }

                String runtimeVersion = subMap.get(moduleId.getModule());
                if (runtimeVersion == null) {
                    continue;
                }

                if (runtimeVersion.equals(moduleId.getVersion())) {
                    continue;
                }

                onDifferentVersionsFound(
                        moduleId.getGroup(),
                        moduleId.getModule(),
                        runtimeVersion,
                        moduleId.getVersion());
            }
        }
    }
}
