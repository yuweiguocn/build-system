/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this/ file except in compliance with the License.
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

import static com.android.build.gradle.internal.scope.InternalArtifactType.APK_FOR_LOCAL_TEST;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_RES;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Supplier;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates the {@code test_config.properties} file that is put on the classpath for running unit
 * tests.
 *
 * <p>See DSL documentation in {@link TestOptions.UnitTestOptions#isIncludeAndroidResources()}
 */
public class GenerateTestConfig extends AndroidVariantTask {

    BuildableArtifact resourcesDirectory;
    BuildableArtifact assets;
    Path sdkHome;
    File generatedJavaResourcesDirectory;
    ApkData mainApkInfo;
    Provider<Directory> manifests;
    BuildableArtifact compiledResourcesZip;
    Supplier<String> packageForR;

    @Input
    public ApkData getMainApkInfo() {
        return mainApkInfo;
    }

    @InputFiles
    public Provider<Directory> getManifests() {
        return manifests;
    }

    @TaskAction
    public void generateTestConfig() throws IOException {
        checkNotNull(assets);
        checkNotNull(sdkHome);

        BuildOutput output =
                ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, manifests)
                        .element(mainApkInfo);
        generateTestConfigForOutput(
                Iterables.getOnlyElement(assets).toPath().toAbsolutePath(),
                resourcesDirectory == null
                        ? null
                        : BuildableArtifactUtil.singleFile(resourcesDirectory)
                                .toPath()
                                .toAbsolutePath(),
                sdkHome,
                getPackageForR(),
                checkNotNull(output, "Unable to find manifest output").getOutputFile().toPath(),
                compiledResourcesZip,
                generatedJavaResourcesDirectory.toPath().toAbsolutePath());
    }

    @VisibleForTesting
    static void generateTestConfigForOutput(
            @NonNull Path assetsDir,
            @Nullable Path resDir,
            @NonNull Path sdkHome,
            @NonNull String packageForR,
            @NonNull Path manifest,
            @Nullable BuildableArtifact compiledResourcesZip,
            @NonNull Path outputDir)
            throws IOException {

        Properties properties = new Properties();
        properties.setProperty("android_sdk_home", sdkHome.toAbsolutePath().toString());
        if (resDir != null) {
            properties.setProperty("android_merged_resources", resDir.toAbsolutePath().toString());
        }
        properties.setProperty("android_merged_manifest", manifest.toAbsolutePath().toString());
        properties.setProperty("android_merged_assets", assetsDir.toAbsolutePath().toString());
        if (compiledResourcesZip != null) {
            properties.setProperty("android_resource_apk",
                    apkFrom(compiledResourcesZip).getPath());
        }
        properties.setProperty("android_custom_package", packageForR);

        Path output =
                outputDir
                        .resolve("com")
                        .resolve("android")
                        .resolve("tools")
                        .resolve("test_config.properties");
        Files.createDirectories(output.getParent());

        try (Writer writer = Files.newBufferedWriter(output)) {
            properties.store(writer, "# Generated by the Android Gradle Plugin");
        }
    }

    @Input // No need for @InputDirectory, we only care about the path.
    @Optional
    public String getResourcesDirectory() {
        if (resourcesDirectory == null) {
            return null;
        }
        return BuildableArtifactUtil.singleFile(resourcesDirectory).getPath();
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getAssets() {
        return Iterables.getOnlyElement(assets).getPath();
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getSdkHome() {
        return sdkHome.toString();
    }

    @Optional
    @Input
    public String getCompiledResourcesZip() {
        if (compiledResourcesZip == null) {
            return null;
        }
        return apkFrom(compiledResourcesZip).getPath();
    }

    @NonNull
    private static File apkFrom(BuildableArtifact compiledResourcesZip) {
        return Iterables.getOnlyElement(compiledResourcesZip.getFiles());
    }

    @OutputDirectory
    public File getOutputFile() {
        return generatedJavaResourcesDirectory;
    }

    @Input
    public String getPackageForR() {
        return packageForR.get();
    }

    public static class CreationAction extends VariantTaskCreationAction<GenerateTestConfig> {

        @NonNull private final VariantScope testedScope;
        private File generatedJavaResourcesDirectory;

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
            this.testedScope =
                    Preconditions.checkNotNull(
                            scope.getTestedVariantData(), "Not a unit test variant.")
                            .getScope();
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("generate", "Config");
        }

        @NonNull
        @Override
        public Class<GenerateTestConfig> getType() {
            return GenerateTestConfig.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            generatedJavaResourcesDirectory =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY,
                                    taskName,
                                    "out");
        }

        @Override
        public void configure(@NonNull GenerateTestConfig task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            // we don't actually consume the task, only the path, so make a manual dependency
            // on the filecollections.
            task.manifests = testedScope.getArtifacts().getFinalProduct(MERGED_MANIFESTS);

            boolean enableBinaryResources =
                    scope.getGlobalScope().getProjectOptions().get(
                            BooleanOption.ENABLE_UNIT_TEST_BINARY_RESOURCES);
            if (enableBinaryResources) {
                task.compiledResourcesZip = scope
                        .getArtifacts().getFinalArtifactFiles(APK_FOR_LOCAL_TEST);
                task.dependsOn(task.compiledResourcesZip);
            } else {
                task.resourcesDirectory = scope.getArtifacts().getFinalArtifactFiles(MERGED_RES);
                task.dependsOn(task.resourcesDirectory);
            }

            task.assets = testedScope.getArtifacts().getFinalArtifactFiles(MERGED_ASSETS);
            task.dependsOn(task.assets);
            task.mainApkInfo = testedScope.getOutputScope().getMainSplit();
            task.sdkHome =
                    Paths.get(scope.getGlobalScope().getAndroidBuilder().getTarget().getLocation());
            task.generatedJavaResourcesDirectory = generatedJavaResourcesDirectory;
            task.packageForR = testedScope.getVariantConfiguration()::getOriginalApplicationId;
        }
    }
}
