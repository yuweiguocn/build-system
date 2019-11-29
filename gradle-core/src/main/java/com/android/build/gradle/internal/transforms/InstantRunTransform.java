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

import static com.android.build.api.transform.QualifiedContent.DefaultContentType;
import static com.android.build.api.transform.QualifiedContent.Scope;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.IncrementalChangeVisitor;
import com.android.build.gradle.internal.incremental.IncrementalSupportVisitor;
import com.android.build.gradle.internal.incremental.IncrementalVisitor;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.options.DeploymentDevice;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.logging.Logging;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Implementation of the {@link Transform} to run the byte code enhancement logic on compiled
 * classes in order to support runtime hot swapping.
 */
public class InstantRunTransform extends Transform {

    protected static final ILogger LOGGER =
            new LoggerWrapper(Logging.getLogger(InstantRunTransform.class));
    private final ImmutableList.Builder<String> generatedClasses3Names = ImmutableList.builder();
    private final InstantRunVariantScope transformScope;
    private final AndroidVersion targetPlatformApi;
    private final WaitableExecutor executor;

    public InstantRunTransform(WaitableExecutor executor, InstantRunVariantScope transformScope) {
        this.transformScope = transformScope;
        this.executor = executor;
        this.targetPlatformApi =
                DeploymentDevice.getDeploymentDeviceAndroidVersion(
                        transformScope.getGlobalScope().getProjectOptions());
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRun";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return ImmutableSet.of(
                DefaultContentType.CLASSES,
                ExtendedContentType.CLASSES_ENHANCED);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS);
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(Scope.EXTERNAL_LIBRARIES,
                Scope.PROVIDED_ONLY);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        // Force the instant run transform to re-run when the dex patching policy changes,
        // as the slicer will re-run.
        return transformScope.getInstantRunBuildContext().isInInstantRunMode()
                ? ImmutableMap.of(
                        "dex patching policy",
                        transformScope
                                .getInstantRunBuildContext()
                                .getPatchingPolicy()
                                .toString())
                : ImmutableMap.of();

    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return Lists.transform(
                transformScope.getInstantRunBootClasspath(), SecondaryFile::nonIncremental);
    }

    private interface WorkItem {

         Void doWork() throws IOException;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        List<JarInput> jarInputs =
                invocation
                        .getInputs()
                        .stream()
                        .flatMap(input -> input.getJarInputs().stream())
                        .collect(Collectors.toList());
        if (!jarInputs.isEmpty()) {
            jarInputs.forEach(
                    jar ->
                            Preconditions.checkState(
                                    !jar.getFile().exists(),
                                    "%s must not exist",
                                    jar.getFile().toString()));
            // just log as warning until https://issuetracker.google.com/72032032 is fixed
            LOGGER.warning("Unexpected inputs: %s", Joiner.on(", ").join(jarInputs));
        }
        InstantRunBuildContext buildContext = transformScope.getInstantRunBuildContext();
        buildContext.startRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_TRANSFORM);
        try {
            doTransform(invocation);
        } finally {
            buildContext.stopRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_TRANSFORM);
        }

    }

    public void doTransform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException {
        InstantRunBuildContext buildContext = transformScope.getInstantRunBuildContext();

        // if we do not run in incremental mode, we should automatically switch to COLD swap.
        if (!invocation.isIncremental()) {
            buildContext.setVerifierStatus(
                    InstantRunVerifierStatus.BUILD_NOT_INCREMENTAL);
        }

        // If this is not a HOT_WARM build, clean up the enhanced classes and don't generate new
        // ones during this build.
        boolean inHotSwapMode =
                buildContext.getBuildMode() == InstantRunBuildMode.HOT_WARM;

        TransformOutputProvider outputProvider = invocation.getOutputProvider();
        if (outputProvider == null) {
            throw new IllegalStateException("InstantRunTransform called with null output");
        }

        File classesTwoOutput =
                outputProvider.getContentLocation(
                        "classes", TransformManager.CONTENT_CLASS, getScopes(), Format.DIRECTORY);

        File classesThreeOutput =
                outputProvider.getContentLocation(
                        "enhanced_classes",
                        ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED),
                        getScopes(),
                        Format.DIRECTORY);

        List<WorkItem> workItems = new ArrayList<>();
        for (TransformInput input : invocation.getInputs()) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                File inputDir = directoryInput.getFile();
                if (invocation.isIncremental()) {
                    for (Map.Entry<File, Status> fileEntry : directoryInput
                            .getChangedFiles()
                            .entrySet()) {

                        File inputFile = fileEntry.getKey();
                        if (!inputFile.getName().endsWith(SdkConstants.DOT_CLASS)) {
                            continue;
                        }
                        switch (fileEntry.getValue()) {
                            case REMOVED:
                                // remove the classes.2 and classes.3 files.
                                deleteOutputFile(
                                        IncrementalSupportVisitor.VISITOR_BUILDER,
                                        inputDir, inputFile, classesTwoOutput);
                                deleteOutputFile(IncrementalChangeVisitor.VISITOR_BUILDER,
                                        inputDir, inputFile, classesThreeOutput);
                                break;
                            case CHANGED:
                                if (inHotSwapMode) {
                                    workItems.add(() -> transformToClasses3Format(
                                            inputDir,
                                            inputFile,
                                            classesThreeOutput));
                                }
                                // fall through the ADDED case to generate classes.2
                            case ADDED:
                                workItems.add(() -> transformToClasses2Format(
                                        inputDir,
                                        inputFile,
                                        classesTwoOutput,
                                        fileEntry.getValue()));
                                break;
                            case NOTCHANGED:
                                break;
                            default:
                                throw new IllegalStateException("Unhandled file status "
                                        + fileEntry.getValue());
                        }
                    }
                } else {
                    // non incremental mode, we need to traverse the TransformInput#getFiles()
                    // folder
                    FileUtils.cleanOutputDir(classesTwoOutput);
                    for (File file : Files.fileTraverser().breadthFirst(inputDir)) {
                        if (file.isDirectory()) {
                            continue;
                        }
                        workItems.add(() -> transformToClasses2Format(
                                inputDir,
                                file,
                                classesTwoOutput,
                                Status.ADDED));
                    }
                }
            }
        }

        // first get all referenced input to construct a class loader capable of loading those
        // classes. This is useful for ASM as it needs to load classes
        List<URL> referencedInputUrls = getAllClassesLocations(
                invocation.getInputs(), invocation.getReferencedInputs());

        // This class loader could be optimized a bit, first we could create a parent class loader
        // with the android.jar only that could be stored in the GlobalScope for reuse. This
        // class loader could also be store in the VariantScope for potential reuse if some
        // other transform need to load project's classes.
        try (URLClassLoader urlClassLoader = new NonDelegatingUrlClassloader(referencedInputUrls)) {
            workItems.forEach(
                    workItem ->
                            executor.execute(
                                    () -> {
                                        ClassLoader currentThreadClassLoader =
                                                Thread.currentThread().getContextClassLoader();
                                        Thread.currentThread()
                                                .setContextClassLoader(urlClassLoader);
                                        try {
                                            return workItem.doWork();
                                        } finally {
                                            Thread.currentThread()
                                                    .setContextClassLoader(
                                                            currentThreadClassLoader);
                                        }
                                    }));

            try {
                // wait for all work items completion.
                executor.waitForTasksWithQuickFail(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransformException(e);
            } catch (Exception e) {
                throw new TransformException(e);
            }
        }

        // If our classes.2 transformations indicated that a cold swap was necessary,
        // clean up the classes.3 output folder as some new files may have been generated.
        if (buildContext.getBuildMode() != InstantRunBuildMode.HOT_WARM) {
            FileUtils.cleanOutputDir(classesThreeOutput);
        }

        wrapUpOutputs(classesTwoOutput, classesThreeOutput);
    }

    protected void wrapUpOutputs(File classes2Folder, File classes3Folder)
            throws IOException {

        // the transform can set the verifier status to failure in some corner cases, in that
        // case, make sure we delete our classes.3
        if (!transformScope.getInstantRunBuildContext().hasPassedVerification()) {
            FileUtils.cleanOutputDir(classes3Folder);
            return;
        }
        // otherwise, generate the patch file and add it to the list of files to process next.
        ImmutableList<String> generatedClassNames = generatedClasses3Names.build();
        if (!generatedClassNames.isEmpty()) {
            writePatchFileContents(
                    generatedClassNames,
                    classes3Folder,
                    transformScope.getInstantRunBuildContext().getBuildId());
        }
    }


    /**
     * Calculate a list of {@link URL} that represent all the directories containing classes
     * either directly belonging to this project or referencing it.
     *
     * @param inputs the project's inputs
     * @param referencedInputs the project's referenced inputs
     * @return a {@link List} or {@link URL} for all the locations.
     * @throws MalformedURLException if once the locatio
     */
    @NonNull
    private List<URL> getAllClassesLocations(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs) throws MalformedURLException {

        List<URL> referencedInputUrls = new ArrayList<>();

        // add the bootstrap classpath for jars like android.jar
        for (File file : transformScope.getInstantRunBootClasspath()) {
            referencedInputUrls.add(file.toURI().toURL());
        }

        // now add the project dependencies.
        for (TransformInput referencedInput : referencedInputs) {
            addAllClassLocations(referencedInput, referencedInputUrls);
        }

        // and finally add input folders.
        for (TransformInput input : inputs) {
            addAllClassLocations(input, referencedInputUrls);
        }
        return referencedInputUrls;
    }

    private static void addAllClassLocations(TransformInput transformInput, List<URL> into)
            throws MalformedURLException {

        for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
            into.add(directoryInput.getFile().toURI().toURL());
        }
        for (JarInput jarInput : transformInput.getJarInputs()) {
            into.add(jarInput.getFile().toURI().toURL());
        }
    }

    /**
     * Transform a single file into a format supporting class hot swap.
     *
     * @param inputDir the input directory containing the input file.
     * @param inputFile the input file within the input directory to transform.
     * @param outputDir the output directory where to place the transformed file.
     * @param change the nature of the change that triggered the transformation.
     * @throws IOException if the transformation failed.
     */
    @Nullable
    protected Void transformToClasses2Format(
            @NonNull final File inputDir,
            @NonNull final File inputFile,
            @NonNull final File outputDir,
            @NonNull final Status change)
            throws IOException {
        if (inputFile.getPath().endsWith(SdkConstants.DOT_CLASS)) {
            IncrementalVisitor.instrumentClass(
                    targetPlatformApi.getFeatureLevel(),
                    inputDir,
                    inputFile,
                    outputDir,
                    IncrementalSupportVisitor.VISITOR_BUILDER,
                    LOGGER);
        }
        return null;
    }

    private static void deleteOutputFile(
            @NonNull IncrementalVisitor.VisitorBuilder visitorBuilder,
            @NonNull File inputDir, @NonNull File inputFile, @NonNull File outputDir) {
        String inputPath = FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir);
        String outputPath =
                visitorBuilder.getMangledRelativeClassFilePath(inputPath);
        File outputFile = new File(outputDir, outputPath);
        if (outputFile.exists()) {
            try {
                FileUtils.delete(outputFile);
            } catch (IOException e) {
                // it's not a big deal if the file cannot be deleted, hopefully
                // no code is still referencing it, yet we should notify.
                LOGGER.warning("Cannot delete %1$s file.\nCause: %2$s",
                        outputFile, Throwables.getStackTraceAsString(e));
            }
        }
    }

    /**
     * Transform a single file into a {@link ExtendedContentType#CLASSES_ENHANCED} format
     *
     * @param inputDir the input directory containing the input file.
     * @param inputFile the input file within the input directory to transform.
     * @param outputDir the output directory where to place the transformed file.
     * @throws IOException if the transformation failed.
     */
    @Nullable
    protected Void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
            throws IOException {

        File outputFile =
                IncrementalVisitor.instrumentClass(
                        targetPlatformApi.getFeatureLevel(),
                        inputDir,
                        inputFile,
                        outputDir,
                        IncrementalChangeVisitor.VISITOR_BUILDER,
                        LOGGER);

        // if the visitor returned null, that means the class cannot be hot swapped or more likely
        // that it was disabled for InstantRun, we don't add it to our collection of generated
        // classes and it will not be part of the Patch class that apply changes.
        if (outputFile == null) {
            transformScope
                    .getInstantRunBuildContext()
                    .setVerifierStatus(InstantRunVerifierStatus.INSTANT_RUN_DISABLED);
            LOGGER.info("Class %s cannot be hot swapped.", inputFile);
            return null;
        }
        generatedClasses3Names.add(
                inputFile.getAbsolutePath().substring(
                    inputDir.getAbsolutePath().length() + 1,
                    inputFile.getAbsolutePath().length() - ".class".length())
                        .replace(File.separatorChar, '.'));
        return null;
    }

    /**
     * Use asm to generate a concrete subclass of the AppPathLoaderImpl class.
     * It only implements one method :
     *      String[] getPatchedClasses();
     *
     * The method is supposed to return the list of classes that were patched in this iteration.
     * This will be used by the InstantRun runtime to load all patched classes and register them
     * as overrides on the original classes.2 class files.
     *
     * @param patchFileContents list of patched class names.
     * @param outputDir output directory where to generate the .class file in.
     */
    private static void writePatchFileContents(
            @NonNull ImmutableList<String> patchFileContents, @NonNull File outputDir, long buildId) {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                IncrementalVisitor.APP_PATCHES_LOADER_IMPL, null,
                IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL, null);

        // Add the build ID to force the patch file to be repackaged.
        cw.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL,
                "BUILD_ID", "J", null, buildId);

        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL,
                    "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                    "getPatchedClasses", "()[Ljava/lang/String;", null, null);
            mv.visitCode();
            mv.visitIntInsn(Opcodes.BIPUSH, patchFileContents.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
            for (int index=0; index < patchFileContents.size(); index++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.BIPUSH, index);
                mv.visitLdcInsn(patchFileContents.get(index));
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] classBytes = cw.toByteArray();
        File outputFile = new File(outputDir, IncrementalVisitor.APP_PATCHES_LOADER_IMPL + ".class");
        try {
            Files.createParentDirs(outputFile);
            Files.write(classBytes, outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class NonDelegatingUrlClassloader extends URLClassLoader {

        public NonDelegatingUrlClassloader(@NonNull List<URL> urls) {
            super(urls.toArray(new URL[0]), null);
        }

        @Override
        public URL getResource(String name) {
            // Never delegate to bootstrap classes.
            return findResource(name);
        }
    }
}
