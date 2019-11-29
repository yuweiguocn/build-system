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
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.packaging.PackagingUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import java.io.File;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

/**
 * Task responsible for loading past iteration build-info.xml file and backup necessary files for
 * disconnected devices to be able to "catch up" to latest bits.
 *
 * <p>It has no explicitly declared inputs and outputs, as it needs to run every time anyway.
 */
public class BuildInfoLoaderTask extends AndroidVariantTask {

    // Outputs
    File pastBuildsFolder;

    // Inputs
    File buildInfoFile;
    File tmpBuildInfoFile;

    Logger logger;

    // Variant state that is modified.
    InstantRunBuildContext buildContext;

    @TaskAction
    public void executeAction() {
        // loads the build information xml file.
        try {
            // load the persisted state, this will give us previous build-ids in case we need them.
            if (buildInfoFile.exists()) {
                buildContext.loadFromXmlFile(buildInfoFile);
            } else {
                buildContext.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
            }
            long token = buildContext.getSecretToken();
            if (token == 0) {
                token = PackagingUtils.computeApplicationHash(getProject().getBuildDir());
                buildContext.setSecretToken(token);
            }
            // check for the presence of a temporary buildInfoFile and if it exists, merge its
            // artifacts into the current build.
            if (tmpBuildInfoFile.exists()) {
                buildContext.mergeFromFile(tmpBuildInfoFile);
                FileUtils.delete(tmpBuildInfoFile);
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "Exception while loading build-info.xml : %s",
                    Throwables.getStackTraceAsString(e)));
        }
        try {
            // move last iteration artifacts to our back up folder.
            InstantRunBuildContext.Build lastBuild = buildContext.getLastBuild();
            if (lastBuild == null) {
                return;
            }

            // create a new backup folder with the old build-id as the name.
            File backupFolder = new File(pastBuildsFolder, String.valueOf(lastBuild.getBuildId()));
            FileUtils.mkdirs(backupFolder);
            for (InstantRunBuildContext.Artifact artifact : lastBuild.getArtifacts()) {
                if (!artifact.isAccumulative()) {
                    File oldLocation = artifact.getLocation();
                    // last iteration could have been a cold swap.
                    if (!oldLocation.isFile()) {
                        return;
                    }
                    File newLocation = new File(backupFolder, oldLocation.getName());
                    if (logger.isEnabled(LogLevel.DEBUG)) {
                        logger.debug(String.format("File moved from %1$s to %2$s",
                                oldLocation.getPath(), newLocation.getPath()));
                    }
                    Files.copy(oldLocation, newLocation);
                    // update the location in the model so it is saved with the build-info.xml
                    artifact.setLocation(newLocation);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "Exception while doing past iteration backup : %s",
                    e.getMessage()));
        }
    }

    public static class CreationAction extends TaskCreationAction<BuildInfoLoaderTask> {

        private final String taskName;

        private final InstantRunVariantScope variantScope;

        private final Logger logger;

        public CreationAction(@NonNull InstantRunVariantScope scope, @NonNull Logger logger) {
            this.taskName = scope.getTransformVariantScope().getTaskName("buildInfo", "Loader");
            this.variantScope = scope;
            this.logger = logger;
        }

        @NonNull
        @Override
        public String getName() {
            return taskName;
        }

        @NonNull
        @Override
        public Class<BuildInfoLoaderTask> getType() {
            return BuildInfoLoaderTask.class;
        }

        @Override
        public void configure(@NonNull BuildInfoLoaderTask task) {
            task.setDescription("InstantRun task to load and backup previous iterations artifacts");
            task.setVariantName(variantScope.getFullVariantName());
            task.buildInfoFile = BuildInfoWriterTask.CreationAction.getBuildInfoFile(variantScope);
            task.tmpBuildInfoFile =
                    BuildInfoWriterTask.CreationAction.getTmpBuildInfoFile(variantScope);
            task.pastBuildsFolder = variantScope.getInstantRunPastIterationsFolder();
            task.buildContext = variantScope.getInstantRunBuildContext();
            task.logger = logger;
        }
    }
}
