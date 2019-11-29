/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.crash.PluginCrashReporter;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

/**
 * This transform processes dex archives, {@link ExtendedContentType#DEX_ARCHIVE}, and merges them
 * to final DEX file(s).
 *
 * <p>It consumes all streams having one of the {@link
 * TransformManager#SCOPE_FULL_WITH_IR_FOR_DEXING} scopes, and {@link
 * ExtendedContentType#DEX_ARCHIVE} type. Output it produces has {@link
 * TransformManager#CONTENT_DEX} type.
 *
 * <p>This transform will try to get incremental updates about its inputs (see {@link
 * #isIncremental()}). However, in {@link DexingType#MONO_DEX} and {@link
 * DexingType#LEGACY_MULTIDEX} modes, we will need to pass entire list of dex archives for merging.
 * This includes inputs that have not changed as well, as merged does not support incremental
 * merging of DEX files currently. Therefore, incremental and full build are the same in these two
 * modes.
 *
 * <p>In {@link DexingType#NATIVE_MULTIDEX} mode, we will process only updated dex archives in the
 * following way. For full builds, all external jar libraries will be merged to DEX file(s).
 * Remaining inputs will produce a DEX file per input i.e. dex archive. Reason for this is that the
 * external libraries rarely change, and native multidex mode on android L does not support more
 * than 100 DEX files (see <a href="http://b.android.com/233093">http://b.android.com/233093</a>).
 * This means that in the incremental case, if the a dex archive of an external library has changed,
 * we will re-merge all external libraries again. If a dex archive of other type of input has
 * changed, we will re-merge only that dex archive. For Android L, due to previously mentioned dex
 * file number limit, we might merge all directory inputs and all non-external jar inputs in two
 * separate dex merger invocations (see {@link #shouldMergeInputsForNative(Collection, Collection)}.
 */
public class DexMergerTransform extends Transform {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(DexMergerTransform.class);
    @VisibleForTesting public static final int ANDROID_L_MAX_DEX_FILES = 100;
    // We assume the maximum number of dexes that will be produced from the external dependencies is
    // EXTERNAL_DEPS_DEX_FILES, so the remaining ANDROID_L_MAX_DEX_FILES - EXTERNAL_DEPS_DEX_FILES
    // can be used for the remaining inputs. This is a generous assumption that 50 completely full
    // dex files will be needed for the external dependencies.
    @VisibleForTesting public static final int EXTERNAL_DEPS_DEX_FILES = 50;

    @NonNull private final DexingType dexingType;
    @Nullable private final BuildableArtifact mainDexListFile;
    @NonNull private final BuildableArtifact duplicateClassesCheck;
    @NonNull private final DexMergerTool dexMerger;
    private final int minSdkVersion;
    private final boolean isDebuggable;
    @NonNull private final MessageReceiver messageReceiver;
    @NonNull private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final boolean includeFeaturesInScopes;
    private final boolean isInInstantRunMode;

    public DexMergerTransform(
            @NonNull DexingType dexingType,
            @Nullable BuildableArtifact mainDexListFile,
            @NonNull BuildableArtifact duplicateClassesCheck,
            @NonNull MessageReceiver messageReceiver,
            @NonNull DexMergerTool dexMerger,
            int minSdkVersion,
            boolean isDebuggable,
            boolean includeFeaturesInScopes,
            boolean isInInstantRunMode) {
        this.dexingType = dexingType;
        this.mainDexListFile = mainDexListFile;
        this.duplicateClassesCheck = duplicateClassesCheck;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexListFile != null),
                "Main dex list must only be set when in legacy multidex");
        this.messageReceiver = messageReceiver;
        this.includeFeaturesInScopes = includeFeaturesInScopes;
        this.isInInstantRunMode = isInInstantRunMode;
    }

    @NonNull
    @Override
    public String getName() {
        return "dexMerger";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE);
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        if (includeFeaturesInScopes) {
            return TransformManager.SCOPE_FULL_WITH_IR_AND_FEATURES;
        }
        return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        SecondaryFile dupCheck = SecondaryFile.nonIncremental(duplicateClassesCheck);
        if (mainDexListFile != null) {
            return ImmutableList.of(SecondaryFile.nonIncremental(mainDexListFile), dupCheck);
        } else {
            return ImmutableList.of(dupCheck);
        }
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMapWithExpectedSize(4);
        params.put("dexing-type", dexingType.name());
        params.put("dex-merger-tool", dexMerger.name());
        params.put("is-debuggable", isDebuggable);
        params.put("min-sdk-version", minSdkVersion);
        params.put("is-in-instant-run", isInInstantRunMode);

        return params;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(
                outputProvider, "Missing output object for transform " + getName());

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        messageReceiver);

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        ProcessOutput output = null;
        List<ForkJoinTask<Void>> mergeTasks;
        try (Closeable ignored = output = outputHandler.createOutput()) {
            if (dexingType == DexingType.NATIVE_MULTIDEX && isDebuggable) {
                mergeTasks =
                        handleNativeMultiDexDebug(
                                transformInvocation.getInputs(),
                                output,
                                outputProvider,
                                transformInvocation.isIncremental());
            } else {
                mergeTasks = mergeDex(transformInvocation.getInputs(), output, outputProvider);
            }

            // now wait for all merge tasks completion
            mergeTasks.forEach(ForkJoinTask::join);
        } catch (Exception e) {
            PluginCrashReporter.maybeReportException(e);
            // Print the error always, even without --stacktrace
            logger.error(null, Throwables.getStackTraceAsString(e));
            throw new TransformException(e);
        } finally {
            if (output != null) {
                try {
                    outputHandler.handleOutput(output);
                } catch (ProcessException e) {
                    // ignore this one
                }
            }
            forkJoinPool.shutdown();
            forkJoinPool.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    /**
     * For legacy and mono-dex we always merge all dex archives, non-incrementally. For release
     * native multidex we do the same, to get the smallest possible dex files.
     */
    @NonNull
    private List<ForkJoinTask<Void>> mergeDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider)
            throws IOException {
        Iterator<Path> dirInputs =
                TransformInputUtil.getDirectories(inputs).stream().map(File::toPath).iterator();
        Iterator<Path> jarInputs =
                inputs.stream()
                        .flatMap(transformInput -> transformInput.getJarInputs().stream())
                        .filter(jarInput -> jarInput.getStatus() != Status.REMOVED)
                        .map(jarInput -> jarInput.getFile().toPath())
                        .iterator();
        Iterator<Path> dexArchives = Iterators.concat(dirInputs, jarInputs);

        if (!dexArchives.hasNext()) {
            return ImmutableList.of();
        }

        File outputDir = getDexOutputLocation(outputProvider, "main", getScopes());
        // this deletes and creates the dir for the output
        FileUtils.cleanOutputDir(outputDir);

        Path mainDexClasses;
        if (mainDexListFile == null) {
            mainDexClasses = null;
        } else {
            mainDexClasses = BuildableArtifactUtil.singleFile(mainDexListFile).toPath();
        }

        return ImmutableList.of(submitForMerging(output, outputDir, dexArchives, mainDexClasses));
    }

    /**
     * All external library inputs will be merged together (this may result in multiple DEX files),
     * while other inputs will be merged individually (merging a single input might also result in
     * multiple DEX files).
     */
    @NonNull
    private List<ForkJoinTask<Void>> handleNativeMultiDexDebug(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental)
            throws IOException {

        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();

        List<DirectoryInput> directoryInputs = new ArrayList<>();
        List<JarInput> externalLibs = new ArrayList<>();
        List<JarInput> nonExternalJars = new ArrayList<>();
        collectInputsForNativeMultiDex(inputs, directoryInputs, externalLibs, nonExternalJars);

        boolean mergeAllInputs = shouldMergeInputsForNative(directoryInputs, nonExternalJars);
        subTasks.addAll(
                processDirectories(
                        output, outputProvider, isIncremental, directoryInputs, mergeAllInputs));

        if (!nonExternalJars.isEmpty()) {
            if (mergeAllInputs) {
                subTasks.addAll(
                        processNonExternalJarsTogether(
                                output, outputProvider, isIncremental, nonExternalJars));
            } else {
                subTasks.addAll(
                        processNonExternalJarsSeparately(
                                output, outputProvider, isIncremental, nonExternalJars));
            }
        }

        subTasks.addAll(processExternalJars(output, outputProvider, isIncremental, externalLibs));
        return subTasks.build();
    }

    /**
     * If all directory and non-external jar inputs should be merge individually, or we should merge
     * them together (all directory ones together, and all non-external jar ones together).
     *
     * <p>In order to improve the incremental build times, we will try to merge a single directory
     * input or non-external jar input in a single dex merger invocation i.e. a single input will
     * produce at least one dex file.
     *
     * <p>However, on Android L (API levels 21 and 22) there is a 100 dex files limit that we might
     * hit. Therefore, we might need to merge all directory inputs in a single dex merger
     * invocation. The same applies to non-external jar inputs.
     */
    private boolean shouldMergeInputsForNative(
            @NonNull Collection<DirectoryInput> directories,
            @NonNull Collection<JarInput> nonExternalJars) {
        if (minSdkVersion > 22) {
            return false;
        }

        long dirInputsCount = directories.stream().filter(d -> d.getFile().exists()).count();
        long nonExternalJarCount =
                nonExternalJars.stream().filter(d -> d.getStatus() != Status.REMOVED).count();
        return dirInputsCount + nonExternalJarCount
                > ANDROID_L_MAX_DEX_FILES - EXTERNAL_DEPS_DEX_FILES;
    }

    /**
     * Reads all inputs and adds the input to the corresponding collection. NB: this method mutates
     * the collections in its parameters.
     */
    private static void collectInputsForNativeMultiDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<DirectoryInput> directoryInputs,
            @NonNull Collection<JarInput> externalLibs,
            @NonNull Collection<JarInput> nonExternalJars) {
        for (TransformInput input : inputs) {
            directoryInputs.addAll(input.getDirectoryInputs());

            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getScopes().equals(Collections.singleton(Scope.EXTERNAL_LIBRARIES))) {
                    externalLibs.add(jarInput);
                } else {
                    nonExternalJars.add(jarInput);
                }
            }
        }
    }

    private List<ForkJoinTask<Void>> processNonExternalJarsSeparately(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<JarInput> inputs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();

        for (JarInput jarInput : inputs) {
            File dexOutput = getDexOutputLocation(outputProvider, jarInput);

            if (!isIncremental || jarInput.getStatus() != Status.NOTCHANGED) {
                FileUtils.cleanOutputDir(dexOutput);
            }

            if (!isIncremental
                    || jarInput.getStatus() == Status.ADDED
                    || jarInput.getStatus() == Status.CHANGED) {
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                Iterators.singletonIterator(jarInput.getFile().toPath()),
                                null));
            }
        }
        return subTasks.build();
    }

    @NonNull
    private List<ForkJoinTask<Void>> processNonExternalJarsTogether(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<JarInput> inputs)
            throws IOException {

        if (inputs.isEmpty()) {
            return ImmutableList.of();
        }

        Set<Status> statuses = EnumSet.noneOf(Status.class);
        Iterable<? super Scope> allScopes = new HashSet<>();
        for (JarInput jarInput : inputs) {
            statuses.add(jarInput.getStatus());
            allScopes = Iterables.concat(allScopes, jarInput.getScopes());
        }
        if (isIncremental && statuses.equals(Collections.singleton(Status.NOTCHANGED))) {
            return ImmutableList.of();
        }

        File mergedOutput =
                getDexOutputLocation(outputProvider, "nonExternalJars", Sets.newHashSet(allScopes));
        FileUtils.cleanOutputDir(mergedOutput);

        Iterator<Path> toMerge =
                inputs.stream()
                        .filter(i -> i.getStatus() != Status.REMOVED)
                        .map(i -> i.getFile().toPath())
                        .iterator();

        if (toMerge.hasNext()) {
            return ImmutableList.of(submitForMerging(output, mergedOutput, toMerge, null));
        } else {
            return ImmutableList.of();
        }
    }

    private List<ForkJoinTask<Void>> processDirectories(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<DirectoryInput> inputs,
            boolean mergeAllInputs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        List<DirectoryInput> deleted = new ArrayList<>();
        List<DirectoryInput> changed = new ArrayList<>();
        List<DirectoryInput> notChanged = new ArrayList<>();

        for (DirectoryInput directoryInput : inputs) {
            Path rootFolder = directoryInput.getFile().toPath();
            if (!Files.isDirectory(rootFolder)) {
                deleted.add(directoryInput);
            } else {
                boolean runAgain = !isIncremental;

                if (!runAgain) {
                    // check the incremental case
                    Collection<Status> statuses = directoryInput.getChangedFiles().values();
                    runAgain =
                            statuses.contains(Status.ADDED)
                                    || statuses.contains(Status.REMOVED)
                                    || statuses.contains(Status.CHANGED);
                }

                if (runAgain) {
                    changed.add(directoryInput);
                } else {
                    notChanged.add(directoryInput);
                }
            }
        }

        if (isIncremental && deleted.isEmpty() && changed.isEmpty()) {
            return subTasks.build();
        }

        if (mergeAllInputs) {
            File dexOutput =
                    getDexOutputLocation(
                            outputProvider,
                            isInInstantRunMode ? "slice_0" : "directories",
                            ImmutableSet.of(Scope.PROJECT));
            FileUtils.cleanOutputDir(dexOutput);

            Iterator<Path> toMerge =
                    Iterators.transform(
                            Iterators.concat(changed.iterator(), notChanged.iterator()),
                            i -> Objects.requireNonNull(i).getFile().toPath());
            if (toMerge.hasNext()) {
                subTasks.add(submitForMerging(output, dexOutput, toMerge, null));
            }
        } else {
            for (DirectoryInput directoryInput : deleted) {
                File dexOutput = getDexOutputLocation(outputProvider, directoryInput);
                FileUtils.cleanOutputDir(dexOutput);
            }
            for (DirectoryInput directoryInput : changed) {
                File dexOutput = getDexOutputLocation(outputProvider, directoryInput);
                FileUtils.cleanOutputDir(dexOutput);
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                Iterators.singletonIterator(directoryInput.getFile().toPath()),
                                null));
            }
        }
        return subTasks.build();
    }

    @NonNull
    private List<ForkJoinTask<Void>> processExternalJars(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            List<JarInput> externalLibs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        File externalLibsOutput =
                getDexOutputLocation(
                        outputProvider, "externalLibs", ImmutableSet.of(Scope.EXTERNAL_LIBRARIES));

        if (!isIncremental
                || externalLibs.stream().anyMatch(i -> i.getStatus() != Status.NOTCHANGED)) {
            // if non-incremental, or inputs have changed, merge again
            FileUtils.cleanOutputDir(externalLibsOutput);
            Iterator<Path> externalLibsToMerge =
                    externalLibs
                            .stream()
                            .filter(i -> i.getStatus() != Status.REMOVED)
                            .map(input -> input.getFile().toPath())
                            .iterator();
            if (externalLibsToMerge.hasNext()) {
                subTasks.add(
                        submitForMerging(output, externalLibsOutput, externalLibsToMerge, null));
            }
        }

        return subTasks.build();
    }

    /**
     * Add a merging task to the queue of tasks.
     *
     * @param output the process output that dx will output to.
     * @param dexOutputDir the directory to output dexes to
     * @param dexArchives the dex archive inputs
     * @param mainDexList the list of classes to keep in the main dex. Must be set <em>if and
     *     only</em> legacy multidex mode is used.
     * @return the {@link ForkJoinTask} instance for the submission.
     */
    @NonNull
    private ForkJoinTask<Void> submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Iterator<Path> dexArchives,
            @Nullable Path mainDexList) {
        DexMergerTransformCallable callable =
                new DexMergerTransformCallable(
                        messageReceiver,
                        dexingType,
                        output,
                        dexOutputDir,
                        dexArchives,
                        mainDexList,
                        forkJoinPool,
                        dexMerger,
                        minSdkVersion,
                        isDebuggable);
        return forkJoinPool.submit(callable);
    }

    @NonNull
    private File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider, @NonNull QualifiedContent content) {
        String name;
        if (content.getName().startsWith("slice_")) {
            name = content.getName();
        } else {
            name = content.getFile().toString();
        }
        return outputProvider.getContentLocation(
                name, getOutputTypes(), content.getScopes(), Format.DIRECTORY);
    }

    @NonNull
    private File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider,
            @NonNull String name,
            @NonNull Set<? super Scope> scopes) {
        return outputProvider.getContentLocation(name, getOutputTypes(), scopes, Format.DIRECTORY);
    }
}
