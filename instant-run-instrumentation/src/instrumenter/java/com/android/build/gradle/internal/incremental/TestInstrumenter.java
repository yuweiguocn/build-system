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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.tools.utils.Unzipper;
import com.android.tools.utils.Zipper;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class TestInstrumenter {

    protected static void main(
            @NonNull String[] args, @NonNull IncrementalVisitor.VisitorBuilder visitorBuilder)
            throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Three arguments required:\n"
                            + " 1. Input jars to instrument (':'-separated)\n"
                            + " 2. Output jar file\n"
                            + " 3. Classpath (':'-separated)"
                            + " 4. Target Platform API level (Optional)");
        }

        ImmutableList.Builder<URL> classPath = ImmutableList.builder();
        for (String path : Splitter.on(':').split(args[2])) {
            classPath.add(assertIsFile(Paths.get(path)).toUri().toURL());
        }
        ImmutableList.Builder<Path> inputJars = ImmutableList.builder();
        for (String path : Splitter.on(':').split(args[0])) {
            Path jar = assertIsFile(Paths.get(path));
            classPath.add(jar.toUri().toURL());
            inputJars.add(jar);
        }
        // if the target api level is not specified, use the max integer value, which will make
        // all classes targeting a lower level.
        int targetPlatformApi = args.length > 3
                ? Integer.parseInt(args[3])
                : Integer.MAX_VALUE;

        Path outputJar = Paths.get(args[1]);

        main(targetPlatformApi, inputJars.build(), outputJar, classPath.build(), visitorBuilder);
    }

    private static Path assertIsFile(@NonNull Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(file + " is not a file.");
        }
        return file;
    }

    private static void main(
            int targetPlatformApi,
            @NonNull List<Path> inputJars,
            @NonNull Path outputJar,
            @NonNull List<URL> classpath,
            @NonNull IncrementalVisitor.VisitorBuilder visitorBuilder)
            throws IOException {

        Path inputDir = Files.createTempDirectory("instrumented_input");
        Unzipper unzipper = new Unzipper();
        for (Path input : inputJars) {
            System.err.println("Extracting " + input);
            unzipper.unzip(input.toFile(), inputDir.toFile());
        }

        URL[] classPathArray = Iterables.toArray(classpath, URL.class);

        ClassLoader classesToInstrumentLoader =
                new URLClassLoader(classPathArray, null) {
                    @Override
                    public URL getResource(String name) {
                        // Never delegate to bootstrap classes.
                        return findResource(name);
                    }
                };

        Path outputDir = Files.createTempDirectory("instrumented_output");
        ILogger logger = new StdLogger(StdLogger.Level.INFO);

        ClassLoader originalThreadContextClassLoader =
                Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classesToInstrumentLoader);

            Files.walkFileTree(
                    inputDir,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            IncrementalVisitor.instrumentClass(
                                    targetPlatformApi,
                                    inputDir.toFile(),
                                    file.toFile(),
                                    outputDir.toFile(),
                                    visitorBuilder,
                                    logger);
                            return FileVisitResult.CONTINUE;
                        }
                    });

        } finally {
            Thread.currentThread().setContextClassLoader(originalThreadContextClassLoader);
        }

        Zipper zipper = new Zipper();
        zipper.directoryToZip(outputDir.toFile(), outputJar.toFile());
    }
}
