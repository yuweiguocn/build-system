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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.builder.core.DesugarProcessArgs;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link DesugarWorkerItem} class */
public class DesugarWorkerItemTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    Path java8SupportJar = Paths.get("/path/to/java8.jar");

    @Mock WorkerConfiguration workerConfiguration;
    @Mock JavaForkOptions options;
    @Captor ArgumentCaptor<Action<? super JavaForkOptions>> forOptionsCaptor;
    @Captor ArgumentCaptor<Iterable<File>> classpathCaptor;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testWorkerConfiguration() throws IOException {
        DesugarProcessArgs args =
                new DesugarProcessArgs(
                        ImmutableMap.of("/dev/null", "/dev/null"),
                        ImmutableList.of(),
                        ImmutableList.of(),
                        temporaryFolder.newFolder().toString(),
                        true,
                        21,
                        true);
        DesugarWorkerItem workerItem =
                new DesugarWorkerItem(java8SupportJar, args, temporaryFolder.getRoot().toPath());

        when(workerConfiguration.getForkOptions()).thenReturn(options);
        doAnswer(
                        invocation -> {
                            Action<? super JavaForkOptions> action =
                                    (Action<? super JavaForkOptions>) invocation.getArguments()[0];
                            action.execute(options);
                            return null;
                        })
                .when(workerConfiguration)
                .forkOptions(forOptionsCaptor.capture());

        workerItem.configure(workerConfiguration);
        verify(options)
                .setJvmArgs(
                        ImmutableList.of(
                                "-Xmx64m",
                                "-Djdk.internal.lambda.dumpProxyClasses="
                                        + temporaryFolder.getRoot().toPath().toString()));
        verify(workerConfiguration).forkOptions(forOptionsCaptor.capture());
        forOptionsCaptor.getValue().execute(options);
        verify(workerConfiguration).setIsolationMode(IsolationMode.PROCESS);

        List<String> params =
                Arrays.asList(
                        "--verbose",
                        "--input",
                        "/dev/null",
                        "--output",
                        "/dev/null",
                        "--min_sdk_version",
                        "21",
                        "--nodesugar_try_with_resources_if_needed",
                        "--desugar_try_with_resources_omit_runtime_classes",
                        "--legacy_jacoco_fix",
                        "--copy_bridges_from_classpath");
        verify(workerConfiguration).setParams(params);
    }

    @Test
    public void testClasspath() throws IOException {
        DesugarProcessArgs args =
                new DesugarProcessArgs(
                        ImmutableMap.of("/dev/null", "/dev/null"),
                        ImmutableList.of("path/to/1.jar", "path/to/2.jar"),
                        ImmutableList.of(),
                        temporaryFolder.newFolder().toString(),
                        true,
                        21,
                        true);

        DesugarWorkerItem workerItem =
                new DesugarWorkerItem(java8SupportJar, args, temporaryFolder.getRoot().toPath());

        doNothing().when(workerConfiguration).classpath(classpathCaptor.capture());
        workerItem.configure(workerConfiguration);

        assertThat(classpathCaptor.getValue()).contains(java8SupportJar.toFile());
    }

    @Test
    public void testParameters() throws IOException {
        DesugarProcessArgs args =
                new DesugarProcessArgs(
                        ImmutableMap.of("/dev/null", "/dev/null"),
                        ImmutableList.of(),
                        ImmutableList.of(),
                        temporaryFolder.newFolder().toString(),
                        false,
                        23,
                        true);

        DesugarWorkerItem workerItem =
                new DesugarWorkerItem(java8SupportJar, args, temporaryFolder.getRoot().toPath());
        workerItem.configure(workerConfiguration);

        List<String> params =
                Arrays.asList(
                        "--input",
                        "/dev/null",
                        "--output",
                        "/dev/null",
                        "--min_sdk_version",
                        "23",
                        "--nodesugar_try_with_resources_if_needed",
                        "--desugar_try_with_resources_omit_runtime_classes",
                        "--legacy_jacoco_fix",
                        "--copy_bridges_from_classpath");
        verify(workerConfiguration).setParams(params);
    }
}
