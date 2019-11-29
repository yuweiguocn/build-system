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

package com.example.test.customplugin;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.api.variant.VariantInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Set;

public class CustomTransform extends Transform {

    @Override
    public String getName() {
        return "mycustomtransform";
    }

    @Override
    public Set<ContentType> getInputTypes() {
        return ImmutableSet.<ContentType>of(DefaultContentType.RESOURCES);
    }

    @Override
    public Set<Scope> getScopes() {
        return EnumSet.of(Scope.PROJECT, Scope.SUB_PROJECTS, Scope.EXTERNAL_LIBRARIES);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public boolean applyToVariant(VariantInfo variant) {
        // Output to stderr to assert in the test
        StringBuilder sb = new StringBuilder();
        sb.append("applyToVariant called with variant=VariantInfo(isTest: ")
                .append(variant.isTest())
                .append(", isDebuggable: ")
                .append(variant.isDebuggable())
                .append(", variantName: ")
                .append(variant.getFullVariantName())
                .append(", buildType: ")
                .append(variant.getBuildTypeName());
        for (String name : variant.getFlavorNames()) {
            sb.append(", flavor: ").append(name);
        }

        sb.append(")");
        System.err.println(sb.toString());
        return !variant.isDebuggable();
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws InterruptedException, IOException {
        final TransformOutputProvider outputProvider = invocation.getOutputProvider();
        outputProvider.deleteAll();
        // Copy inputs to outputs
        for (TransformInput ti : invocation.getInputs()) {
            for (JarInput jarInput : ti.getJarInputs()) {
                Path inputJar = jarInput.getFile().toPath();
                Path outputJar =
                        outputProvider
                                .getContentLocation(
                                        jarInput.getName(),
                                        jarInput.getContentTypes(),
                                        jarInput.getScopes(),
                                        Format.JAR)
                                .toPath();
                Files.copy(inputJar, outputJar);
                Files.createDirectories(outputJar.getParent());
            }
            for (DirectoryInput di : ti.getDirectoryInputs()) {
                Path inputDir = di.getFile().toPath();
                Path outputDir =
                        outputProvider
                                .getContentLocation(
                                        di.getName(),
                                        di.getContentTypes(),
                                        di.getScopes(),
                                        Format.DIRECTORY)
                                .toPath();
                Files.createDirectories(outputDir);
                Files.walkFileTree(
                        inputDir,
                        new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(
                                    Path dir, BasicFileAttributes attrs) throws IOException {
                                Files.createDirectory(outputDir.resolve(inputDir.relativize(dir)));
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                    throws IOException {
                                Files.copy(file, outputDir.resolve(inputDir.relativize(file)));
                                return FileVisitResult.CONTINUE;
                            }
                        });
            }
        }
        // Add a single resource file.
        Path outputDir =
                outputProvider
                        .getContentLocation(
                                "my_custom_transform",
                                getInputTypes(),
                                getScopes(),
                                Format.DIRECTORY)
                        .toPath();
        Files.createDirectories(outputDir);
        Files.write(outputDir.resolve("my_custom_transform_ran.txt"), ImmutableList.of(":)"));
    }
}
