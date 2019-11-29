/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.build.gradle.internal.scope.InternalArtifactType.APK_FOR_LOCAL_TEST;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.PROCESSED_RES;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class PackageForUnitTest extends AndroidVariantTask {
    BuildableArtifact resApk;
    BuildableArtifact mergedAssets;
    File apkForUnitTest;

    @TaskAction
    public void generateApkForUnitTest() throws IOException {
        // this can certainly be optimized by making it incremental...

        FileUtils.copyFile(apkFrom(resApk), apkForUnitTest);

        URI uri = URI.create("jar:" + apkForUnitTest.toURI());
        try (FileSystem apkFs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path apkAssetsPath = apkFs.getPath("/assets");
            for (File mergedAssetsDir : mergedAssets.getFiles()) {
                final Path mergedAssetsPath = mergedAssetsDir.toPath();
                Files.walkFileTree(mergedAssetsPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path,
                            BasicFileAttributes basicFileAttributes)
                            throws IOException {
                        String relativePath = PathUtils.toSystemIndependentPath(
                                mergedAssetsPath.relativize(path));
                        Path destPath = apkAssetsPath.resolve(relativePath);
                        Files.createDirectories(destPath.getParent());
                        Files.copy(path, destPath);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    @InputFiles
    public BuildableArtifact getResApk() {
        return resApk;
    }

    @InputFiles
    public BuildableArtifact getMergedAssets() {
        return mergedAssets;
    }

    @OutputFile
    public File getApkForUnitTest() {
        return apkForUnitTest;
    }

    @NonNull
    private static File apkFrom(BuildableArtifact compiledResourcesZip) {
        return Iterables.getOnlyElement(
                ExistingBuildElements.from(PROCESSED_RES, compiledResourcesZip))
                .getOutputFile();
    }

    public static class CreationAction extends VariantTaskCreationAction<PackageForUnitTest> {
        private File apkForUnitTest;

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("package", "ForUnitTest");
        }

        @NonNull
        @Override
        public Class<PackageForUnitTest> getType() {
            return PackageForUnitTest.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            apkForUnitTest =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(APK_FOR_LOCAL_TEST, taskName, "apk-for-local-test.ap_");
        }

        @Override
        public void configure(@NonNull PackageForUnitTest task) {
            super.configure(task);

            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            task.resApk = artifacts.getArtifactFiles(PROCESSED_RES);
            task.mergedAssets = artifacts.getArtifactFiles(MERGED_ASSETS);
            task.apkForUnitTest = apkForUnitTest;
        }
    }
}
