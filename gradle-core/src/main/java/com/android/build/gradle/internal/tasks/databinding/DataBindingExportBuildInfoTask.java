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

package com.android.build.gradle.internal.tasks.databinding;

import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.processing.Scope;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import java.util.function.Supplier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/**
 * Task to create an empty class annotated with @BindingBuildInfo, so that the Java compiler invokes
 * data binding even when the rest of the source code does not have data binding annotations.
 *
 * <p>Note: The task name might be misleading: Historically, this task was used to generate a class
 * that contained the build environment information needed for data binding, but it is now no longer
 * the case. We'll rename it later.
 */
public class DataBindingExportBuildInfoTask extends AndroidVariantTask {

    @Internal private Supplier<LayoutXmlProcessor> xmlProcessor;

    private boolean useAndroidX;

    private File emptyClassOutDir;

    @Input
    public boolean isUseAndroidX() {
        return useAndroidX;
    }

    @OutputDirectory
    public File getEmptyClassOutDir() {
        return emptyClassOutDir;
    }

    @TaskAction
    public void run() {
        xmlProcessor.get().writeEmptyInfoClass(useAndroidX);
        Scope.assertNoError();
    }

    public static class CreationAction
            extends VariantTaskCreationAction<DataBindingExportBuildInfoTask> {

        public CreationAction(@NonNull VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("dataBindingExportBuildInfo");
        }

        @NonNull
        @Override
        public Class<DataBindingExportBuildInfoTask> getType() {
            return DataBindingExportBuildInfoTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends DataBindingExportBuildInfoTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setDataBindingExportBuildInfoTask(taskProvider);
        }

        @Override
        public void configure(@NonNull DataBindingExportBuildInfoTask task) {
            super.configure(task);
            VariantScope variantScope = getVariantScope();

            task.xmlProcessor = variantScope.getVariantData()::getLayoutXmlProcessor;
            task.useAndroidX =
                    variantScope
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.USE_ANDROID_X);
            task.emptyClassOutDir = variantScope.getClassOutputForDataBinding();

            task.dependsOn(variantScope.getTaskContainer().getSourceGenTask());
        }
    }
}