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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.GradleBuildLoggingEnvironment;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueReporter;
import com.android.ide.common.process.BuildCommandException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does build steps with them.
 *
 * <p>It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
public class ExternalNativeBuildTask extends AndroidBuilderTask {

    private List<File> nativeBuildConfigurationsJsons;

    private File soFolder;

    private File objFolder;

    private Set<String> targets;

    private Map<Abi, File> stlSharedObjectFiles;

    private GradleBuildVariant.Builder stats;

    @TaskAction
    void build() throws BuildCommandException, IOException {
        try (GradleBuildLoggingEnvironment ignore =
                new GradleBuildLoggingEnvironment(getLogger(), getVariantName())) {
            buildImpl();
        }
    }

    private void buildImpl() throws BuildCommandException, IOException {
        info("starting build");
        checkNotNull(getVariantName());
        info("reading expected JSONs");
        List<NativeBuildConfigValueMini> miniConfigs = getNativeBuildConfigValueMinis();
        info("done reading expected JSONs");

        List<String> buildCommands = Lists.newArrayList();
        List<String> libraryNames = Lists.newArrayList();
        List<File> outputFolders = Lists.newArrayList();
        if (targets.isEmpty()) {
            info("executing build commands for targets that produce .so files or executables");
        } else {
            // Check the resulting JSON targets against the targets specified in ndkBuild.targets or
            // cmake.targets. If a target name specified by the user isn't present then provide an
            // error to the user that lists the valid target names.
            info("executing build commands for targets: '%s'", Joiner.on(", ").join(targets));

            // Search libraries for matching targets.
            Set<String> matchingTargets = Sets.newHashSet();
            Set<String> unmatchedTargets = Sets.newHashSet();
            for (NativeBuildConfigValueMini config : miniConfigs) {
                for (NativeLibraryValueMini libraryValue : config.libraries.values()) {
                    if (targets.contains(libraryValue.artifactName)) {
                        matchingTargets.add(libraryValue.artifactName);
                    } else {
                        unmatchedTargets.add(libraryValue.artifactName);
                    }
                }
            }

            // All targets must be found or it's a build error
            for (String target : targets) {
                if (!matchingTargets.contains(target)) {
                    throw new GradleException(
                            String.format(
                                    "Unexpected native build target %s. Valid values are: %s",
                                    target, Joiner.on(", ").join(unmatchedTargets)));
                }
            }
        }

        for (int miniConfigIndex = 0; miniConfigIndex < miniConfigs.size(); ++miniConfigIndex) {
            NativeBuildConfigValueMini config = miniConfigs.get(miniConfigIndex);
            info("evaluate miniconfig");
            if (config.libraries.isEmpty()) {
                info("no libraries");
                continue;
            }
            for (String libraryName : config.libraries.keySet()) {
                info("evaluate library %s", libraryName);
                NativeLibraryValueMini libraryValue = config.libraries.get(libraryName);
                if (!targets.isEmpty() && !targets.contains(libraryValue.artifactName)) {
                    info(
                            "not building target %s because it isn't in targets set",
                            libraryValue.artifactName);
                    continue;
                }
                if (Strings.isNullOrEmpty(libraryValue.buildCommand)) {
                    // This can happen when there's an externally referenced library.
                    info(
                            "not building target %s because there was no build command for it",
                            libraryValue.artifactName);
                    continue;
                }
                if (targets.isEmpty()) {
                    if (libraryValue.output == null) {
                        info(
                                "not building target %s because no targets are specified and "
                                        + "library build output file is null",
                                libraryValue.artifactName);
                        continue;
                    }

                    String extension = Files.getFileExtension(libraryValue.output.getName());
                    switch (extension) {
                        case "so":
                            info(
                                    "building target library %s because no targets are "
                                            + "specified.",
                                    libraryValue.artifactName);
                            break;
                        case "":
                            info(
                                    "building target executable %s because no targets are "
                                            + "specified.",
                                    libraryValue.artifactName);
                            break;
                        default:
                            info(
                                    "not building target %s because the type cannot be "
                                            + "determined.",
                                    libraryValue.artifactName);
                            continue;
                    }
                }

                buildCommands.add(libraryValue.buildCommand);
                libraryNames.add(libraryValue.artifactName + " " + libraryValue.abi);
                outputFolders.add(
                        nativeBuildConfigurationsJsons.get(miniConfigIndex).getParentFile());
                info("about to build %s", libraryValue.buildCommand);
            }
        }

        executeProcessBatch(libraryNames, buildCommands, outputFolders);

        info("check expected build outputs");
        for (NativeBuildConfigValueMini config : miniConfigs) {
            for (String library : config.libraries.keySet()) {
                NativeLibraryValueMini libraryValue = config.libraries.get(library);
                String libraryName = libraryValue.artifactName + " " + libraryValue.abi;
                checkNotNull(libraryValue);
                checkNotNull(libraryValue.output);
                checkState(!Strings.isNullOrEmpty(libraryValue.artifactName));
                if (!targets.isEmpty() && !targets.contains(libraryValue.artifactName)) {
                    continue;
                }
                if (!libraryNames.contains(libraryName)) {
                    // Only need to check existence of output files we expect to create
                    continue;
                }
                if (!libraryValue.output.exists()) {
                    throw new GradleException(
                            String.format(
                                    "Expected output file at %s for target %s"
                                            + " but there was none",
                                    libraryValue.output, libraryValue.artifactName));
                }
                if (libraryValue.abi == null) {
                    throw new GradleException("Expected NativeLibraryValue to have non-null abi");
                }

                // If the build chose to write the library output somewhere besides objFolder
                // then copy to objFolder (reference b.android.com/256515)
                //
                // Since there is now a .so file outside of the standard build/ folder we have to
                // consider clean. Here's how the two files are covered.
                // (1) Gradle plugin deletes the build/ folder. This covers the destination of the
                //     copy.
                // (2) ExternalNativeCleanTask calls the individual clean targets for everything
                //     that was built. This should cover the source of the copy but it is up to the
                //     CMakeLists.txt or Android.mk author to ensure this.
                Abi abi = Abi.getByName(libraryValue.abi);
                if (abi == null) {
                    throw new RuntimeException(
                            String.format("Unknown ABI seen %s", libraryValue.abi));
                }
                File expectedOutputFile =
                        FileUtils.join(objFolder, abi.getName(), libraryValue.output.getName());
                if (!FileUtils.isSameFile(libraryValue.output, expectedOutputFile)) {
                    info(
                            "external build set its own library output location for '%s', "
                                    + "copy to expected location",
                            libraryValue.output.getName());

                    if (expectedOutputFile.getParentFile().mkdirs()) {
                        info("created folder %s", expectedOutputFile.getParentFile());
                    }
                    info("copy file %s to %s", libraryValue.output, expectedOutputFile);
                    Files.copy(libraryValue.output, expectedOutputFile);
                }
            }
        }

        if (!stlSharedObjectFiles.isEmpty()) {
            info("copy STL shared object files");
            for (Abi abi : stlSharedObjectFiles.keySet()) {
                File stlSharedObjectFile = checkNotNull(stlSharedObjectFiles.get(abi));
                File objAbi =
                        FileUtils.join(objFolder, abi.getName(), stlSharedObjectFile.getName());
                if (!objAbi.getParentFile().isDirectory()) {
                    // A build failure can leave the obj/abi folder missing. Just note that case
                    // and continue without copying STL.
                    info(
                            "didn't copy STL file to %s because that folder wasn't created "
                                    + "by the build ",
                            objAbi.getParentFile());
                } else {
                    info("copy file %s to %s", stlSharedObjectFile, objAbi);
                    Files.copy(stlSharedObjectFile, objAbi);
                }
            }
        }

        info("build complete");
    }

    /**
     * Get native build config minis. Also gather stats if they haven't already been gathered for
     * this variant
     *
     * @return the mini configs
     */
    private List<NativeBuildConfigValueMini> getNativeBuildConfigValueMinis() throws IOException {
        // Gather stats only if they haven't been gathered during model build
        if (stats.getNativeBuildConfigCount() == 0) {
            return AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
                    nativeBuildConfigurationsJsons, stats);
        }
        return AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
                nativeBuildConfigurationsJsons, null);
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private void executeProcessBatch(
            @NonNull List<String> libraryNames,
            @NonNull List<String> commands,
            @NonNull List<File> output)
            throws BuildCommandException, IOException {
        // Order of building doesn't matter to final result but building in reverse order causes
        // the dependencies to be built first for CMake and ndk-build. This gives better progress
        // visibility to the user because they will see "building XXXXX.a" before
        // "building XXXXX.so". Nicer still would be to have the dependency information in the JSON
        // so we can build in toposort order.
        for (int library = libraryNames.size() - 1; library >= 0; --library) {
            String libraryName = libraryNames.get(library);
            getLogger().lifecycle(String.format("Build %s", libraryName));
            String command = commands.get(library);
            List<String> tokens = StringHelper.tokenizeCommandLineToEscaped(command);
            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
            processBuilder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                processBuilder.addArgs(tokens.get(i));
            }
            info("%s", processBuilder);
            createProcessOutputJunction(
                            output.get(library),
                            "android_gradle_build_" + libraryName.replace(" ", "_"),
                            processBuilder,
                            getBuilder(),
                            "")
                    .logStderrToInfo()
                    .logStdoutToInfo()
                    .execute();
        }
    }

    @NonNull
    @SuppressWarnings("unused") // Exposed in Variants API
    public File getSoFolder() {
        return soFolder;
    }

    private void setSoFolder(@NonNull File soFolder) {
        this.soFolder = soFolder;
    }

    private void setTargets(@NonNull Set<String> targets) {
        this.targets = targets;
    }

    @NonNull
    @SuppressWarnings("unused") // Exposed in Variants API
    public File getObjFolder() {
        return objFolder;
    }

    private void setObjFolder(@NonNull File objFolder) {
        this.objFolder = objFolder;
    }

    @NonNull
    @SuppressWarnings("unused") // Exposed in Variants API
    public List<File> getNativeBuildConfigurationsJsons() {
        return nativeBuildConfigurationsJsons;
    }

    private void setNativeBuildConfigurationsJsons(
            @NonNull List<File> nativeBuildConfigurationsJsons) {
        this.nativeBuildConfigurationsJsons = nativeBuildConfigurationsJsons;
    }

    private void setStlSharedObjectFiles(Map<Abi, File> stlSharedObjectFiles) {
        this.stlSharedObjectFiles = stlSharedObjectFiles;
    }

    public static class CreationAction extends VariantTaskCreationAction<ExternalNativeBuildTask> {
        @Nullable private final String buildTargetAbi;
        @NonNull private final ExternalNativeJsonGenerator generator;
        @NonNull private final TaskProvider<? extends Task> generateTask;
        @NonNull private final AndroidBuilder androidBuilder;

        public CreationAction(
                @Nullable String buildTargetAbi,
                @NonNull ExternalNativeJsonGenerator generator,
                @NonNull TaskProvider<? extends Task> generateTask,
                @NonNull VariantScope scope,
                @NonNull AndroidBuilder androidBuilder) {
            super(scope);
            this.buildTargetAbi = buildTargetAbi;
            this.generator = generator;
            this.generateTask = generateTask;
            this.androidBuilder = androidBuilder;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("externalNativeBuild");
        }

        @NonNull
        @Override
        public Class<ExternalNativeBuildTask> getType() {
            return ExternalNativeBuildTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ExternalNativeBuildTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().getExternalNativeBuildTasks().add(taskProvider);
            getVariantScope().getTaskContainer().setExternalNativeBuildTask(taskProvider);
        }

        @Override
        public void configure(@NonNull ExternalNativeBuildTask task) {
            super.configure(task);
            VariantScope scope = getVariantScope();
            final BaseVariantData variantData = scope.getVariantData();
            final Set<String> targets;
            CoreExternalNativeBuildOptions nativeBuildOptions =
                    variantData.getVariantConfiguration().getExternalNativeBuildOptions();
            switch (generator.getNativeBuildSystem()) {
                case CMAKE: {
                    CoreExternalNativeCmakeOptions options = checkNotNull(nativeBuildOptions
                            .getExternalNativeCmakeOptions());
                    targets = options.getTargets();
                    break;
                }
                case NDK_BUILD: {
                    CoreExternalNativeNdkBuildOptions options = checkNotNull(nativeBuildOptions
                            .getExternalNativeNdkBuildOptions());
                    targets = options.getTargets();
                    break;
                }
                default:
                    throw new RuntimeException("Unexpected native build system "
                            + generator.getNativeBuildSystem().getName());
            }
            task.setStlSharedObjectFiles(generator.getStlSharedObjectFiles());
            task.setTargets(targets);
            task.setSoFolder(generator.getSoFolder());
            task.setObjFolder(generator.getObjFolder());
            task.stats = generator.stats;
            if (Strings.isNullOrEmpty(buildTargetAbi)) {
                task.setNativeBuildConfigurationsJsons(
                        generator.getNativeBuildConfigurationsJsons());
            } else {
                // Android Studio has requested a particular ABI to build or the user has specified
                // one from the command-line like with: -Pandroid.injected.build.abi=x86
                //
                // In this case, the requested ABI overrides and abiFilters in the variantConfig.
                // So this can build ABIs that aren't specified in any variant.
                //
                // It is possible for multiple ABIs to be passed through buildTargetAbi. In this
                // case, take the first. It is preferred.
                List<File> expectedJson =
                        ExternalNativeBuildTaskUtils.getOutputJsons(
                                generator.getJsonFolder(),
                                Arrays.asList(buildTargetAbi.split(",")));
                // Remove JSONs that won't be created by the generator.
                expectedJson.retainAll(generator.getNativeBuildConfigurationsJsons());
                // If no JSONs remain then issue a warning and proceed with no-op build.
                if (expectedJson.isEmpty()) {
                    androidBuilder
                            .getIssueReporter()
                            .reportWarning(
                                    EvalIssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                                    String.format(
                                            "Targeted device ABI or comma-delimited ABIs [%s] is not"
                                                    + " one of [%s]. Nothing to build.",
                                            buildTargetAbi,
                                            Joiner.on(", ")
                                                    .join(
                                                            generator
                                                                    .getAbis()
                                                                    .stream()
                                                                    .map(Abi::getName)
                                                                    .collect(Collectors.toList()))),
                                    scope.getFullVariantName());
                    task.setNativeBuildConfigurationsJsons(ImmutableList.of());
                } else {
                    // Take the first JSON that matched the build configuration
                    task.setNativeBuildConfigurationsJsons(
                            Lists.newArrayList(expectedJson.iterator().next()));
                }
            }

            task.setAndroidBuilder(androidBuilder);

            task.dependsOn(
                    generateTask, scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, JNI));
        }
    }
}
