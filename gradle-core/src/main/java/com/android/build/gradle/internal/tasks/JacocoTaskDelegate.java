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

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

/** Delegate for {@link JacocoTask}. */
public class JacocoTaskDelegate {

    private static final Pattern CLASS_PATTERN = Pattern.compile(".*\\.class$");
    // META-INF/*.kotlin_module files need to be copied to output so they show up
    // in the intermediate classes jar.
    private static final Pattern KOTLIN_MODULE_PATTERN =
            Pattern.compile("^META-INF/.*\\.kotlin_module$");

    @NonNull private final FileCollection jacocoAntTaskConfiguration;
    @NonNull private final File output;
    @NonNull private final BuildableArtifact inputClasses;

    public JacocoTaskDelegate(
            @NonNull FileCollection jacocoAntTaskConfiguration,
            @NonNull File output,
            @NonNull BuildableArtifact inputClasses) {
        this.jacocoAntTaskConfiguration = jacocoAntTaskConfiguration;
        this.output = output;
        this.inputClasses = inputClasses;
    }

    public void run(@NonNull WorkerExecutor executor, @NonNull IncrementalTaskInputs inputs)
            throws IOException {
        for (File file : inputClasses.getFiles()) {
            if (file.exists()) {
                Preconditions.checkState(
                        file.isDirectory(),
                        "Jacoco instrumentation supports only directory inputs: %s",
                        file.toString());
            }
        }

        if (inputs.isIncremental()) {
            processIncrementally(executor, inputs);
        } else {
            for (File file : inputClasses.getFiles()) {
                Map<Action, List<File>> nonIncToProcess =
                        getFilesForInstrumentationNonIncrementally(file, output);
                executor.submit(
                        JacocoWorkerAction.class,
                        workerConfiguration -> {
                            workerConfiguration.setIsolationMode(IsolationMode.CLASSLOADER);
                            workerConfiguration.classpath(jacocoAntTaskConfiguration.getFiles());
                            workerConfiguration.setParams(nonIncToProcess, file, output);
                        });
            }
        }

        executor.await();
    }

    private void processIncrementally(
            @NonNull WorkerExecutor executor, @NonNull IncrementalTaskInputs inputs)
            throws IOException {
        Multimap<Path, Path> basePathToRemove =
                Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);
        Multimap<Path, Path> basePathToProcess =
                Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

        Set<Path> baseDirs = new HashSet<>(inputClasses.getFiles().size());
        for (File file : inputClasses.getFiles()) {
            baseDirs.add(file.toPath());
        }

        inputs.outOfDate(
                info -> {
                    Path file = info.getFile().toPath();
                    Path baseDir = findBase(baseDirs, file);
                    if (info.isAdded()) {
                        basePathToProcess.put(baseDir, file);
                    } else if (info.isModified()) {
                        basePathToRemove.put(baseDir, file);
                        basePathToProcess.put(baseDir, file);
                    } else if (info.isRemoved()) {
                        basePathToRemove.put(baseDir, file);
                    }
                });
        inputs.removed(
                info -> {
                    Path file = info.getFile().toPath();
                    Path baseDir = findBase(baseDirs, file);
                    basePathToRemove.put(baseDir, file);
                });

        // remove old output
        for (Path basePath : basePathToRemove.keys()) {
            for (Path toRemove : basePathToRemove.get(basePath)) {
                Action action = calculateAction(toRemove.toFile(), basePath.toFile());
                if (action == Action.IGNORE) {
                    continue;
                }

                Path outputPath = getOutputPath(basePath, toRemove, output.toPath());
                PathUtils.deleteRecursivelyIfExists(outputPath);
            }
        }

        // process changes
        for (Path basePath : basePathToProcess.keys()) {
            Map<Action, List<File>> toProcess = new EnumMap<>(Action.class);
            for (Path changed : basePathToProcess.get(basePath)) {
                Action action = calculateAction(changed.toFile(), basePath.toFile());
                if (action == Action.IGNORE) {
                    continue;
                }

                List<File> byAction = toProcess.getOrDefault(action, new ArrayList<>());
                byAction.add(changed.toFile());
                toProcess.put(action, byAction);
            }

            executor.submit(
                    JacocoWorkerAction.class,
                    workerConfiguration -> {
                        workerConfiguration.setIsolationMode(IsolationMode.CLASSLOADER);
                        workerConfiguration.classpath(jacocoAntTaskConfiguration.getFiles());
                        workerConfiguration.setParams(toProcess, basePath.toFile(), output);
                    });
        }
    }

    @NonNull
    private static Path findBase(@NonNull Set<Path> baseDirs, @NonNull Path file) {
        for (Path baseDir : baseDirs) {
            if (file.startsWith(baseDir)) {
                return baseDir;
            }
        }

        throw new RuntimeException(
                String.format(
                        "Unable to find base directory for %s. List of base dirs: %s",
                        file,
                        baseDirs.stream().map(Path::toString).collect(Collectors.joining(","))));
    }

    @NonNull
    private static Path getOutputPath(
            @NonNull Path baseDir, @NonNull Path inputFile, @NonNull Path outputBaseDir) {
        Path relativePath = baseDir.relativize(inputFile);
        return outputBaseDir.resolve(relativePath);
    }

    @NonNull
    private static Map<Action, List<File>> getFilesForInstrumentationNonIncrementally(
            @NonNull File inputDir, @NonNull File outputDir) throws IOException {
        Map<Action, List<File>> toProcess = Maps.newHashMap();
        FileUtils.cleanOutputDir(outputDir);
        Iterable<File> files = FileUtils.getAllFiles(inputDir);
        for (File inputFile : files) {
            Action fileAction = calculateAction(inputFile, inputDir);
            switch (fileAction) {
                case COPY:
                    // fall through
                case INSTRUMENT:
                    List<File> actionFiles = toProcess.getOrDefault(fileAction, new ArrayList<>());
                    actionFiles.add(inputFile);
                    toProcess.put(fileAction, actionFiles);
                    break;
                case IGNORE:
                    // do nothing
                    break;
                default:
                    throw new AssertionError("Unsupported Action: " + fileAction);
            }
        }
        return toProcess;
    }

    private static Action calculateAction(@NonNull File inputFile, @NonNull File inputDir) {
        final String inputRelativePath =
                FileUtils.toSystemIndependentPath(
                        FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
        for (Pattern pattern : Action.COPY.getPatterns()) {
            if (pattern.matcher(inputRelativePath).matches()) {
                return Action.COPY;
            }
        }
        for (Pattern pattern : Action.INSTRUMENT.getPatterns()) {
            if (pattern.matcher(inputRelativePath).matches()) {
                return Action.INSTRUMENT;
            }
        }
        return Action.IGNORE;
    }

    /** The possible actions which can happen to an input file */
    private enum Action {

        /** The file is just copied to the transform output. */
        COPY(KOTLIN_MODULE_PATTERN),

        /** The file is ignored. */
        IGNORE(),

        /** The file is instrumented and added to the transform output. */
        INSTRUMENT(CLASS_PATTERN);

        private final ImmutableList<Pattern> patterns;

        /**
         * @param patterns Patterns are compared to files' relative paths to determine if they
         *     undergo the corresponding action.
         */
        Action(@NonNull Pattern... patterns) {
            ImmutableList.Builder<Pattern> builder = new ImmutableList.Builder<>();
            for (Pattern pattern : patterns) {
                Preconditions.checkNotNull(pattern);
                builder.add(pattern);
            }
            this.patterns = builder.build();
        }

        @NonNull
        ImmutableList<Pattern> getPatterns() {
            return patterns;
        }
    }

    private static class JacocoWorkerAction implements Runnable {
        @NonNull private Map<Action, List<File>> inputs;
        @NonNull private File inputDir;
        @NonNull private File outputDir;

        @Inject
        public JacocoWorkerAction(
                @NonNull Map<Action, List<File>> inputs,
                @NonNull File inputDir,
                @NonNull File outputDir) {
            this.inputs = inputs;
            this.inputDir = inputDir;
            this.outputDir = outputDir;
        }

        @Override
        public void run() {
            Instrumenter instrumenter =
                    new Instrumenter(new OfflineInstrumentationAccessGenerator());
            for (File toInstrument : inputs.getOrDefault(Action.INSTRUMENT, ImmutableList.of())) {
                try (InputStream inputStream =
                        Files.asByteSource(toInstrument).openBufferedStream()) {
                    byte[] instrumented =
                            instrumenter.instrument(inputStream, toInstrument.toString());
                    File outputFile =
                            new File(outputDir, FileUtils.relativePath(toInstrument, inputDir));
                    Files.createParentDirs(outputFile);
                    Files.write(instrumented, outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Unable to instrument file with Jacoco: " + toInstrument, e);
                }
            }

            for (File toCopy : inputs.getOrDefault(Action.COPY, ImmutableList.of())) {
                File outputFile = new File(outputDir, FileUtils.relativePath(toCopy, inputDir));
                try {
                    Files.createParentDirs(outputFile);
                    Files.copy(toCopy, outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to copy file: " + toCopy, e);
                }
            }
        }
    }
}
