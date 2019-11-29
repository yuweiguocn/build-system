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

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TransformManagerTest extends TaskTestUtils {

    public static final String MY_FAKE_DEPENDENCY_TASK_NAME = "fake dependency";

    private Project project;
    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private FileCollection fileCollection;

    @Before
    @Override
    public void setUp() throws IOException {
        super.setUp();
        File projectDirectory = java.nio.file.Files.createTempDirectory(getClass().getName()).toFile();
        project = ProjectBuilder.builder().withProjectDir(projectDirectory).build();

        fileCollection = project.files(new File("my file")).builtBy(MY_FAKE_DEPENDENCY_TASK_NAME);
    }

    @Test
    public void simpleTransform() {
        // create a stream and add it to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // and a new one is up
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void missingStreams() {
        // create a stream and add it to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.RESOURCES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        transformManager.addTransform(taskFactory, scope, t);

        SyncIssue syncIssue = errorReporter.getSyncIssue();
        assertThat(syncIssue).isNotNull();
        assertThat(syncIssue.getMessage())
                .isEqualTo(
                        "Unable to add Transform 'transform name' on variant 'theVariantName': "
                                + "requested streams not available: [PROJECT]+[] / [RESOURCES]");
        assertThat(syncIssue.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
    }

    @Test
    public void referencedScope() {
        // create streams and add them to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(libClasses);

        TransformStream modulesClasses =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.SUB_PROJECTS)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(modulesClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES, Scope.SUB_PROJECTS)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(3);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        // check the referenced stream is still present
        assertThat(streams).containsAllOf(libClasses, modulesClasses);

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).containsAllOf(libClasses, modulesClasses);
    }

    @Test
    public void splitStreamByTypes() throws Exception {
        // test the case where the input stream has more types than gets consumed,
        // and we need to create a new stream with the unused types.
        // (class+res) -[class]-> (class, transformed) + (res, untouched)

        project.getTasks().create(MY_FAKE_DEPENDENCY_TASK_NAME, DefaultTask.class);

        // create streams and add them to the pipeline
        OriginalStream projectClassAndResources =
                OriginalStream.builder(project, "")
                        .addContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClassAndResources);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClassAndResources);

        // check we now have 2 streams, one for classes and one for resources.
        // the one for resources should match projectClassAndResources for location and dependency.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();
        streamTester()
                .withContentTypes(DefaultContentType.RESOURCES)
                .withScopes(Scope.PROJECT)
                .withDependencies(ImmutableList.of(MY_FAKE_DEPENDENCY_TASK_NAME))
                .withFileCollection(projectClassAndResources.getFileCollection())
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        streamTester(transformTask.consumedInputStreams)
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependencies(ImmutableList.of(MY_FAKE_DEPENDENCY_TASK_NAME))
                .withFileCollection(projectClassAndResources.getFileCollection())
                .test();
    }

    @Test
    public void splitStreamByScopesAndTypes() throws Exception {
        // test the case where the input stream has more types than gets consumed,
        // and more scopes than they get consumed.
        // and we need to create two new stream with the unused types and scopes.
        // (project+libs@classes+resources) -[project@classes] ->
        // 1. (project@classes, transformed)
        // 2. (libs@classes+resources, untouched)
        // 3. (project+libs@resources, untouched)
        project.getTasks().create(MY_FAKE_DEPENDENCY_TASK_NAME, DefaultTask.class);

        // create streams and add them to the pipeline
        IntermediateStream projectAndLibsClasses =
                IntermediateStream.builder(project, "", MY_FAKE_DEPENDENCY_TASK_NAME)
                        .addContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                        .addScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                        .setRootLocation(temporaryFolder.newFolder("folder"))
                        .build();

        transformManager.addStream(projectAndLibsClasses);

        // add a new transform
        Transform t =
                TestTransform.builder()
                        .setInputTypes(DefaultContentType.CLASSES)
                        .setScopes(Scope.PROJECT)
                        .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(3);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectAndLibsClasses);

        // check we now have 3 streams, one for classes and one for resources.
        // the one for resources should match projectClassAndResources for location and dependency.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();
        streamTester()
                .withContentTypes(DefaultContentType.RESOURCES)
                .withScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .withFileCollection(projectAndLibsClasses.getFileCollection())
                .withDependencies(ImmutableList.of(MY_FAKE_DEPENDENCY_TASK_NAME))
                .withRootLocation(projectAndLibsClasses.getRootLocation())
                .test();
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .withScopes(Scope.EXTERNAL_LIBRARIES)
                .withFileCollection(projectAndLibsClasses.getFileCollection())
                .withDependencies(ImmutableList.of(MY_FAKE_DEPENDENCY_TASK_NAME))
                .withRootLocation(projectAndLibsClasses.getRootLocation())
                .test();

        // we also check that the stream used by the transform only has the requested scopes.

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        streamTester(transformTask.consumedInputStreams)
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependencies(ImmutableList.of(MY_FAKE_DEPENDENCY_TASK_NAME))
                .withFileCollection(projectAndLibsClasses.getFileCollection())
                .withRootLocation(projectAndLibsClasses.getRootLocation())
                .test();
    }

    @Test
    public void splitReferencedStreamByTypes() throws IOException {
        // transform processes classes.
        // There's a (class, res) stream in a scope that's referenced. This stream should not
        // be split in two since it's not consumed.
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        TransformStream libClassAndResources =
                OriginalStream.builder(project, "")
                        .addContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(libClassAndResources);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .setReferencedScopes(Scope.EXTERNAL_LIBRARIES)
                .build();
        transformManager.addTransform(taskFactory, scope, t);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the referenced stream is still present in the list (ie not consumed, nor split)
        assertThat(streams).contains(libClassAndResources);
    }

    @Test
    public void splitStreamByScopes() throws Exception {
        // test the case where the input stream has more types than gets consumed,
        // and we need to create a new stream with the unused types.
        // (project+libs) -[project]-> (project, transformed) + (libs, untouched)
        project.getTasks().create(MY_FAKE_DEPENDENCY_TASK_NAME, DefaultTask.class);

        // create streams and add them to the pipeline
        IntermediateStream projectAndLibsClasses =
                IntermediateStream.builder(project, "", MY_FAKE_DEPENDENCY_TASK_NAME)
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                        .setRootLocation(temporaryFolder.newFolder("folder"))
                        .build();

        transformManager.addStream(projectAndLibsClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new streams
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectAndLibsClasses);

        // check we now have 2 streams, one for classes and one for resources.
        // the one for resources should match projectClassAndResources for location and dependency.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.EXTERNAL_LIBRARIES)
                .withFileCollection(projectAndLibsClasses.getFileCollection())
                .withDependencies(ImmutableList.of(MY_FAKE_DEPENDENCY_TASK_NAME))
                .withRootLocation(projectAndLibsClasses.getRootLocation())
                .test();

        // we also check that the stream used by the transform only has the requested scopes.

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        streamTester(transformTask.consumedInputStreams)
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependencies(ImmutableList.of(MY_FAKE_DEPENDENCY_TASK_NAME))
                .withFileCollection(projectAndLibsClasses.getFileCollection())
                .withRootLocation(projectAndLibsClasses.getRootLocation())
                .test();
    }

    @Test
    public void combinedScopes() throws Exception {
        // create streams and add them to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(libClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(libClasses);

        // check we now have 1 streams, containing both scopes.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT, Scope.EXTERNAL_LIBRARIES)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsAllOf(projectClass, libClasses);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void noOpTransform() throws Exception {
        // create stream and add them to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setReferencedScopes(Scope.PROJECT)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // check the class stream was not consumed.
        assertThat(transformManager.getStreams()).containsExactly(projectClass);

        // check the task contains no consumed streams
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).isEmpty();
        assertThat(transformTask.referencedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.outputStream).isNull();
    }

    @Test
    public void combinedTypes() {
        // create streams and add them to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        TransformStream libClasses =
                OriginalStream.builder(project, "")
                        .addContentType(DefaultContentType.RESOURCES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(libClasses);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(libClasses);

        // check we now have 1 streams, containing both types.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass, libClasses);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void forkInput() {
        // test the case where the transform creates an additional stream.
        // (class) -[class]-> (class) + (dex)

        // create streams and add them to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setOutputTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // check we now have a DEX/RES stream.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void forkInputWithMultiScopes() {
        // test the case where the transform creates an additional stream.
        // (class) -[class]-> (class) + (dex)

        // create streams and add them to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        TransformStream libClass =
                OriginalStream.builder(project, "")
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScope(Scope.SUB_PROJECTS)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(libClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setOutputTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .setScopes(Scope.PROJECT, Scope.SUB_PROJECTS)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(1);

        // check the class stream was consumed.
        assertThat(streams).doesNotContain(projectClass);
        assertThat(streams).doesNotContain(libClass);

        // check we now have a single stream with CLASS/DEX and both scopes.
        streamTester()
                .withContentTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .withScopes(Scope.PROJECT, Scope.SUB_PROJECTS)
                .withDependency(TASK_NAME)
                .test();

        // check the task contains the streams
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        assertThat(transformTask.consumedInputStreams).containsExactly(projectClass, libClass);
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(Iterables.getOnlyElement(streams));
    }

    @Test
    public void forkInputWithSplitStream() {
        // test the case where the transform creates an additional stream, and the original
        // stream has more than the requested type.
        // (class+res) -[class]-> (res, untouched) + (class, transformed) +(dex, transformed)

        // create streams and add them to the pipeline
        TransformStream projectClass =
                OriginalStream.builder(project, "")
                        .addContentTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                        .addScope(Scope.PROJECT)
                        .setFileCollection(fileCollection)
                        .build();
        transformManager.addStream(projectClass);

        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setOutputTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        TaskProvider<TransformTask> task =
                transformManager
                        .addTransform(taskFactory, scope, t)
                        .orElseThrow(mTransformTaskFailed);

        // get the new stream
        List<TransformStream> streams = transformManager.getStreams();
        assertThat(streams).hasSize(2);

        // check the multi-stream was consumed.
        assertThat(streams).doesNotContain(projectClass);

        // check we now have a DEX/CLASS stream.
        TransformStream outStream = streamTester()
                .withContentTypes(DefaultContentType.CLASSES, ExtendedContentType.DEX)
                .withScopes(Scope.PROJECT)
                .withDependency(TASK_NAME)
                .test();
        // and the remaining res stream, with the original dependency, and location
        streamTester()
                .withContentTypes(DefaultContentType.RESOURCES)
                .withScopes(Scope.PROJECT)
                .withDependency(MY_FAKE_DEPENDENCY_TASK_NAME)
                .withJar(new File("my file"))
                .test();

        // check the task contains the stream
        TransformTask transformTask = (TransformTask) taskFactory.findByName(task.getName());
        assertThat(transformTask).isNotNull();
        streamTester(transformTask.consumedInputStreams)
                .withContentTypes(DefaultContentType.CLASSES)
                .withScopes(Scope.PROJECT)
                .withDependency(MY_FAKE_DEPENDENCY_TASK_NAME)
                .withJar(new File("my file"))
                .test();
        assertThat(transformTask.referencedInputStreams).isEmpty();
        assertThat(transformTask.outputStream).isSameAs(outStream);
    }

    enum FakeContentType implements QualifiedContent.ContentType {
        FOO;

        @Override
        public int getValue() {
            return 0;
        }
    }

    @Test
    public void wrongInputType() {
        Transform t = TestTransform.builder()
                .setInputTypes(FakeContentType.FOO)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        Optional<TaskProvider<TransformTask>> task =
                transformManager.addTransform(taskFactory, scope, t);

        assertThat(task.isPresent()).isFalse();

        SyncIssue syncIssue = errorReporter.getSyncIssue();
        assertThat(syncIssue).isNotNull();
        assertThat(syncIssue.getMessage()).isEqualTo(
                "Custom content types "
                        + "(com.android.build.gradle.internal.pipeline.TransformManagerTest$FakeContentType)"
                        + " are not supported in transforms (transform name)");
        assertThat(syncIssue.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
    }

    @Test
    public void wrongOutputType() {
        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setOutputTypes(FakeContentType.FOO)
                .setScopes(Scope.PROJECT)
                .build();

        // add the transform
        Optional<TaskProvider<TransformTask>> task =
                transformManager.addTransform(taskFactory, scope, t);

        assertThat(task.isPresent()).isFalse();

        SyncIssue syncIssue = errorReporter.getSyncIssue();
        assertThat(syncIssue).isNotNull();
        assertThat(syncIssue.getMessage()).isEqualTo(
                "Custom content types "
                        + "(com.android.build.gradle.internal.pipeline.TransformManagerTest$FakeContentType)"
                        + " are not supported in transforms (transform name)");
        assertThat(syncIssue.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
    }

    @Test
    public void consumedProvidedOnlyScope() {
        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.PROVIDED_ONLY)
                .build();

        // add the transform
        Optional<TaskProvider<TransformTask>> task =
                transformManager.addTransform(taskFactory, scope, t);

        assertThat(task.isPresent()).isFalse();

        SyncIssue syncIssue = errorReporter.getSyncIssue();
        assertThat(syncIssue).isNotNull();
        assertThat(syncIssue.getMessage())
                .isEqualTo("PROVIDED_ONLY scope cannot be consumed by Transform 'transform name'");
        assertThat(syncIssue.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
    }

    @Test
    public void consumedTestedScope() {
        // add a new transform
        Transform t = TestTransform.builder()
                .setInputTypes(DefaultContentType.CLASSES)
                .setScopes(Scope.TESTED_CODE)
                .build();

        // add the transform
        Optional<TaskProvider<TransformTask>> task =
                transformManager.addTransform(taskFactory, scope, t);

        assertThat(task.isPresent()).isFalse();

        SyncIssue syncIssue = errorReporter.getSyncIssue();
        assertThat(syncIssue).isNotNull();
        assertThat(syncIssue.getMessage())
                .isEqualTo("TESTED_CODE scope cannot be consumed by Transform 'transform name'");
        assertThat(syncIssue.getType()).isEqualTo(SyncIssue.TYPE_GENERIC);
    }

    @Test
    public void testTaskName() throws Exception {
        Transform transform;

        transform =
                TestTransform.builder()
                        .setInputTypes(DefaultContentType.CLASSES)
                        .setName("Enhancer")
                        .build();
        assertThat(TransformManager.getTaskNamePrefix(transform))
                .isEqualTo("transformClassesWithEnhancerFor");

        transform =
                TestTransform.builder()
                        .setInputTypes(DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                        .setName("verifier")
                        .build();
        assertThat(TransformManager.getTaskNamePrefix(transform))
                .isEqualTo("transformClassesAndResourcesWithVerifierFor");

        transform =
                TestTransform.builder()
                        .setInputTypes(
                                ExtendedContentType.CLASSES_ENHANCED,
                                ExtendedContentType.NATIVE_LIBS)
                        .setName("fooBar")
                        .build();
        assertThat(TransformManager.getTaskNamePrefix(transform))
                .isEqualTo("transformClassesEnhancedAndNativeLibsWithFooBarFor");
    }

}