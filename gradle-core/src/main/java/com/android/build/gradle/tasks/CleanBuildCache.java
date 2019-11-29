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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.utils.FileCache;
import com.google.common.base.Preconditions;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskAction;

/** Task to clean the build cache. */
public class CleanBuildCache extends DefaultTask {

    @Nullable private FileCache buildCache;

    public void setBuildCache(@NonNull FileCache buildCache) {
        this.buildCache = buildCache;
    }

    @TaskAction
    public void clean() throws IOException {
        Preconditions.checkNotNull(buildCache, "buildCache must not be null");
        buildCache.delete();
    }

    @Immutable
    public static final class CreationAction extends TaskCreationAction<CleanBuildCache> {

        @NonNull private final GlobalScope globalScope;

        public CreationAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        @Override
        public String getName() {
            return "cleanBuildCache";
        }

        @NonNull
        @Override
        public Class<CleanBuildCache> getType() {
            return CleanBuildCache.class;
        }

        @Override
        public void configure(@NonNull CleanBuildCache task) {
            Preconditions.checkNotNull(globalScope.getBuildCache());

            task.setDescription("Deletes the build cache directory.");
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setBuildCache(globalScope.getBuildCache());
        }
    }
}
