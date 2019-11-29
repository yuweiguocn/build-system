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

package com.android.builder.internal.packaging;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.files.RelativeFile;
import com.android.ide.common.resources.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Closer;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link DexIncrementalRenameManager}.
 */
public class DexIncrementalRenameManagerTest {

    /**
     * Folder used for tests.
     */
    @Rule
    public final TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Closer to use.
     */
    @NonNull
    private final Closer mCloser = Closer.create();

    @After
    public void after() throws Exception {
        mCloser.close();
    }

    /**
     * Create a new manager in the temporary folder.
     *
     * @return the manager
     * @throws Exception failed to create the manager
     */
    @NonNull
    private DexIncrementalRenameManager make() throws Exception {
        return mCloser.register(new DexIncrementalRenameManager(mTemporaryFolder.getRoot()));
    }

    /**
     * Transforms a relative file specification and returns an incremental relative set. The
     * provided set will contain all provided paths in {@link FileStatus#NEW} state.
     *
     * @param paths paths to include as specs (see {@link #makeRelative(String)})
     * @return the incremental set
     */
    @NonNull
    private static ImmutableMap<RelativeFile, FileStatus> makeNew(String... paths) {
        Map<RelativeFile, FileStatus> map = Maps.newHashMap();

        for (String path : paths) {
            map.put(makeRelative(path), FileStatus.NEW);
        }

        return ImmutableMap.copyOf(map);
    }

    /**
     * Creates a relative file from a spec. A spec is in the form {@code a/b} where {@code a} is the
     * base file name {@code b} the relative file name. {@code b} can have multiple slashes (only
     * slashes should be used as separators)
     *
     * @param path the path
     * @return the relative file
     */
    @NonNull
    private static RelativeFile makeRelative(@NonNull String path) {
        String[] split = path.split("/", 2);
        File base = new File(split[0]);
        File file = new File(base, FileUtils.toSystemDependentPath(split[1]));
        return new RelativeFile(base, file);
    }

    /**
     * Creates a packaged file update based on the provided data.
     *
     * @param path the relative file specification (see {@link #makeRelative(String)})
     * @param name the file name
     * @param status the file status
     * @return the packaged file update
     */
    @NonNull
    private static PackagedFileUpdate packaged(@NonNull String path, @NonNull String name,
            @NonNull FileStatus status) {
        return new PackagedFileUpdate(makeRelative(path), name, status);
    }

    @Test
    public void addFilesToEmptyArchive() throws Exception {
        DexIncrementalRenameManager mgr = make();
        ImmutableMap<RelativeFile, FileStatus> set = makeNew("a/b", "b/c");

        Set<PackagedFileUpdate> updates = mgr.update(set);
        if (updates.contains(packaged("a/b", "classes.dex", FileStatus.NEW))) {
            assertThat(updates)
                    .containsExactly(
                            packaged("a/b", "classes.dex", FileStatus.NEW),
                            packaged("b/c", "classes2.dex", FileStatus.NEW));
        } else {
            assertThat(updates)
                    .containsExactly(
                            packaged("a/b", "classes2.dex", FileStatus.NEW),
                            packaged("b/c", "classes.dex", FileStatus.NEW));
        }

        set = makeNew("a/d");
        updates = mgr.update(set);
        assertThat(updates).containsExactly(packaged("a/d", "classes3.dex", FileStatus.NEW));
    }

    @Test
    public void updateFilesInArchive() throws Exception {
        DexIncrementalRenameManager mgr = make();
        ImmutableMap<RelativeFile, FileStatus> set = makeNew("a/b", "b/c");

        mgr.update(set);

        set = ImmutableMap.of(makeRelative("a/b"), FileStatus.CHANGED);
        Set<PackagedFileUpdate> updates = mgr.update(set);
        assertThat(updates).containsExactly(packaged("a/b", "classes.dex", FileStatus.CHANGED));
    }

    @Test
    public void deleteFilesInArchive() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());
        assertEquals(1, mgr.update(makeNew("x/c")).size());
        assertEquals(1, mgr.update(makeNew("x/d")).size());

        Set<PackagedFileUpdate> updates = mgr.update(
                ImmutableMap.of(makeRelative("x/d"), FileStatus.REMOVED));
        assertThat(updates).containsExactly(packaged("x/d", "classes4.dex", FileStatus.REMOVED));

        updates = mgr.update(ImmutableMap.of(makeRelative("x/b"), FileStatus.REMOVED));
        assertThat(updates)
                .containsExactly(
                        packaged("x/c", "classes3.dex", FileStatus.REMOVED),
                        packaged("x/c", "classes2.dex", FileStatus.CHANGED));
    }

    @Test
    public void addAndDeleteMoreAdds() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());
        assertEquals(1, mgr.update(makeNew("x/c")).size());
        assertEquals(1, mgr.update(makeNew("x/d")).size());

        Set<PackagedFileUpdate> updates = mgr.update(ImmutableMap.of(
                makeRelative("x/e"), FileStatus.NEW,
                makeRelative("x/f"), FileStatus.NEW,
                makeRelative("x/b"), FileStatus.REMOVED));
        assertEquals(2, updates.size());
        if (updates.contains(packaged("x/e", "classes2.dex", FileStatus.CHANGED))) {
            assertThat(updates)
                    .containsExactly(
                            packaged("x/e", "classes2.dex", FileStatus.CHANGED),
                            packaged("x/f", "classes5.dex", FileStatus.NEW));
        } else {
            assertThat(updates)
                    .containsExactly(
                            packaged("x/e", "classes5.dex", FileStatus.NEW),
                            packaged("x/f", "classes2.dex", FileStatus.CHANGED));
        }
    }

    @Test
    public void addAndDeleteMoreDeletes() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());
        assertEquals(1, mgr.update(makeNew("x/c")).size());
        assertEquals(1, mgr.update(makeNew("x/d")).size());

        Set<PackagedFileUpdate> updates = mgr.update(ImmutableMap.of(
                makeRelative("x/e"), FileStatus.NEW,
                makeRelative("x/a"), FileStatus.REMOVED,
                makeRelative("x/b"), FileStatus.REMOVED));
        assertThat(updates)
                .containsExactly(
                        packaged("x/e", "classes.dex", FileStatus.CHANGED),
                        packaged("x/d", "classes2.dex", FileStatus.CHANGED),
                        packaged("x/d", "classes4.dex", FileStatus.REMOVED));
    }

    @Test
    public void reverseOrderOfFilesInUpdate() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());
        assertEquals(1, mgr.update(makeNew("x/c")).size());

        Set<PackagedFileUpdate> updates =
                mgr.update(
                        ImmutableMap.of(
                                makeRelative("x/c"), FileStatus.REMOVED,
                                makeRelative("x/b"), FileStatus.REMOVED,
                                makeRelative("x/a"), FileStatus.CHANGED));
        assertThat(updates)
                .containsExactly(
                        packaged("x/a", "classes.dex", FileStatus.CHANGED),
                        packaged("x/b", "classes2.dex", FileStatus.REMOVED),
                        packaged("x/c", "classes3.dex", FileStatus.REMOVED));
    }

    @Test
    public void multipleClassesDexAdded() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());

        Set<PackagedFileUpdate> updates =
                mgr.update(
                        ImmutableMap.of(
                                makeRelative("x/classes.dex"), FileStatus.NEW,
                                makeRelative("y/classes.dex"), FileStatus.NEW));

        // one of the new files should be now classes.dex
        if (updates.contains(packaged("x/classes.dex", "classes.dex", FileStatus.CHANGED))) {
            assertThat(updates)
                    .contains(packaged("x/classes.dex", "classes.dex", FileStatus.CHANGED));
        } else {
            assertThat(updates)
                    .contains(packaged("y/classes.dex", "classes.dex", FileStatus.CHANGED));
        }

    }

    @Test
    public void saveState() throws Exception {
        DexIncrementalRenameManager mgr = make();

        assertEquals(1, mgr.update(makeNew("x/a")).size());
        assertEquals(1, mgr.update(makeNew("x/b")).size());

        mgr.close();

        mgr = make();
        Set<PackagedFileUpdate> updates = mgr.update(makeNew("x/c"));
        assertThat(updates).containsExactly(packaged("x/c", "classes3.dex", FileStatus.NEW));
    }

    @Test
    public void classesDexIsNotRenamed() throws Exception {
        DexIncrementalRenameManager mgr = make();

        PackagedFileUpdate pfu = Iterables.getOnlyElement(mgr.update(makeNew("x/y/classes.dex")));
        assertEquals("classes.dex", pfu.getName());
        assertEquals("y/classes.dex", pfu.getSource().getRelativePath());

        pfu = Iterables.getOnlyElement(mgr.update(makeNew("a/b/classes.dex")));
        assertEquals("classes2.dex", pfu.getName());
        assertEquals("b/classes.dex", pfu.getSource().getRelativePath());
    }

    @Test
    public void initialClassesDexNameIsKept() throws Exception {
        DexIncrementalRenameManager mgr = make();

        Set<PackagedFileUpdate> updates =
                mgr.update(makeNew("a/abc.dex", "a/classes.dex", "a/foo.dex"));
        PackagedFileUpdate cdex = Iterables.getOnlyElement(
                updates.stream()
                        .filter(u -> u.getName().equals("classes.dex"))
                        .collect(Collectors.toList()));
        assertEquals("classes.dex", cdex.getSource().getRelativePath());
    }

    @Test
    public void classesDexIsRemovedAndLaterAdded() throws Exception {
        DexIncrementalRenameManager mgr = make();

        PackagedFileUpdate pfu = Iterables.getOnlyElement(mgr.update(makeNew("x/y/classes.dex")));
        assertEquals("classes.dex", pfu.getName());

        PackagedFileUpdate pfu2 = Iterables.getOnlyElement(mgr.update(makeNew("x/y/aaa.dex")));
        assertEquals("classes2.dex", pfu2.getName());

        Set<PackagedFileUpdate> pfu3 = mgr.update(ImmutableMap.of(
                makeRelative("x/y/classes.dex"), FileStatus.REMOVED));
        assertEquals(2, pfu3.size());
        PackagedFileUpdate pfu3_1 = Iterables.getOnlyElement(pfu3.stream()
                .filter(u -> u.getName().equals("classes.dex"))
                .collect(Collectors.toList()));
        PackagedFileUpdate pfu3_2 = Iterables.getOnlyElement(pfu3.stream()
                .filter(u -> u.getName().equals("classes2.dex"))
                .collect(Collectors.toList()));

        assertEquals(FileStatus.CHANGED, pfu3_1.getStatus());
        assertEquals("classes.dex", pfu3_1.getName());
        assertEquals("y/aaa.dex", pfu3_1.getSource().getRelativePath());
        assertEquals(FileStatus.REMOVED, pfu3_2.getStatus());
        assertEquals("classes2.dex", pfu3_2.getName());
        assertEquals("y/aaa.dex", pfu3_2.getSource().getRelativePath());

        Set<PackagedFileUpdate> pfu4 = mgr.update(makeNew("x/y/z/classes.dex"));
        assertEquals(2, pfu4.size());
        PackagedFileUpdate pfu4_1 = Iterables.getOnlyElement(pfu4.stream()
                .filter(u -> u.getName().equals("classes.dex"))
                .collect(Collectors.toList()));
        PackagedFileUpdate pfu4_2 = Iterables.getOnlyElement(pfu4.stream()
                .filter(u -> u.getName().equals("classes2.dex"))
                .collect(Collectors.toList()));

        assertEquals(FileStatus.CHANGED, pfu4_1.getStatus());
        assertEquals("classes.dex", pfu4_1.getName());
        assertEquals("y/z/classes.dex", pfu4_1.getSource().getRelativePath());
        assertEquals(FileStatus.NEW, pfu4_2.getStatus());
        assertEquals("classes2.dex", pfu4_2.getName());
        assertEquals("y/aaa.dex", pfu4_2.getSource().getRelativePath());
    }

    @Test
    public void testDexFileComparator() {
        DexIncrementalRenameManager.DexFileComparator comparator =
                new DexIncrementalRenameManager.DexFileComparator();

        // Case 1. Both dex files have path classes.dex
        RelativeFile dexFile1 = makeRelative("x/classes.dex");
        RelativeFile dexFile2 = makeRelative("y/classes.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(-1);

        dexFile1 = makeRelative("y/classes.dex");
        dexFile2 = makeRelative("x/classes.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(1);

        dexFile1 = makeRelative("x/classes.dex");
        dexFile2 = makeRelative("x/classes.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(0);

        // Case 2. Only one dex file has path classes.dex
        dexFile1 = makeRelative("x/classes.dex");
        dexFile2 = makeRelative("x/classes2.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(-1);

        dexFile1 = makeRelative("x/classes2.dex");
        dexFile2 = makeRelative("x/classes.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(1);

        // Case 3. Neither dex files has path classes.dex
        dexFile1 = makeRelative("x/classes2.dex");
        dexFile2 = makeRelative("x/classes3.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(-1);

        dexFile1 = makeRelative("x/classes3.dex");
        dexFile2 = makeRelative("x/classes2.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(1);

        dexFile1 = makeRelative("x/classes3.dex");
        dexFile2 = makeRelative("y/classes2.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(-1);

        dexFile1 = makeRelative("y/classes2.dex");
        dexFile2 = makeRelative("x/classes3.dex");
        assertThat(comparator.compare(dexFile1, dexFile2)).isEqualTo(1);
    }
}
