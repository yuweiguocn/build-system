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

package com.android.build.gradle.internal.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;

/** Pre build task that does some checks for application variants */
@CacheableTask
public class AppPreBuildTask extends AndroidVariantTask {

    // list of Android only compile and runtime classpath.
    private ArtifactCollection compileManifests;
    private ArtifactCollection compileNonNamespacedManifests;
    private ArtifactCollection runtimeManifests;
    private ArtifactCollection runtimeNonNamespacedManifests;
    private File fakeOutputDirectory;
    private boolean isBaseModule;

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public FileCollection getCompileManifests() {
        return compileManifests.getArtifactFiles();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public FileCollection getCompileNonNamespacedManifests() {
        return compileNonNamespacedManifests.getArtifactFiles();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public FileCollection getRuntimeManifests() {
        return runtimeManifests.getArtifactFiles();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public FileCollection getRuntimeNonNamespacedManifests() {
        return runtimeNonNamespacedManifests.getArtifactFiles();
    }

    @OutputDirectory
    public File getFakeOutputDirectory() {
        return fakeOutputDirectory;
    }

    @Input
    public boolean isBaseModule() {
        return isBaseModule;
    }

    @TaskAction
    void run() {
        Set<ResolvedArtifactResult> compileArtifacts = new HashSet<>();
        compileArtifacts.addAll(compileManifests.getArtifacts());
        compileArtifacts.addAll(compileNonNamespacedManifests.getArtifacts());

        Set<ResolvedArtifactResult> runtimeArtifacts = new HashSet<>();
        runtimeArtifacts.addAll(runtimeManifests.getArtifacts());
        runtimeArtifacts.addAll(runtimeNonNamespacedManifests.getArtifacts());

        // create a map where the key is either the sub-project path, or groupId:artifactId for
        // external dependencies.
        // For external libraries, the value is the version.
        Map<String, String> runtimeIds = Maps.newHashMapWithExpectedSize(runtimeArtifacts.size());

        // build a list of the runtime artifacts
        for (ResolvedArtifactResult artifact : runtimeArtifacts) {
            handleArtifact(artifact.getId().getComponentIdentifier(), runtimeIds::put);
        }

        // run through the compile ones to check for provided only.
        for (ResolvedArtifactResult artifact : compileArtifacts) {
            final ComponentIdentifier compileId = artifact.getId().getComponentIdentifier();
            handleArtifact(
                    compileId,
                    (key, value) -> {
                        String runtimeVersion = runtimeIds.get(key);
                        if (runtimeVersion == null) {
                            if (isBaseModule) {
                                String display = compileId.getDisplayName();
                                throw new RuntimeException(
                                        "Android dependency '"
                                                + display
                                                + "' is set to compileOnly/provided which is not supported");
                            }
                        } else if (!runtimeVersion.isEmpty()) {
                            // compare versions.
                            if (!runtimeVersion.equals(value)) {
                                throw new RuntimeException(
                                        String.format(
                                                "Android dependency '%s' has different version for the compile (%s) and runtime (%s) classpath. You should manually set the same version via DependencyResolution",
                                                key, value, runtimeVersion));
                            }
                        }
                    });
        }
    }

    private void handleArtifact(
            @NonNull ComponentIdentifier id, @NonNull BiConsumer<String, String> consumer) {
        if (id instanceof ProjectComponentIdentifier) {
            consumer.accept(((ProjectComponentIdentifier) id).getProjectPath().intern(), "");
        } else if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) id;
            consumer.accept(
                    moduleComponentId.getGroup() + ":" + moduleComponentId.getModule(),
                    moduleComponentId.getVersion());
        } else if (id instanceof OpaqueComponentArtifactIdentifier) {
            // skip those for now.
            // These are file-based dependencies and it's unlikely to be an AAR.
        } else {
            getLogger()
                    .warn(
                            "Unknown ComponentIdentifier type: "
                                    + id.getClass().getCanonicalName());
        }
    }

    public static class CreationAction
            extends TaskManager.AbstractPreBuildCreationAction<AppPreBuildTask> {

        public CreationAction(@NonNull VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public Class<AppPreBuildTask> getType() {
            return AppPreBuildTask.class;
        }

        @Override
        public void configure(@NonNull AppPreBuildTask task) {
            super.configure(task);
            task.setVariantName(variantScope.getFullVariantName());

            task.isBaseModule = variantScope.getType().isBaseModule();
            task.compileManifests =
                    variantScope.getArtifactCollection(COMPILE_CLASSPATH, ALL, MANIFEST);
            task.compileNonNamespacedManifests =
                    variantScope.getArtifactCollection(
                            COMPILE_CLASSPATH, ALL, NON_NAMESPACED_MANIFEST);
            task.runtimeManifests =
                    variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);
            task.runtimeNonNamespacedManifests =
                    variantScope.getArtifactCollection(
                            RUNTIME_CLASSPATH, ALL, NON_NAMESPACED_MANIFEST);

            task.fakeOutputDirectory =
                    new File(
                            variantScope.getGlobalScope().getIntermediatesDir(),
                            "prebuild/" + variantScope.getVariantConfiguration().getDirName());

        }
    }
}
