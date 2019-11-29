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

package com.android.build.gradle.internal.tasks.featuresplit;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the {@link FeatureSplitDeclarationWriterTask} */
public class FeatureSplitDeclarationWriterTaskTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    Project project;
    FeatureSplitDeclarationWriterTask task;
    File outputDirectory;

    @Before
    public void setUp() throws IOException {

        File testDir = temporaryFolder.newFolder();
        outputDirectory = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", FeatureSplitDeclarationWriterTask.class);
        task.outputDirectory = outputDirectory;
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
    }

    @Test
    public void testTask() throws IOException {
        task.uniqueIdentifier = "unique_split";
        task.originalApplicationIdSupplier = () -> null;
        task.fullTaskAction();
        File[] files = outputDirectory.listFiles();
        assertThat(files).hasLength(1);

        FeatureSplitDeclaration loadedDecl = FeatureSplitDeclaration.load(files[0]);
        assertThat("unique_split").isEqualTo(loadedDecl.getModulePath());
    }
}
