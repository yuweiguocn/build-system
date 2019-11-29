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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExtractJarsTransformTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    Context context;

    @Mock
    Logger logger;

    @Before
    public void setLogger() {
        ExtractJarsTransform.LOGGER = logger;
    }

    @Test
    public void checkWarningForPotentialIssuesOnCaseSensitiveFileSystems()
            throws Exception {
        File jar = temporaryFolder.newFile("Jar with case issues.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(jar)))) {
            jarOutputStream.putNextEntry(new ZipEntry("com/example/a.class"));
            jarOutputStream.closeEntry();
            jarOutputStream.putNextEntry(new ZipEntry("com/example/A.class"));
            jarOutputStream.closeEntry();
            jarOutputStream.putNextEntry(new ZipEntry("com/example/B.class"));
            jarOutputStream.closeEntry();
        }
        checkWarningForCaseIssues(jar, true  /*expectingWarning*/);
    }

    @Test
    public void checkNoWarningWhenWillNotHaveIssuesOnCaseSensitiveFileSystems()
            throws Exception {
        File jar = temporaryFolder.newFile("Jar without case issues.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(jar)))) {
            jarOutputStream.putNextEntry(new ZipEntry("com/example/a.class"));
            jarOutputStream.closeEntry();
            jarOutputStream.putNextEntry(new ZipEntry("com/example/B.class"));
            jarOutputStream.closeEntry();
        }
        checkWarningForCaseIssues(jar, false /*expectingWarning*/);
    }

    @Test
    public void checkIncrementalDeleteJar() throws IOException, TransformException, InterruptedException {
        Path jar1 = temporaryFolder.newFile("jar1.jar").toPath();
        try (JarOutputStream jarOutputStream =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jar1)))) {
            jarOutputStream.putNextEntry(new ZipEntry("com/example/A.class"));
            jarOutputStream.closeEntry();
        }
        Path jar2 = temporaryFolder.newFile("jar2.jar").toPath();
        try (JarOutputStream jarOutputStream =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jar2)))) {
            jarOutputStream.putNextEntry(new ZipEntry("com/example/B.class"));
            jarOutputStream.closeEntry();
        }

        final ImmutableSet<QualifiedContent.ContentType> types =
                ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
        final ImmutableSet<QualifiedContent.Scope> scopes =
                ImmutableSet.of(QualifiedContent.Scope.SUB_PROJECTS);
        ExtractJarsTransform transform = new ExtractJarsTransform(types, scopes);
        Path outputDirectory = temporaryFolder.newFolder().toPath();

        // Initial Run
        final TransformOutputProviderImpl transformOutputProvider =
                new TransformOutputProviderImpl(outputDirectory, types, scopes);
        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(
                                ImmutableList.of(
                                        asJarInput(jar1, Status.NOTCHANGED),
                                        asJarInput(jar2, Status.NOTCHANGED)))
                        .addOutputProvider(transformOutputProvider)
                        .build());

        // we need to save the output state so that the next run picks it up
        transformOutputProvider.save();

        assertThat(getFileNames(outputDirectory))
                .containsExactly("A.class", "B.class", SubStream.FN_FOLDER_CONTENT);

        // Delete jar
        Files.delete(jar2);
        // Initial Run
        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(ImmutableList.of(asJarInput(jar2, Status.REMOVED)))
                        .addOutputProvider(
                                new TransformOutputProviderImpl(outputDirectory, types, scopes))
                        .setIncrementalMode(true)
                        .build());

        assertThat(getFileNames(outputDirectory))
                .containsExactly("A.class", SubStream.FN_FOLDER_CONTENT);
    }

    private static List<String> getFileNames(Path outputDirectory) throws IOException {
        return Files.walk(outputDirectory)
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toList());
    }

    private void checkWarningForCaseIssues(@NonNull File jar, boolean expectingWarning)
            throws IOException, TransformException, InterruptedException {

        final ImmutableSet<QualifiedContent.ContentType> types =
                ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
        final ImmutableSet<QualifiedContent.Scope> scopes =
                ImmutableSet.of(QualifiedContent.Scope.SUB_PROJECTS);
        ExtractJarsTransform transform = new ExtractJarsTransform(types, scopes);

        List<TransformInput> inputList = ImmutableList.of(asJarInput(jar, Status.NOTCHANGED));

        transform.transform(
                new TransformInvocationBuilder(context)
                        .addInputs(inputList)
                        .addOutputProvider(
                                new TransformOutputProviderImpl(
                                        temporaryFolder.newFolder().toPath(), types, scopes))
                        .build());
        if (expectingWarning) {
            verify(logger).error(anyString(), eq((Object)jar.getAbsolutePath()));
        }
        verifyNoMoreInteractions(logger);
    }

    private static TransformInput asJarInput(@NonNull Path jarFile, @NonNull Status status) {
        return asJarInput(jarFile.toFile(), status);
    }
    private static TransformInput asJarInput(@NonNull File jarFile, @NonNull Status status) {
        return TransformTestHelper.singleJarBuilder(jarFile)
                .setScopes(QualifiedContent.Scope.SUB_PROJECTS)
                .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setStatus(status)
                .build();
    }

    static class TransformOutputProviderImpl implements TransformOutputProvider {

        @NonNull private final File rootLocation;
        private final ImmutableSet<QualifiedContent.ContentType> types;
        private final ImmutableSet<QualifiedContent.Scope> scopes;
        private IntermediateFolderUtils folderUtils;

        TransformOutputProviderImpl(
                @NonNull Path rootLocation,
                ImmutableSet<QualifiedContent.ContentType> types,
                ImmutableSet<QualifiedContent.Scope> scopes) {
            this.rootLocation = rootLocation.toFile();
            this.types = types;
            this.scopes = scopes;
        }

        @Override
        public void deleteAll() throws IOException {
            FileUtils.cleanOutputDir(rootLocation);
        }

        public void save() throws IOException {
            folderUtils.save();
        }

        @NonNull
        @Override
        public File getContentLocation(
                @NonNull String name,
                @NonNull Set<QualifiedContent.ContentType> types,
                @NonNull Set<? super QualifiedContent.Scope> scopes,
                @NonNull Format format) {
            if (folderUtils == null) {
                folderUtils = new IntermediateFolderUtils(rootLocation, this.types, this.scopes);
            }
            return folderUtils.getContentLocation(name, types, scopes, format);
        }
    }

}
