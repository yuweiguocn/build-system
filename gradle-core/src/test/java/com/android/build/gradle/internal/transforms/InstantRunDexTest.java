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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.fixtures.FakeFileCollection;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.builder.model.OptionalCompilationStep;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for the InstantRunDex transform. */
public class InstantRunDexTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule().silent();

    @Mock
    InstantRunVariantScope variantScope;

    @Mock
    GlobalScope globalScope;

    @Mock
    TransformOutputProvider transformOutputProvider;

    @Mock InstantRunBuildContext buildContext;

    @Mock
    Context context;

    @Mock
    Project project;

    @Mock
    InstantRunDex.JarClassesBuilder jarClassesBuilder;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File directoryInput;


    @Before
    public void setUp() throws IOException {

        final File reloadOutputFolder = temporaryFolder.newFolder();
        File oldDexFile = new File(reloadOutputFolder, "reload.dex");
        assertTrue(oldDexFile.createNewFile());

        final File restartOutputFolder = temporaryFolder.newFolder();
        File oldRestartFile = new File(restartOutputFolder, "restart.dex");
        assertTrue(oldRestartFile.createNewFile());

        when(variantScope.getInstantRunBuildContext()).thenReturn(buildContext);
        when(variantScope.getRestartDexOutputFolder()).thenReturn(restartOutputFolder);
        when(variantScope.getReloadDexOutputFolder()).thenReturn(reloadOutputFolder);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(globalScope.getProject()).thenReturn(project);
        when(project.getProperties()).then(
                invocation -> ImmutableMap.of("android.injected.build.api", "23"));
        when(globalScope.isActive(OptionalCompilationStep.RESTART_ONLY))
                .thenReturn(Boolean.FALSE);

        File tmp = new File(System.getProperty("java.io.tmpdir"));
        directoryInput = new File(tmp, "directory");
        File changedFile = new File(directoryInput, "path/to/some/file");
        Files.createParentDirs(changedFile);
        Files.asCharSink(changedFile, Charsets.UTF_8).write("abcde");
    }

    @After
    public void takeDown() throws IOException {
        FileUtils.deletePath(directoryInput);
    }

    @Test
    public void testVerifierFlaggedClass()
            throws TransformException, InterruptedException, IOException {

        when(buildContext.hasPassedVerification()).thenReturn(Boolean.FALSE);

        final List<File> convertedFiles = new ArrayList<>();
        InstantRunDex instantRunDex = getTestedDex(convertedFiles);

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isEmpty();

        convertedFiles.clear();
    }

    @Test
    public void testVerifierPassedClassOnLollipopOrAbove()
            throws TransformException, InterruptedException, IOException {
        when(buildContext.hasPassedVerification()).thenReturn(Boolean.TRUE);

        List<File> convertedFiles = new ArrayList<>();
        InstantRunDex instantRunDex = getTestedDex(convertedFiles);

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isNotEmpty();
        verify(buildContext).addChangedFile(
                eq(FileType.RELOAD_DEX),
                any(File.class));
    }

    @Test
    public void testVerifierPassedClassOnDalvik()
            throws TransformException, InterruptedException, IOException {
        when(buildContext.hasPassedVerification()).thenReturn(Boolean.TRUE);
        when(project.getProperties()).then(
                invocation -> ImmutableMap.of("android.injected.build.api", "15"));

        InstantRunDex instantRunDex =
                new InstantRunDex(
                        variantScope,
                        1,
                        false,
                        new FakeFileCollection(),
                        m -> {
                            /* ignore */
                        });

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addReferencedInputs(ImmutableList.of(getTransformInput(directoryInput)))
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isNotEmpty();
    }

    @Test
    public void testNoChanges() throws TransformException, InterruptedException, IOException {
        when(buildContext.hasPassedVerification()).thenReturn(Boolean.TRUE);
        when(project.getProperties()).then(
                invocation -> ImmutableMap.of("android.injected.build.api", "15"));

        InstantRunDex instantRunDex =
                new InstantRunDex(
                        variantScope,
                        1,
                        false,
                        new FakeFileCollection(),
                        m -> {
                            /* ignore */
                        });

        instantRunDex.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .build());

        assertThat(variantScope.getReloadDexOutputFolder().listFiles()).isEmpty();

    }

    private InstantRunDex getTestedDex(final List<File> convertedFiles) {
        return new InstantRunDex(
                variantScope,
                1,
                false,
                new FakeFileCollection(),
                m -> {
                    /* ignore */
                }) {

            @Override
            protected JarClassesBuilder getJarClassBuilder(File outputFile) {
                return jarClassesBuilder;
            }

            @Override
            protected void convertByteCode(File inputJar, List<Path> classpath, File outputFolder) {
                convertedFiles.add(inputJar);
            }
        };
    }

    private static TransformInput getTransformInput(final File dirInput) {
        return TransformTestHelper.directoryBuilder(dirInput)
                .setContentType(ExtendedContentType.CLASSES_ENHANCED)
                .setScope(QualifiedContent.Scope.PROJECT)
                .setName("test-input")
                .build();
    }
}
