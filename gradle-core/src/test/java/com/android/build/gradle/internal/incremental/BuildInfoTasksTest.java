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

package com.android.build.gradle.internal.incremental;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.builder.profile.ProcessProfileWriterFactory;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BuildInfoTasksTest {

    @Rule
    public final TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    File buildDir;
    File pastBuildsDirectory;
    File buildInfoFile;
    File tmpBuildInfoFile;

    Logger logger = Logging.getLogger(BuildInfoTasksTest.class);

    @Before
    public void createBuildDir() throws IOException {
        buildDir = mTemporaryFolder.newFolder();
        pastBuildsDirectory = new File(buildDir, "build/intermediates/builds/debug/");
        buildInfoFile = new File(buildDir, "build/intermediates/restart-dex/build-info.xml");
        tmpBuildInfoFile = new File(buildDir, "build/intermediates/restart-dex/tmp-build-info.xml");
        ProcessProfileWriterFactory.initializeForTests();
    }

    @Test
    public void testReload() throws IOException {
        initialFailedBuild();

        assertThat(buildInfoFile).doesNotExist();
        assertThat(tmpBuildInfoFile).exists();

        secondPassingBuild();

        assertThat(buildInfoFile).exists();
        assertThat(tmpBuildInfoFile).doesNotExist();
    }

    private void initialFailedBuild() throws IOException {
        Project project = createProject();
        InstantRunBuildContext context =
                new InstantRunBuildContext(
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        runLoaderTask(project, context);

        context.addChangedFile(FileType.RESOURCES, new File("resources-debug.ap_"));
        context.setBuildHasFailed();

        runWriterTask(createProject(), context);
    }

    private void secondPassingBuild() throws IOException {
        Project project = createProject();
        InstantRunBuildContext context =
                new InstantRunBuildContext(
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);

        runLoaderTask(project, context);

        context.addChangedFile(FileType.RELOAD_DEX, new File("reload dex.dex"));

        runWriterTask(createProject(), context);

        assertThat(context.getLastBuild()).isNotNull();
        assertThat(context.getLastBuild().getArtifacts()).hasSize(2);
        assertThat(context.getLastBuild().getArtifactForType(FileType.RELOAD_DEX)).isNotNull();
        assertThat(context.getLastBuild().getArtifactForType(FileType.RESOURCES)).isNotNull();
    }

    private void runLoaderTask(@NonNull Project project, @NonNull InstantRunBuildContext context) {
        BuildInfoLoaderTask loader = project.getTasks().create("loader", BuildInfoLoaderTask.class);
        loader.buildInfoFile = buildInfoFile;
        loader.tmpBuildInfoFile = tmpBuildInfoFile;
        loader.pastBuildsFolder = pastBuildsDirectory;
        loader.buildContext = context;
        loader.logger = logger;
        loader.executeAction();
    }

    private void runWriterTask(@NonNull Project project, @NonNull InstantRunBuildContext context) {
        BuildInfoWriterTask writer = project.getTasks().create("writer", BuildInfoWriterTask.class);
        writer.buildInfoFile = buildInfoFile;
        writer.tmpBuildInfoFile = tmpBuildInfoFile;
        writer.logger = logger;
        writer.buildContext = context;
        writer.executeAction();
    }

    private Project createProject() throws IOException {
        return ProjectBuilder.builder()
                .withName("app")
                .withProjectDir(mTemporaryFolder.newFolder())
                .build();
    }
}
