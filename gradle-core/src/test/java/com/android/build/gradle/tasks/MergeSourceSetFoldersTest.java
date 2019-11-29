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

package com.android.build.gradle.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.builder.core.BuilderConstants;
import com.android.ide.common.resources.AssetSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MergeSourceSetFoldersTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    public Project project;
    public MergeSourceSetFolders task;
    private List<AssetSet> folderSets;

    @Before
    public void setUp() throws IOException {
        File testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", MergeSourceSetFolders.class);

        folderSets = Lists.newArrayList();
        task.setAssetSetSupplier(() -> folderSets);
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
    }

    @Test
    public void singleSetWithSingleFile() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
    }

    @Test
    public void singleSetWithMultiFiles() throws Exception {
        File file = new File("src/main");
        File file2 = new File("src/main2");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file, file2);

        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
    }

    @Test
    public void twoSetsWithSingleFile() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        File file2 = new File("src/debug");
        AssetSet debugSet = createAssetSet(folderSets, "debug", file2);

        assertThat(task.computeAssetSetList()).containsExactly(mainSet, debugSet);
    }

    @Test
    public void singleSetWithDependency() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        File file2 = new File("foo/bar/1.0");
        List<AssetSet> librarySets = setupLibraryDependencies(file2, ":path");

        assertThat(task.getLibraries().getFiles()).containsExactly(file2);
        assertThat(task.computeAssetSetList()).containsExactly(librarySets.get(0), mainSet).inOrder();
    }

    @Test
    public void singleSetWithRenderscript() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        File shaderFile = new File("shader");
        setBuildableArtifact(task::setShadersOutputDir, shaderFile);

        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
        // shader file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, shaderFile);
    }

    @Test
    public void singleSetWithGeneratedRes() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        File copyApkFile = new File("copyApk");
        setFileCollection(task::setCopyApk, copyApkFile);

        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
        // copyApk file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, copyApkFile);
    }

    @Test
    public void everything() throws Exception {
        File file = new File("src/main");
        File file2 = new File("src/main2");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file, file2);

        File debugFile = new File("src/debug");
        AssetSet debugSet = createAssetSet(folderSets, "debug", debugFile);

        File libFile = new File("foo/bar/1.0");
        File libFile2 = new File("foo/bar/2.0");

        // the order returned by the dependency is meant to be in the wrong order (consumer first,
        // when we want dependent first for the merger), so the order in the asset set should be
        // the opposite order.
        List<AssetSet> librarySets = setupLibraryDependencies(
                libFile, "foo:bar:1.0",
                libFile2, "foo:bar:2.0");
        AssetSet librarySet = librarySets.get(0);
        AssetSet librarySet2 = librarySets.get(1);

        File shaderFile = new File("shader");
        setBuildableArtifact(task::setShadersOutputDir, shaderFile);

        File copyApkFile = new File("copyApk");
        setFileCollection(task::setCopyApk, copyApkFile);

        assertThat(task.getLibraries().getFiles()).containsExactly(libFile, libFile2);
        assertThat(task.computeAssetSetList())
                .containsExactly(librarySet2, librarySet, mainSet, debugSet)
                .inOrder();
        // generated files should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles())
                .containsExactly(file, file2, shaderFile, copyApkFile);
    }

    @NonNull
    private static AssetSet createAssetSet(
            @Nullable List<AssetSet> folderSets,
            @NonNull String name,
            @NonNull File... files) {
        AssetSet mainSet = new AssetSet(name);
        mainSet.addSources(Arrays.asList(files));
        if (folderSets != null) {
            folderSets.add(mainSet);
        }
        return mainSet;
    }

    private static void setFileCollection(Consumer<FileCollection> setter, File... files) {
        FileCollection fileCollection = mock(FileCollection.class);
        Set<File> fileSet = ImmutableSet.copyOf(Arrays.asList(files));
        when(fileCollection.getFiles()).thenReturn(fileSet);
        setter.accept(fileCollection);
    }


    private static void setBuildableArtifact(Consumer<BuildableArtifact> setter, File... files) {
        BuildableArtifact fileCollection = mock(BuildableArtifact.class);
        Set<File> fileSet = ImmutableSet.copyOf(Arrays.asList(files));
        when(fileCollection.getFiles()).thenReturn(fileSet);
        setter.accept(fileCollection);
    }

    @NonNull
    private List<AssetSet> setupLibraryDependencies(Object... objects) {
        ArtifactCollection libraries = mock(ArtifactCollection.class);

        Set<ResolvedArtifactResult> artifacts = new LinkedHashSet<>();
        Set<File> files = new HashSet<>();
        List<AssetSet> assetSets = Lists.newArrayListWithCapacity(objects.length/2);

        for (int i = 0, count = objects.length; i < count ; i+=2) {
            assertThat(objects[i]).isInstanceOf(File.class);
            assertThat(objects[i+1]).isInstanceOf(String.class);

            File file = (File) objects[i];
            String path = (String) objects[i+1];

            files.add(file);

            ResolvedArtifactResult artifact = mock(ResolvedArtifactResult.class);
            artifacts.add(artifact);

            ComponentArtifactIdentifier artifactId = mock(ComponentArtifactIdentifier.class);
            ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);

            when(id.getProjectPath()).thenReturn(path);
            when(artifactId.getComponentIdentifier()).thenReturn(id);
            when(artifact.getFile()).thenReturn(file);
            when(artifact.getId()).thenReturn(artifactId);

            // create a resource set that must match the one returned by the computation
            AssetSet set = new AssetSet(path);
            set.addSource(file);
            assetSets.add(set);
        }

        FileCollection fileCollection = mock(FileCollection.class);
        when(fileCollection.getFiles()).thenReturn(files);

        when(libraries.getArtifacts()).thenReturn(artifacts);
        when(libraries.getArtifactFiles()).thenReturn(fileCollection);

        task.setLibraries(libraries);

        return assetSets;
    }
}