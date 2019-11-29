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

import static com.android.build.gradle.internal.cxx.configure.LoggingEnvironmentKt.info;
import static com.android.build.gradle.internal.cxx.process.ProcessOutputJunctionKt.createProcessOutputJunction;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.GradleBuildLoggingEnvironment;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does clean steps with them.
 *
 * <p>It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
public class ExternalNativeCleanTask extends AndroidBuilderTask {

    private List<File> nativeBuildConfigurationsJsons;

    private File objFolder;

    private Map<Abi, File> stlSharedObjectFiles;

    @TaskAction
    void clean() throws ProcessException, IOException {
        try (GradleBuildLoggingEnvironment ignore =
                new GradleBuildLoggingEnvironment(getLogger(), getVariantName())) {
            info("starting clean");
            info("finding existing JSONs");

            List<File> existingJsons = Lists.newArrayList();
            for (File json : nativeBuildConfigurationsJsons) {
                if (json.isFile()) {
                    existingJsons.add(json);
                }
            }

            List<NativeBuildConfigValueMini> configValueList =
                    AndroidBuildGradleJsons.getNativeBuildMiniConfigs(existingJsons, null);
            List<String> cleanCommands = Lists.newArrayList();
            List<String> targetNames = Lists.newArrayList();
            for (NativeBuildConfigValueMini config : configValueList) {
                cleanCommands.addAll(config.cleanCommands);
                Set<String> targets = Sets.newHashSet();
                for (NativeLibraryValueMini library : config.libraries.values()) {
                    targets.add(String.format("%s %s", library.artifactName, library.abi));
                }
                targetNames.add(Joiner.on(",").join(targets));
            }
            info("about to execute %s clean commands", cleanCommands.size());
            executeProcessBatch(cleanCommands, targetNames);

            if (!stlSharedObjectFiles.isEmpty()) {
                info("remove STL shared object files");
                for (Abi abi : stlSharedObjectFiles.keySet()) {
                    File stlSharedObjectFile = checkNotNull(stlSharedObjectFiles.get(abi));
                    File objAbi =
                            FileUtils.join(objFolder, abi.getName(), stlSharedObjectFile.getName());

                    if (objAbi.delete()) {
                        info("removed file %s", objAbi);
                    } else {
                        info("failed to remove file %s", objAbi);
                    }
                }
            }
            info("clean complete");
        }
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private void executeProcessBatch(
            @NonNull List<String> commands, @NonNull List<String> targetNames)
            throws ProcessException, IOException {
        for (int commandIndex = 0; commandIndex < commands.size(); ++commandIndex) {
            String command = commands.get(commandIndex);
            String target = targetNames.get(commandIndex);
            getLogger().lifecycle(String.format("Clean %s", target));
            List<String> tokens = StringHelper.tokenizeCommandLineToEscaped(command);
            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
            processBuilder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                processBuilder.addArgs(tokens.get(i));
            }
            info("%s", processBuilder);
            createProcessOutputJunction(
                            this.objFolder,
                            "android_gradle_clean_" + commandIndex,
                            processBuilder,
                            getBuilder(),
                            "")
                    .logStderrToInfo()
                    .logStdoutToInfo()
                    .execute();
        }
    }

    public static class CreationAction extends TaskCreationAction<ExternalNativeCleanTask> {
        @NonNull
        private final ExternalNativeJsonGenerator generator;
        @NonNull private final VariantScope variantScope;

        public CreationAction(
                @NonNull ExternalNativeJsonGenerator generator, @NonNull VariantScope scope) {
            this.generator = generator;
            this.variantScope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("externalNativeBuildClean");
        }

        @NonNull
        @Override
        public Class<ExternalNativeCleanTask> getType() {
            return ExternalNativeCleanTask.class;
        }

        @Override
        public void configure(@NonNull ExternalNativeCleanTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            // Attempt to clean every possible ABI even those that aren't currently built.
            // This covers cases where user has changed abiFilters or platform. We don't want
            // to leave stale results hanging around.
            List<String> abiNames = Lists.newArrayList();
            for(Abi abi : NdkHandler.getAbiList()) {
                abiNames.add(abi.getName());
            }
            task.setAndroidBuilder(variantScope.getGlobalScope().getAndroidBuilder());
            task.nativeBuildConfigurationsJsons =
                    ExternalNativeBuildTaskUtils.getOutputJsons(
                            generator.getJsonFolder(), abiNames);
            task.stlSharedObjectFiles = generator.getStlSharedObjectFiles();
            task.objFolder = generator.getObjFolder();
        }
    }
}
