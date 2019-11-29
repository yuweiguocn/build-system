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
import com.android.builder.profile.ProcessProfileWriter;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to finalize and write the {@code build-info.xml}.
 *
 * <p>If the build has failed, it writes a tmp build info instead, which is loaded in the next
 * build.
 *
 * <p>See {@link InstantRunBuildContext}.
 */
public class BuildInfoWriterTask extends AndroidVariantTask {

    /**
     * Output File
     */
    File buildInfoFile;

    /** Input */
    File tmpBuildInfoFile;

    Logger logger;

    InstantRunBuildContext buildContext;

    @TaskAction
    public void executeAction() {

        if (buildContext.getBuildHasFailed()) {
            try {
                buildContext.writeTmpBuildInfo(tmpBuildInfoFile);
            } catch (ParserConfigurationException | IOException e) {
                throw new RuntimeException("Exception while saving temp-build-info.xml", e);
            }
            return;
        }

        // done with the instant run context.
        buildContext.close();

        try {
            String xml = buildContext.toXml();
            if (logger.isEnabled(LogLevel.DEBUG)) {
                logger.debug("build-id $1$l, build-info.xml : %2$s",
                        buildContext.getBuildId(), xml);
            }
            Files.createParentDirs(buildInfoFile);
            Files.asCharSink(buildInfoFile, Charsets.UTF_8).write(xml);
        } catch (Exception e) {
            throw new RuntimeException("Exception while saving build-info.xml", e);
        }

        // Record instant run status in analytics for this build
        ProcessProfileWriter.getGlobalProperties()
                .setInstantRunStatus(
                        InstantRunAnalyticsHelper.generateAnalyticsProto(buildContext));
    }

    public static class CreationAction extends TaskCreationAction<BuildInfoWriterTask> {

        public static File getBuildInfoFile(@NonNull InstantRunVariantScope scope) {
            return new File(scope.getBuildInfoOutputFolder(), "build-info.xml");
        }

        public static File getTmpBuildInfoFile(@NonNull InstantRunVariantScope scope) {
            return new File(scope.getBuildInfoOutputFolder(), "tmp-build-info.xml");
        }


        private final String taskName;
        private final InstantRunVariantScope variantScope;
        private final Logger logger;

        public CreationAction(@NonNull InstantRunVariantScope scope, @NonNull Logger logger) {
            this.taskName = scope.getTransformVariantScope().getTaskName("buildInfoGenerator");
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
        public Class<BuildInfoWriterTask> getType() {
            return BuildInfoWriterTask.class;
        }

        @Override
        public void configure(@NonNull BuildInfoWriterTask task) {
            task.setDescription("InstantRun task to build incremental artifacts");
            task.setVariantName(variantScope.getFullVariantName());
            task.buildInfoFile = getBuildInfoFile(variantScope);
            task.tmpBuildInfoFile = getTmpBuildInfoFile(variantScope);
            task.buildContext = variantScope.getInstantRunBuildContext();
            task.logger = logger;
        }
    }

}
