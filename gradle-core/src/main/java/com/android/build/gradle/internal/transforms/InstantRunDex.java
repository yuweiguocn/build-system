/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.InternalScope;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.ClassFileInputs;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.r8.ClassFileProviderFactory;
import com.android.builder.model.OptionalCompilationStep;
import com.android.ide.common.blame.MessageReceiver;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;

/**
 * Transform that takes the hot (or warm) swap classes and dexes them.
 *
 * <p>The instant run transform outputs all of the hot swap files in the
 * {@link ExtendedContentType#CLASSES_ENHANCED} stream. This transform runs incrementally to re-dex
 * and redeliver only the classes changed since the last build, even in a series of hot swaps.
 *
 * <p>Note that a non-incremental run is still correct, it will just dex all of the hot swap changes
 * since the last cold swap or full build.
 */
public class InstantRunDex extends Transform {

    @NonNull
    private final InstantRunVariantScope variantScope;
    private final int minSdkForDx;
    private final boolean enableDesugaring;
    @NonNull private final FileCollection bootClasspath;
    @NonNull private MessageReceiver messageReceiver;

    public InstantRunDex(
            @NonNull InstantRunVariantScope transformVariantScope,
            int minSdkForDx,
            boolean enableDesugaring,
            @NonNull FileCollection bootClasspath,
            @NonNull MessageReceiver messageReceiver) {
        this.variantScope = transformVariantScope;
        this.minSdkForDx = minSdkForDx;
        this.enableDesugaring = enableDesugaring;
        this.bootClasspath = bootClasspath;
        this.messageReceiver = messageReceiver;
    }


    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        File outputFolder = InstantRunBuildType.RELOAD.getOutputFolder(variantScope);

        boolean changesAreCompatible =
                variantScope.getInstantRunBuildContext().hasPassedVerification();
        boolean restartDexRequested =
                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY);

        if (!changesAreCompatible || restartDexRequested) {
            FileUtils.cleanOutputDir(outputFolder);
            return;
        }

        // create a tmp jar file.
        File classesJar = new File(outputFolder, "classes.jar");
        if (classesJar.exists()) {
            FileUtils.delete(classesJar);
        }
        Files.createParentDirs(classesJar);
        final JarClassesBuilder jarClassesBuilder = getJarClassBuilder(classesJar);

        try {
            for (TransformInput input : invocation.getReferencedInputs()) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    Set<QualifiedContent.ContentType> types = directoryInput.getContentTypes();
                    // we want to process only CLASSES_ENHANCED
                    if (types.size() != 1
                            || !types.contains(ExtendedContentType.CLASSES_ENHANCED)) {
                        continue;
                    }
                    final File folder = directoryInput.getFile();
                    if (invocation.isIncremental()) {
                        for (Map.Entry<File, Status> entry :
                                directoryInput.getChangedFiles().entrySet()) {
                            if (entry.getValue() != Status.REMOVED) {
                                File file = entry.getKey();
                                if (file.isFile()) {
                                    jarClassesBuilder.add(folder, file);
                                }
                            }
                        }
                    } else {
                        Iterable<File> files = FileUtils.getAllFiles(folder);
                        for (File inputFile : files) {
                            jarClassesBuilder.add(folder, inputFile);
                        }
                    }
                }
            }
        } finally {
            jarClassesBuilder.close();
        }

        // if no files were added, clean up and return.
        if (jarClassesBuilder.isEmpty()) {
            FileUtils.cleanOutputDir(outputFolder);
            return;
        }

        try {
            variantScope
                    .getInstantRunBuildContext()
                    .startRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
            List<Path> classpath = getClasspath(invocation);
            convertByteCode(classesJar, classpath, outputFolder);
            variantScope
                    .getInstantRunBuildContext()
                    .addChangedFile(FileType.RELOAD_DEX, new File(outputFolder, "classes.dex"));
        } finally {
            variantScope
                    .getInstantRunBuildContext()
                    .stopRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
        }
    }

    @VisibleForTesting
    static class JarClassesBuilder implements Closeable {
        final File outputFile;
        private JarOutputStream jarOutputStream;
        boolean empty = true;

        private JarClassesBuilder(@NonNull File outputFile) {
            this.outputFile = outputFile;
        }

        void add(File inputDir, File file) throws IOException {
            if (jarOutputStream == null) {
                jarOutputStream = new JarOutputStream(
                        new BufferedOutputStream(new FileOutputStream(outputFile)));
            }
            empty = false;
            copyFileInJar(inputDir, file, jarOutputStream);
        }

        @Override
        public void close() throws IOException {
            if (jarOutputStream != null) {
                jarOutputStream.close();
            }
        }

        boolean isEmpty() {
            return empty;
        }
    }

    @VisibleForTesting
    protected void convertByteCode(File inputJar, List<Path> classpath, File outputFolder)
            throws IOException {
        try (ClassFileProviderFactory bootClasspathProvider =
                        new ClassFileProviderFactory(getBootClasspath());
                ClassFileProviderFactory libraryClasspathProvider =
                        new ClassFileProviderFactory(classpath);
                ClassFileInput classInput = ClassFileInputs.fromPath(inputJar.toPath())) {
            DexArchiveBuilder d8DexBuilder =
                    DexArchiveBuilder.createD8DexBuilder(
                            minSdkForDx,
                            true,
                            bootClasspathProvider,
                            libraryClasspathProvider,
                            enableDesugaring,
                            messageReceiver);
            d8DexBuilder.convert(classInput.entries(p -> true), outputFolder.toPath(), false);
        }
    }

    @VisibleForTesting
    protected JarClassesBuilder getJarClassBuilder(File outputFile) {
        return new JarClassesBuilder(outputFile);
    }

    private static void copyFileInJar(
            File inputDir, File inputFile, JarOutputStream jarOutputStream) throws IOException {

        String entryName = inputFile.getPath().substring(inputDir.getPath().length() + 1);
        JarEntry jarEntry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(jarEntry);
        Files.copy(inputFile, jarOutputStream);
        jarOutputStream.closeEntry();
    }

    @NonNull
    @Override
    public String getName() {
        return "instantReloadDex";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(
                ExtendedContentType.CLASSES_ENHANCED, QualifiedContent.DefaultContentType.CLASSES);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
        if (enableDesugaring) {
            return ImmutableSet.of(
                    QualifiedContent.Scope.PROJECT,
                    QualifiedContent.Scope.SUB_PROJECTS,
                    QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                    QualifiedContent.Scope.PROVIDED_ONLY,
                    InternalScope.MAIN_SPLIT);
        } else {
            return ImmutableSet.of(QualifiedContent.Scope.PROJECT);
        }
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> params = ImmutableMap.builder();
        params.put(
                "changesAreCompatible",
                variantScope.getInstantRunBuildContext().hasPassedVerification());
        params.put(
                "restartDexRequested",
                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY));
        params.put("minSdkForDx", minSdkForDx);
        return params.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(InstantRunBuildType.RELOAD.getOutputFolder(variantScope));
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @NonNull
    private List<Path> getClasspath(@NonNull TransformInvocation transformInvocation) {
        if (!enableDesugaring) {
            return Collections.emptyList();
        }

        Collection<File> allFiles =
                TransformInputUtil.getAllFiles(transformInvocation.getReferencedInputs());
        return allFiles.stream().map(File::toPath).collect(Collectors.toList());
    }

    @NonNull
    private List<Path> getBootClasspath() {
        if (!enableDesugaring) {
            return Collections.emptyList();
        }

        return bootClasspath.getFiles().stream().map(File::toPath).collect(Collectors.toList());
    }
}
