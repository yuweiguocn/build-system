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

package com.android.build.api.transform;

import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test for SecondaryFile
 */
public class SecondaryFileTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void incrementalWithFile() throws Exception {
        File file = new File("foo");
        SecondaryFile result = SecondaryFile.incremental(file);
        assertThat(result.getFile()).isEqualTo(file);
        assertThat(result.supportsIncrementalBuild()).isTrue();

        File testDir = temporaryFolder.newFolder();
        Project project = ProjectBuilder.builder().withProjectDir(testDir).build();
        assertThat(result.getFileCollection(project)).containsExactly(project.file(file));
    }

    @Test
    public void incrementalWithFileCollection() throws Exception {
        File testDir = temporaryFolder.newFolder();
        Project project = ProjectBuilder.builder().withProjectDir(testDir).build();
        File file = new File("foo");

        SecondaryFile result = SecondaryFile.incremental(project.files(file));
        assertThat(result.supportsIncrementalBuild()).isTrue();
        assertThat(result.getFileCollection(project)).containsExactly(project.file(file));
    }

    @Test
    public void nonIncrementalWithFile() throws Exception {
        File file = new File("foo");
        SecondaryFile result = SecondaryFile.nonIncremental(file);
        assertThat(result.getFile()).isEqualTo(file);
        assertThat(result.supportsIncrementalBuild()).isFalse();
    }

    @Test
    public void nonIncrementalWithFileCollection() throws Exception {
        File testDir = temporaryFolder.newFolder();
        Project project = ProjectBuilder.builder().withProjectDir(testDir).build();
        File file = new File("foo");

        SecondaryFile result = SecondaryFile.nonIncremental(project.files(file));
        assertThat(result.supportsIncrementalBuild()).isFalse();
        assertThat(result.getFileCollection(project)).containsExactly(project.file(file));
    }
}