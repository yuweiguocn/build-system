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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifier;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.pipeline.TaskTestUtils;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.OptionalCompilationStep;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Tests for the {@link InstantRunVerifierTransform}
 */
public class InstantRunVerifierTransformTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    final Map<File, File> recordedVerification = new HashMap<>();
    final Map<File, File> recordedCopies = new HashMap<>();
    private File backupDir;

    @Mock
    VariantScope variantScope;

    @Mock
    GlobalScope globalScope;

    @Mock
    Context context;

    @Mock
    TransformOutputProvider transformOutputProvider;

    @Mock InstantRunBuildContext buildContext;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUpMock() throws IOException {
        backupDir = temporaryFolder.newFolder();
        when(variantScope.getIncrementalVerifierDir()).thenReturn(backupDir);
        when(variantScope.getInstantRunBuildContext()).thenReturn(buildContext);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(buildContext.getVerifierResult()).thenReturn(
                InstantRunVerifierStatus.NO_CHANGES);
        when(globalScope.isActive(OptionalCompilationStep.RESTART_ONLY)).thenReturn(false);
    }

    @Test
    public void testNonIncrementalModel()
            throws TransformException, InterruptedException, IOException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File inputClass = new File(tmpDir, "com/foo/bar/InputFile.class");
        Files.createParentDirs(inputClass);
        assertTrue(inputClass.createNewFile());

        TransformInput transformInput =
                TransformTestHelper.directoryBuilder(tmpDir)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(ImmutableMap.of(inputClass, Status.ADDED))
                        .build();

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addOutputProvider(transformOutputProvider)
                        .addReferencedInputs(ImmutableList.of(transformInput))
                        .build());

        // clean up.
        FileUtils.deletePath(tmpDir);

        // input class should have been copied.
        assertThat(recordedVerification).isEmpty();
        assertThat(recordedCopies).hasSize(1);
        assertThat(recordedCopies).containsEntry(inputClass,
                new File(backupDir, "com/foo/bar/InputFile.class"));
    }

    @Test
    public void testIncrementalMode_changedAndAdded() throws TransformException, IOException, InterruptedException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File addedFile = new File(tmpDir, "com/foo/bar/NewInputFile.class");
        Files.createParentDirs(addedFile);
        assertTrue(addedFile.createNewFile());

        final File changedFile = new File(tmpDir, "com/foo/bar/ChangedFile.class");
        assertTrue(changedFile.createNewFile());
        final File lastIterationChangedFile =
                new File(backupDir, "com/foo/bar/ChangedFile.class");
        Files.createParentDirs(lastIterationChangedFile);
        assertTrue(lastIterationChangedFile.createNewFile());

        TransformInput transformInput =
                TransformTestHelper.directoryBuilder(tmpDir)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(
                                ImmutableMap.<File, Status>builder()
                                        .put(addedFile, Status.ADDED)
                                        .put(changedFile, Status.CHANGED)
                                        .build())
                        .build();

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addOutputProvider(transformOutputProvider)
                        .addReferencedInputs(ImmutableList.of(transformInput))
                        .setIncrementalMode(true)
                        .build());

        // clean up.
        FileUtils.deletePath(tmpDir);

        // changed class should have been verified
        assertThat(recordedVerification).isEmpty();

        // new classes should have been copied, and changed ones updated.
        assertThat(recordedCopies).hasSize(2);
        assertThat(recordedCopies).containsEntry(
                changedFile, lastIterationChangedFile);
        assertThat(recordedCopies).containsEntry(addedFile,
                new File(backupDir, "com/foo/bar/NewInputFile.class"));
    }

    @Test
    public void testIncrementalMode_changedAndDeleted() throws TransformException, IOException, InterruptedException {

        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File changedFile = new File(tmpDir, "com/foo/bar/ChangedFile.class");
        Files.createParentDirs(changedFile);
        assertTrue(changedFile.createNewFile());
        final File lastIterationChangedFile =
                new File(backupDir, "com/foo/bar/ChangedFile.class");
        Files.createParentDirs(lastIterationChangedFile);
        assertTrue(lastIterationChangedFile.createNewFile());

        final File deletedFile = new File(tmpDir, "com/foo/bar/DeletedFile.class");

        TransformInput transformInput =
                TransformTestHelper.directoryBuilder(tmpDir)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .putChangedFiles(
                                ImmutableMap.<File, Status>builder()
                                        .put(changedFile, Status.CHANGED)
                                        .put(deletedFile, Status.REMOVED)
                                        .build())
                        .build();

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addOutputProvider(transformOutputProvider)
                        .addReferencedInputs(ImmutableList.of(transformInput))
                        .setIncrementalMode(true)
                        .build());

        // clean up.
        FileUtils.deletePath(tmpDir);

        // changed class should have been verified
        assertThat(recordedVerification).hasSize(1);
        assertThat(recordedVerification).containsEntry(
                lastIterationChangedFile, changedFile);

        // new classes should have been copied, and changed ones updated.
        assertThat(recordedCopies).hasSize(1);
        assertThat(recordedCopies).containsEntry(
                changedFile, lastIterationChangedFile);
    }

    @Test
    public void testSeveralAddedFilesInIncrementalMode()
            throws IOException, TransformException, InterruptedException {
        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File[] files = new File[5];
        for (int i = 0; i < 5; i++) {
            files[i] = new File(tmpDir, "com/foo/bar/NewInputFile-" + i + ".class");
            Files.createParentDirs(files[i]);
            assertTrue(files[i].createNewFile());
        }

        ImmutableMap.Builder<File, Status> changesBuilder = ImmutableMap.builder();
        for (int i = 0; i < 5; i++) {
            changesBuilder.put(files[i], Status.ADDED);
        }
        TransformInput transformInput =
                TransformTestHelper.directoryBuilder(tmpDir)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .putChangedFiles(changesBuilder.build())
                        .build();

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addOutputProvider(transformOutputProvider)
                        .addReferencedInputs(ImmutableList.of(transformInput))
                        .setIncrementalMode(true)
                        .build());

        // clean up.
        FileUtils.deletePath(tmpDir);

        // input class should have been copied.
        assertThat(recordedCopies).hasSize(5);
        for (int i=0; i<5; i++) {
            assertThat(recordedCopies).containsEntry(files[i],
                    new File(backupDir, "com/foo/bar/" + files[i].getName()));
        }
        assertThat(recordedVerification).isEmpty();
    }

    @Test
    public void testSeveralChangedFilesInIncrementalMode()
            throws IOException, TransformException, InterruptedException {
        InstantRunVerifierTransform transform = getTransform();
        final File tmpDir = temporaryFolder.newFolder();

        final File[] files = new File[5];
        final File[] lastIterationFiles = new File[5];
        for (int i = 0; i < 5; i++) {
            files[i] = new File(tmpDir, "com/foo/bar/NewInputFile-" + i + ".class");
            Files.createParentDirs(files[i]);
            assertTrue(files[i].createNewFile());
            lastIterationFiles[i] = new File(backupDir, "com/foo/bar/NewInputFile-" + i + ".class");
            Files.createParentDirs(lastIterationFiles[i]);
            assertTrue(lastIterationFiles[i].createNewFile());
        }

        ImmutableMap.Builder<File, Status> changesBuilder = ImmutableMap.builder();
        for (int i = 0; i < 5; i++) {
            changesBuilder.put(files[i], Status.CHANGED);
        }
        TransformInput transformInput =
                TransformTestHelper.directoryBuilder(tmpDir)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .putChangedFiles(changesBuilder.build())
                        .build();

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addOutputProvider(transformOutputProvider)
                        .addReferencedInputs(ImmutableList.of(transformInput))
                        .setIncrementalMode(true)
                        .build());

        // clean up.
        FileUtils.deletePath(tmpDir);

        // input class should have been verified.
        assertThat(recordedVerification).hasSize(5);
        for (int i=0; i<5; i++) {
            assertThat(recordedVerification).containsEntry(lastIterationFiles[i], files[i]);
        }
        // and updated...
        assertThat(recordedCopies).hasSize(5);
        for (int i=0; i<5; i++) {
            assertThat(recordedCopies).containsEntry(files[i], lastIterationFiles[i]);
        }
    }

    @Test
    public void testStatusAlreadySet()
            throws TransformException, InterruptedException, IOException {
        when(buildContext.getVerifierResult()).thenReturn(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);

        final File inputDir = temporaryFolder.newFolder();
        final File inputFile = new File(inputDir, "com/foo/bar/InputFile.class");

        TransformInput transformInput =
                TransformTestHelper.directoryBuilder(inputDir)
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .putChangedFiles(ImmutableMap.of(inputFile, Status.ADDED))
                        .build();

        getTransform()
                .transform(
                        new TransformInvocationBuilder(context)
                                .addOutputProvider(transformOutputProvider)
                                .addReferencedInputs(ImmutableList.of(transformInput))
                                .setIncrementalMode(true)
                                .build());

        // check the verifier status is not reset.
        assertThat(buildContext.getVerifierResult()).isEqualTo(
                InstantRunVerifierStatus.DEPENDENCY_CHANGED);
    }

    private InstantRunVerifierTransform getTransform() {

        return new InstantRunVerifierTransform(variantScope, new TaskTestUtils.FakeRecorder()) {

            @NonNull
            @Override
            protected InstantRunVerifierStatus runVerifier(
                    String name,
                    @NonNull final InstantRunVerifier.ClassBytesProvider originalClass,
                    @NonNull final InstantRunVerifier.ClassBytesProvider updatedClass)
                    throws IOException {

                recordedVerification.put(
                        ((InstantRunVerifier.ClassBytesFileProvider) originalClass).getFile(),
                        ((InstantRunVerifier.ClassBytesFileProvider) updatedClass).getFile());
                return InstantRunVerifierStatus.COMPATIBLE;
            }

            @Override
            protected void copyFile(File inputFile, File outputFile) {
                recordedCopies.put(inputFile, outputFile);
            }
        };
    }
}
