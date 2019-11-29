/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.compiling.ResValueGenerator;
import com.android.builder.model.ClassField;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

@CacheableTask
public class GenerateResValues extends AndroidBuilderTask {

    // ----- PUBLIC TASK API -----

    private File resOutputDir;

    @OutputDirectory
    public File getResOutputDir() {
        return resOutputDir;
    }

    public void setResOutputDir(File resOutputDir) {
        this.resOutputDir = resOutputDir;
    }

    // ----- PRIVATE TASK API -----

    @Internal // handled by getItemValues()
    public List<Object> getItems() {
        return items.get();
    }

    public void setItems(List<Object> items) {
        this.items = () -> items;
    }

    private Supplier<List<Object>> items;

    @Input
    public List<String> getItemValues() {
        List<Object> resolvedItems = getItems();
        List<String> list = Lists.newArrayListWithCapacity(resolvedItems.size() * 3);

        for (Object object : resolvedItems) {
            if (object instanceof String) {
                list.add((String) object);
            } else if (object instanceof ClassField) {
                ClassField field = (ClassField) object;
                list.add(field.getType());
                list.add(field.getName());
                list.add(field.getValue());
            }
        }

        return list;
    }

    @TaskAction
    void generate() throws IOException, ParserConfigurationException {
        File folder = getResOutputDir();
        List<Object> resolvedItems = getItems();

        if (resolvedItems.isEmpty()) {
            FileUtils.cleanOutputDir(folder);
        } else {
            ResValueGenerator generator = new ResValueGenerator(folder);
            generator.addItems(getItems());

            generator.generate();
        }
    }


    public static class CreationAction extends VariantTaskCreationAction<GenerateResValues> {

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("generate", "ResValues");
        }

        @NonNull
        @Override
        public Class<GenerateResValues> getType() {
            return GenerateResValues.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends GenerateResValues> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setGenerateResValuesTask(taskProvider);
        }

        @Override
        public void configure(@NonNull GenerateResValues task) {
            super.configure(task);

            VariantScope scope = getVariantScope();

            task.items = TaskInputHelper.memoize(scope.getVariantConfiguration()::getResValues);

            task.setResOutputDir(scope.getGeneratedResOutputDir());
        }
    }
}
