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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link FeatureSplitDeclaration} */
public class FeatureSplitDeclarationTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock FileCollection fileCollection;
    @Mock FileTree fileTree;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(fileCollection.getAsFileTree()).thenReturn(fileTree);
    }

    @Test(expected = FileNotFoundException.class)
    public void testNoPersistenceFileFound() throws IOException {
        FeatureSplitDeclaration.load(fileCollection);
    }

    @Test
    public void testPersistence() throws IOException {
        FeatureSplitDeclaration featureSplitDeclaration =
                new FeatureSplitDeclaration("unique", "foo.bar");
        featureSplitDeclaration.save(temporaryFolder.getRoot());
        File[] files = temporaryFolder.getRoot().listFiles();
        assertThat(files).isNotNull();
        assertThat(files).hasLength(1);
        File persistedFile = files[0];
        assertThat(persistedFile.exists()).isTrue();

        when(fileTree.getFiles())
                .thenReturn(
                        ImmutableSet.of(
                                new File(
                                        temporaryFolder.getRoot(),
                                        FeatureSplitDeclaration.PERSISTED_FILE_NAME)));
        FeatureSplitDeclaration loadedDeclaration = FeatureSplitDeclaration.load(fileCollection);
        assertThat(featureSplitDeclaration.getModulePath())
                .isEqualTo(loadedDeclaration.getModulePath());
        assertThat(featureSplitDeclaration.getApplicationId())
                .isEqualTo(loadedDeclaration.getApplicationId());
    }
}
