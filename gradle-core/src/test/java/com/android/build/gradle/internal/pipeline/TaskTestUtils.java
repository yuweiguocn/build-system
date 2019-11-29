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

package com.android.build.gradle.internal.pipeline;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.errors.SyncIssueHandler;
import com.android.build.gradle.internal.ide.SyncIssueImpl;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskFactory;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * Base class for Junit-4 based tests that need to manually instantiate tasks to test them.
 *
 * Right now this is limited to using the TransformManager but that could be refactored
 * to allow for other tasks using the AndroidTaskRegistry directly.
 */
public class TaskTestUtils {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String FOLDER_TEST_PROJECTS = "test-projects";

    protected static final String TASK_NAME = "task name";

    protected TaskFactory taskFactory;
    protected VariantScope scope;
    protected TransformManager transformManager;
    protected FakeConfigurableErrorReporter errorReporter;

    protected Supplier<RuntimeException> mTransformTaskFailed;
    protected Project project;

    static class FakeConfigurableErrorReporter implements SyncIssueHandler {

        private SyncIssue syncIssue = null;

        protected FakeConfigurableErrorReporter() {}

        public SyncIssue getSyncIssue() {
            return syncIssue;
        }

        @NonNull
        @Override
        public SyncIssue reportIssue(
                @NonNull Type type,
                @NonNull Severity severity,
                @NonNull EvalIssueException exception) {
            // always create a sync issue, no matter what the mode is. This can be used to validate
            // what error is thrown anyway.
            syncIssue =
                    new SyncIssueImpl(type, severity, exception.getData(), exception.getMessage());
            return syncIssue;
        }

        @Override
        public boolean hasSyncIssue(@NonNull Type type) {
            return syncIssue != null && syncIssue.getType() == type.getType();
        }

        @NonNull
        @Override
        public ImmutableList<SyncIssue> getSyncIssues() {
            if (syncIssue != null) {
                return ImmutableList.of(syncIssue);
            }

            return ImmutableList.of();
        }

        // Kotlin default impl doesn't work in java...
        @NonNull
        @Override
        public SyncIssue reportIssue(
                @NonNull Type type, @NonNull Severity severity, @NonNull String msg) {
            return reportIssue(type, severity, msg, null);
        }

        @NonNull
        @Override
        public SyncIssue reportIssue(
                @NonNull Type type,
                @NonNull Severity severity,
                @NonNull String msg,
                @Nullable String data) {
            return reportIssue(type, severity, new EvalIssueException(msg, data));
        }

        @NonNull
        @Override
        public SyncIssue reportError(@NonNull Type type, @NonNull EvalIssueException exception) {
            return reportIssue(type, Severity.ERROR, exception);
        }

        @NonNull
        @Override
        public SyncIssue reportWarning(
                @NonNull Type type, @NonNull String msg, @Nullable String data) {
            return reportIssue(type, Severity.WARNING, msg, data);
        }

        @NonNull
        @Override
        public SyncIssue reportWarning(@NonNull Type type, @NonNull String msg) {
            return reportIssue(type, Severity.WARNING, msg, null);
        }
    }

    public static final class FakeRecorder implements Recorder {
        @Nullable
        @Override
        public <T> T record(
                @NonNull GradleBuildProfileSpan.ExecutionType executionType,
                @NonNull String projectPath,
                @Nullable String variant,
                @NonNull Block<T> block) {
            try {
                return block.call();
            } catch (Exception e) {
                block.handleException(e);
            }
            return null;
        }

        @Override
        public void record(
                @NonNull GradleBuildProfileSpan.ExecutionType executionType,
                @NonNull String projectPath,
                @Nullable String variant,
                @NonNull VoidBlock block) {
            try {
                block.call();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Nullable
        @Override
        public <T> T record(
                @NonNull GradleBuildProfileSpan.ExecutionType executionType,
                @Nullable GradleTransformExecution transform,
                @NonNull String projectPath,
                @Nullable String variant,
                @NonNull Block<T> block) {
            return record(executionType, projectPath, variant, block);
        }
    }

    @Before
    public void setUp() throws IOException {
        File projectDirectory = temporaryFolder.newFolder();
        FileUtils.mkdirs(projectDirectory);
        project = ProjectBuilder.builder().withProjectDir(projectDirectory).build();
        scope = getScope();
        errorReporter = new FakeConfigurableErrorReporter();
        transformManager = new TransformManager(project, errorReporter, new FakeRecorder());
        taskFactory = new TaskFactoryImpl(project.getTasks());
        mTransformTaskFailed = () -> new RuntimeException(
                String.format("Transform task creation failed.  Sync issue:\n %s",
                        errorReporter.getSyncIssue().toString()));
    }

    protected StreamTester streamTester() {
        return new StreamTester(null);
    }

    protected StreamTester streamTester(@NonNull final Collection<TransformStream> streams) {
        return new StreamTester(new FilterableStreamCollection() {
            @Override
            Project getProject() {
                return project;
            }

            @NonNull
            @Override
            Collection<TransformStream> getStreams() {
                return streams;
            }
        });
    }

    /**
     * Simple class to test that a stream is present in the list of available streams in the
     * transform manager.
     *
     * Right now this expects to find ony a single stream based on the content types and/or scopes
     * provided.
     *
     * Then it optionally test for additional values, if provided.
     */
    protected class StreamTester {
        @NonNull
        private final FilterableStreamCollection streamCollection;
        @NonNull
        private final Set<QualifiedContent.ContentType> contentTypes = Sets.newHashSet();
        private final Set<QualifiedContent.Scope> scopes = Sets.newHashSet();
        private List<Object> dependencies = Lists.newArrayList();

        private List<File> jars = Lists.newArrayList();
        private List<File> folders = Lists.newArrayList();
        private List<FileCollection> fileCollections = Lists.newArrayList();
        private File rootLocation = null;

        private StreamTester(@Nullable FilterableStreamCollection streamCollection) {
            if (streamCollection != null) {
                this.streamCollection = streamCollection;
            } else {
                this.streamCollection = transformManager;
            }
        }

        StreamTester withContentTypes(@NonNull QualifiedContent.ContentType... contentTypes) {
            this.contentTypes.addAll(Arrays.asList(contentTypes));
            return this;
        }

        StreamTester withScopes(@NonNull QualifiedContent.Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        StreamTester withJar(@NonNull File file) {
            jars.add(file);
            return this;
        }

        StreamTester withJars(@NonNull Collection<File> files) {
            jars.addAll(files);
            return this;
        }

        StreamTester withFolder(@NonNull File file) {
            folders.add(file);
            return this;
        }

        StreamTester withFileCollection(@NonNull FileCollection fileCollection) {
            fileCollections.add(fileCollection);
            return this;
        }

        StreamTester withFolders(@NonNull Collection<File> files) {
            folders.addAll(files);
            return this;
        }

        StreamTester withRootLocation(@NonNull File file) {
            rootLocation = file;
            return this;
        }


        StreamTester withDependency(@NonNull Object dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        StreamTester withDependencies(@NonNull Collection<? extends Object> dependencies) {
            this.dependencies.addAll(dependencies);
            return this;
        }

        TransformStream test() {
            if (contentTypes.isEmpty() && scopes.isEmpty()) {
                fail("content-type and scopes empty in StreamTester");
            }

            ImmutableList<TransformStream> streams = streamCollection
                    .getStreams(new StreamFilter() {
                        @Override
                        public boolean accept(@NonNull Set<QualifiedContent.ContentType> types,
                                @NonNull Set<? super QualifiedContent.Scope> scopes) {
                            return (StreamTester.this.contentTypes.isEmpty() ||
                                    types.equals(contentTypes)) &&
                                    (StreamTester.this.scopes.isEmpty() ||
                                            StreamTester.this.scopes.equals(scopes));
                        }
                    });
            assertThat(streams)
                    .named(String.format("Streams matching requested %s/%s", scopes, contentTypes))
                            .hasSize(1);
            TransformStream stream = Iterables.getOnlyElement(streams);

            assertThat(stream.getContentTypes()).containsExactlyElementsIn(contentTypes);

            if (!fileCollections.isEmpty()) {
                for (FileCollection fileCollection : fileCollections) {
                    List<String> taskNames = fileCollection.getBuildDependencies()
                            .getDependencies(null /* task */)
                            .stream()
                            .map(Task::getName)
                            .collect(Collectors.toList());
                    assertThat(taskNames).containsExactlyElementsIn(dependencies);
                }
            }

            if (!jars.isEmpty()) {
                if (!(stream instanceof OriginalStream)) {
                    throw new RuntimeException("StreamTest.withJar(s) used on Stream that is not an OriginalStream");
                }

                OriginalStream originalStream = (OriginalStream) stream;
                Set<String> expectedFileNames = jars.stream().map(File::getName).collect(Collectors.toSet());
                for (File file : originalStream.getFileCollection().getFiles()) {
                    assertThat(expectedFileNames.contains(file.getName())).isTrue();
                    expectedFileNames.remove(file.getName());
                }
                assertThat(expectedFileNames).isEmpty();
            }

            if (!folders.isEmpty()) {
                if (!(stream instanceof OriginalStream)) {
                    throw new RuntimeException("StreamTest.withFolder(s) used on Stream that is not an OriginalStream");
                }

                OriginalStream originalStream = (OriginalStream) stream;
                assertThat(originalStream.getFileCollection().getFiles())
                        .containsExactlyElementsIn(folders);
            }

            if (rootLocation != null) {
                if (!(stream instanceof IntermediateStream)) {
                    throw new RuntimeException("StreamTest.withRootLocation used on Stream that is not an IntermediateStream");
                }

                IntermediateStream originalStream = (IntermediateStream) stream;
                assertThat(originalStream.getRootLocation()).isEqualTo(rootLocation);
            }

            return stream;
        }
    }

    @NonNull
    private static VariantScope getScope() {
        GlobalScope globalScope = mock(GlobalScope.class);
        when(globalScope.getBuildDir()).thenReturn(new File("build dir"));

        VariantScope scope = mock(VariantScope.class);
        when(scope.getDirName()).thenReturn("config dir name");
        when(scope.getGlobalScope()).thenReturn(globalScope);
        when(scope.getTaskName(Mockito.anyString())).thenReturn(TASK_NAME);
        when(scope.getFullVariantName()).thenReturn("theVariantName");

        CoreBuildType buildType = mock(CoreBuildType.class);
        when(buildType.getName()).thenReturn("debug");
        when(buildType.isDebuggable()).thenReturn(true);

        GradleVariantConfiguration variantConfiguration = mock(GradleVariantConfiguration.class);
        when(variantConfiguration.getType()).thenReturn(VariantTypeImpl.BASE_APK);
        when(variantConfiguration.getBuildType()).thenReturn(buildType);
        when(variantConfiguration.getProductFlavors()).thenReturn(ImmutableList.of());
        when(scope.getVariantConfiguration()).thenReturn(variantConfiguration);
        return scope;
    }

    /**
     * Returns the root dir for the gradle plugin project
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());

                File f= dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                return  new File(
                        f,
                        Joiner.on(File.separator).join(
                                "tools",
                                "base",
                                "build-system",
                                "integration-test"));
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        }

        fail("Fail to get the tools/build folder");
        return null;
    }
}
