/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.ide.common.resources.FileStatus;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Map;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

/**
 * A helper class to support the writing of tasks that support doing less work if they have already
 * been fully built, for example if they only need to operate on the files that have changed between
 * the previous build and the current one.
 *
 * <p>The API that inheriting classes are to implement consists of three methods, two of which are
 * optional:
 *
 * <pre>{@code
 * public class MyTask extends IncrementalTask {
 *     // This is the only non-optional method. This will be run when it's not possible to run
 *     // this task incrementally. By default, it is never possible to run your task
 *     // incrementally. You must implement the next method to determine that.
 *     @Override
 *     protected void doFullTaskAction() throws Exception {}
 *
 *     // This is the method that determines if your task can be run incrementally. If it returns
 *     // true, the next and last override method is run instead of doFullTaskAction().
 *     @Override
 *     protected boolean isIncremental() {}
 *
 *     // If you've determined that it's possible to save some time and only operate on the files
 *     // that have changed between the previous build and now, you can define that in this
 *     // method.
 *     @Override
 *     protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs)
 *        throws Exception {}
 * }
 *
 * }</pre>
 */
public abstract class IncrementalTask extends AndroidBuilderTask {

    public static final String MARKER_NAME = "build_was_incremental";

    private File incrementalFolder;

    public void setIncrementalFolder(File incrementalFolder) {
        this.incrementalFolder = incrementalFolder;
    }

    @OutputDirectory @Optional
    public File getIncrementalFolder() {
        return incrementalFolder;
    }

    /**
     * @return whether this task can support incremental update.
     */
    @Internal
    protected boolean isIncremental() {
        return false;
    }

    /**
     * This method will be called in inheriting classes if it is determined that it is not possible
     * to do this task incrementally.
     */
    protected abstract void doFullTaskAction() throws Exception;

    /**
     * Optional incremental task action. Only used if {@link #isIncremental()} returns true.
     *
     * @param changedInputs input files that have changed since the last run of this task.
     */
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws Exception {
        // do nothing.
    }

    /**
     * Gradle's entry-point into this task. Determines whether or not it's possible to do this task
     * incrementally and calls either doIncrementalTaskAction() if an incremental build is possible,
     * and doFullTaskAction() if not.
     */
    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) throws Exception {
        if (!isIncremental() || !inputs.isIncremental()) {
            getProject().getLogger().info("Unable do incremental execution: full task run");
            doFullTaskAction();
            return;
        }

        doIncrementalTaskAction(getChangedInputs(inputs));
    }

    private Map<File, FileStatus> getChangedInputs(IncrementalTaskInputs inputs) {
        final Map<File, FileStatus> changedInputs = Maps.newHashMap();

        inputs.outOfDate(
                change -> {
                    FileStatus status = change.isAdded() ? FileStatus.NEW : FileStatus.CHANGED;
                    changedInputs.put(change.getFile(), status);
                });

        inputs.removed(change -> changedInputs.put(change.getFile(), FileStatus.REMOVED));

        return changedInputs;
    }
}
