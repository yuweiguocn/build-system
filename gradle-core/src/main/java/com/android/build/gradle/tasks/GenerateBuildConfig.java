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
package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.compiling.BuildConfigGenerator;
import com.android.builder.model.ClassField;
import com.android.utils.FileUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

@CacheableTask
public class GenerateBuildConfig extends AndroidBuilderTask {

    // ----- PUBLIC TASK API -----

    private File sourceOutputDir;

    @OutputDirectory
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    // ----- PRIVATE TASK API -----

    private Supplier<String> buildConfigPackageName;

    private Supplier<String> appPackageName;

    private Supplier<Boolean> debuggable;

    private Supplier<String> flavorName;

    private Supplier<List<String>> flavorNamesWithDimensionNames;

    private String buildTypeName;

    private Supplier<String> versionName;

    private Supplier<Integer> versionCode;

    private Supplier<List<Object>> items;

    private BuildableArtifact checkManifestResult;

    @Input
    public String getBuildConfigPackageName() {
        return buildConfigPackageName.get();
    }

    @Input
    public String getAppPackageName() {
        return appPackageName.get();
    }

    @Input
    public boolean isDebuggable() {
        return debuggable.get();
    }

    @Input
    public String getFlavorName() {
        return flavorName.get();
    }

    @Input
    public List<String> getFlavorNamesWithDimensionNames() {
        return flavorNamesWithDimensionNames.get();
    }

    @Input
    public String getBuildTypeName() {
        return buildTypeName;
    }

    public void setBuildTypeName(String buildTypeName) {
        this.buildTypeName = buildTypeName;
    }

    @Input
    @Optional
    public String getVersionName() {
        return versionName.get();
    }

    @Input
    public int getVersionCode() {
        return versionCode.get();
    }

    @Internal // handled by getItemValues()
    public List<Object> getItems() {
        return items.get();
    }

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

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public BuildableArtifact getCheckManifestResult() {
        return checkManifestResult;
    }

    @TaskAction
    void generate() throws IOException {
        // must clear the folder in case the packagename changed, otherwise,
        // there'll be two classes.
        File destinationDir = getSourceOutputDir();
        FileUtils.cleanOutputDir(destinationDir);

        BuildConfigGenerator generator = new BuildConfigGenerator(
                getSourceOutputDir(),
                getBuildConfigPackageName());

        // Hack (see IDEA-100046): We want to avoid reporting "condition is always true"
        // from the data flow inspection, so use a non-constant value. However, that defeats
        // the purpose of this flag (when not in debug mode, if (BuildConfig.DEBUG && ...) will
        // be completely removed by the compiler), so as a hack we do it only for the case
        // where debug is true, which is the most likely scenario while the user is looking
        // at source code.
        //map.put(PH_DEBUG, Boolean.toString(mDebug));
        generator
                .addField(
                        "boolean",
                        "DEBUG",
                        isDebuggable() ? "Boolean.parseBoolean(\"true\")" : "false")
                .addField("String", "APPLICATION_ID", '"' + appPackageName.get() + '"')
                .addField("String", "BUILD_TYPE", '"' + getBuildTypeName() + '"')
                .addField("String", "FLAVOR", '"' + getFlavorName() + '"')
                .addField("int", "VERSION_CODE", Integer.toString(getVersionCode()))
                .addField(
                        "String", "VERSION_NAME", '"' + Strings.nullToEmpty(getVersionName()) + '"')
                .addItems(getItems());

        List<String> flavors = getFlavorNamesWithDimensionNames();
        int count = flavors.size();
        if (count > 1) {
            for (int i = 0; i < count; i += 2) {
                generator.addField(
                        "String", "FLAVOR_" + flavors.get(i + 1), '"' + flavors.get(i) + '"');
            }
        }

        generator.generate();
    }

    // ----- Config Action -----

    public static final class CreationAction
            extends VariantTaskCreationAction<GenerateBuildConfig> {

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("generate", "BuildConfig");
        }

        @Override
        @NonNull
        public Class<GenerateBuildConfig> getType() {
            return GenerateBuildConfig.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends GenerateBuildConfig> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setGenerateBuildConfigTask(taskProvider);
        }

        @Override
        public void configure(@NonNull GenerateBuildConfig task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            BaseVariantData variantData = scope.getVariantData();

            final GradleVariantConfiguration variantConfiguration =
                    variantData.getVariantConfiguration();

            task.buildConfigPackageName =
                    TaskInputHelper.memoize(variantConfiguration::getOriginalApplicationId);

            task.appPackageName = TaskInputHelper.memoize(variantConfiguration::getApplicationId);

            task.versionName = TaskInputHelper.memoize(variantConfiguration::getVersionName);
            task.versionCode = TaskInputHelper.memoize(variantConfiguration::getVersionCode);

            task.debuggable =
                    TaskInputHelper.memoize(
                            () -> variantConfiguration.getBuildType().isDebuggable());

            task.buildTypeName = variantConfiguration.getBuildType().getName();

            // no need to memoize, variant configuration does that already.
            task.flavorName = variantConfiguration::getFlavorName;

            task.flavorNamesWithDimensionNames =
                    TaskInputHelper.memoize(variantConfiguration::getFlavorNamesWithDimensionNames);

            task.items = TaskInputHelper.memoize(variantConfiguration::getBuildConfigItems);

            task.setSourceOutputDir(scope.getBuildConfigSourceOutputDir());

            task.checkManifestResult =
                    scope.getArtifacts()
                            .getFinalArtifactFilesIfPresent(
                                    InternalArtifactType.CHECK_MANIFEST_RESULT);
            if (scope.getVariantConfiguration().getType().isTestComponent()) {
                // in case of a test project, the manifest is generated so we need to depend
                // on its creation.
                task.dependsOn(scope.getTaskContainer().getProcessManifestTask());
            }
        }
    }
}
