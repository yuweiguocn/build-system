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

package com.android.builder.internal.compiler;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DirectoryWalkerTest {
    @Parameterized.Parameters
    public static Iterable<? extends Object> data() {
        return ImmutableList.of(Configuration.unix(), Configuration.windows(), Configuration.osX());
    }

    @Parameterized.Parameter public Configuration configuration;

    private FileSystem fs;
    private Path root;
    private Path noExtension;
    private Path proto;
    private Path trailingDot;
    private Path fileAtRoot;
    private Path mainJava;
    private Path utilsJava;
    private Collection<Path> paths;

    private void createFiles(Collection<Path> files) throws IOException {
        for (Path file : files) {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(file.getParent());
            }
            Files.createFile(file);
        }
    }

    private Path fromRoot(FileSystem fs, String... parts) {
        if (configuration == Configuration.windows()) {
            // JimFS doesn't support getting absolute paths from the current drive, so we need to
            // specify the drive explicitly.
            return fs.getPath("C:\\", parts);
        }

        return fs.getPath(fs.getSeparator(), parts);
    }

    @Before
    public void setUp() throws IOException {
        fs = Jimfs.newFileSystem(configuration);
        root = fromRoot(fs);

        noExtension = fromRoot(fs, "config", "noextension");
        proto = fromRoot(fs, "config", "something.proto");
        trailingDot = fromRoot(fs, "config", "trailingdot.");
        fileAtRoot = fromRoot(fs, "file_at_root.md");
        mainJava = fromRoot(fs, "src", "main", "com", "google", "example", "Main.java");
        utilsJava = fromRoot(fs, "src", "main", "com", "google", "example", "Utils.java");

        paths = ImmutableList.of(noExtension, proto, trailingDot, fileAtRoot, mainJava, utilsJava);

        createFiles(paths);
    }

    @Test
    public void testBasicWalk() throws IOException {
        List<Path> foundPaths = Lists.newArrayList();
        DirectoryWalker.builder()
                .root(root)
                .action((root, path) -> foundPaths.add(path))
                .build()
                .walk();

        assertThat(paths).containsExactlyElementsIn(foundPaths);
    }

    @Test
    public void testExtensionFilterWalk() throws IOException {
        List<Path> foundPaths = Lists.newArrayList();
        DirectoryWalker.builder()
                .root(root)
                .extensions("java")
                .action((root, path) -> foundPaths.add(path))
                .build()
                .walk();

        assertThat(ImmutableList.of(mainJava, utilsJava)).containsExactlyElementsIn(foundPaths);
    }

    @Test
    public void testNonRootWalk() throws IOException {
        List<Path> foundPaths = Lists.newArrayList();
        DirectoryWalker.builder()
                .root(root.resolve("config"))
                .action((root, path) -> foundPaths.add(path))
                .build()
                .walk();

        assertThat(ImmutableList.of(noExtension, proto, trailingDot))
                .containsExactlyElementsIn(foundPaths);
    }

    @Test
    public void testNonRootExtensionWalk() throws IOException {
        List<Path> foundPaths = Lists.newArrayList();
        DirectoryWalker.builder()
                .root(root.resolve("config"))
                .extensions("proto")
                .action((root, path) -> foundPaths.add(path))
                .build()
                .walk();

        assertThat(ImmutableList.of(proto)).containsExactlyElementsIn(foundPaths);
    }

    @Test
    public void testRootIsRoot() throws IOException {
        DirectoryWalker.builder()
                .root(root)
                .action((foundRoot, path) -> assertEquals(root, foundRoot))
                .build()
                .walk();
    }

    /**
     * The current behaviour for attempting to walk a directory that does not exist is to silently
     * do nothing. This may not be ideal behaviour, but it is relied on in parts of the code base.
     */
    @Test
    public void testNonExistentDirectoryWalk() throws IOException {
        List<Path> foundPaths = Lists.newArrayList();
        DirectoryWalker.builder()
                .root(root.resolve("wubbalubbadubdub"))
                .action((root, path) -> foundPaths.add(path))
                .build()
                .walk();

        assertThat(ImmutableList.of()).containsExactlyElementsIn(foundPaths);
    }

    @Test
    public void testLazyFileAction() throws IOException {
        List<Path> foundPaths = Lists.newArrayList();
        DirectoryWalker.builder()
                .root(root)
                .action(() -> (root, path) -> foundPaths.add(path))
                .build()
                .walk();

        assertThat(paths).containsExactlyElementsIn(foundPaths);
    }

    @Test
    public void testLazyFileActionNotCreatedIfNotNeeded() throws IOException {
        DirectoryWalker.builder()
                .root(Files.createTempDirectory(root, ""))
                .action(
                        () -> {
                            fail("this action should never be created");
                            return (root, path) -> {};
                        })
                .build()
                .walk();
    }

    /**
     * We support the {@code walk()} method being called multiple times on the same walker instance.
     * I can't think of good use-cases for this off the top of my head, but it's good to be explicit
     * about the API surface a class offers, and back it up with tests.
     */
    @Test
    public void testDoubleWalk() throws IOException {
        List<Path> foundPaths = Lists.newArrayList();

        DirectoryWalker.builder()
                .root(root)
                .action((root, path) -> foundPaths.add(path))
                .build()
                .walk()
                .walk();

        assertEquals(paths.size() * 2, foundPaths.size());
    }

    @Test
    public void testNullRoot() throws IOException {
        try {
            DirectoryWalker.builder()
                    .root(null) // bad, not allowed
                    .action((root, path) -> {})
                    .build();

            fail("code should throw before this point");
        } catch (IllegalArgumentException iae) {
            assertEquals("cannot pass in a null root directory", iae.getMessage());
        }
    }

    @Test
    public void testForgottenRoot() throws IOException {
        try {
            DirectoryWalker.builder()
                    // .root(root)
                    .action((root, path) -> {})
                    .build();

            fail("code should throw before this point");
        } catch (IllegalArgumentException iae) {
            assertEquals("root cannot be left unset", iae.getMessage());
        }
    }

    @Test
    public void testForgottenAction() throws IOException {
        try {
            DirectoryWalker.builder()
                    .root(root)
                    // .action((root, path) -> ...)
                    .build();

            fail("code should throw before this point");
        } catch (IllegalArgumentException iae) {
            assertEquals("action cannot be left unset", iae.getMessage());
        }
    }

    @Test
    public void testNullExtension() throws IOException {
        try {
            DirectoryWalker.builder()
                    .root(root)
                    .extensions(null, "java") // bad, not allowed
                    .action((root, path) -> {})
                    .build();

            fail("code should throw before this point");
        } catch (IllegalArgumentException iae) {
            assertEquals("cannot pass in a null extension", iae.getMessage());
        }
    }

    @Test
    public void testEmptyExtension() throws IOException {
        try {
            DirectoryWalker.builder()
                    .root(root)
                    .extensions("", "java") // bad, not allowed
                    .action((root, path) -> {})
                    .build();

            fail("code should throw before this point");
        } catch (IllegalArgumentException iae) {
            assertEquals("cannot pass in an empty extension", iae.getMessage());
        }
    }

    @Test
    public void testNullLazyAction() throws IOException {
        try {
            DirectoryWalker.builder()
                    .root(root)
                    .action(() -> null) // bad, not allowed
                    .build()
                    .walk(); // note that this test needs to call walk() to trigger the error

            fail("code should throw before this point");
        } catch (NullPointerException npe) {
            assertEquals("action supplier cannot return null action", npe.getMessage());
        }
    }

    @Test
    public void testLazyActionSupplierOnlyCalledOnce() throws IOException {
        AtomicInteger getCounter = new AtomicInteger(0);
        AtomicInteger fileCounter = new AtomicInteger(0);

        DirectoryWalker.builder()
                .root(root)
                .action(
                        () -> {
                            getCounter.incrementAndGet();
                            return (root, path) -> fileCounter.incrementAndGet();
                        })
                .build()
                .walk();

        assertEquals(1, getCounter.get());

        /*
         * In order to test that get() is only called once, we also need to assert that we scanned
         * more than one file. This exercises the memoization we use on the supplier.
         */
        assertNotEquals(0, fileCounter.get());
        assertNotEquals(1, fileCounter.get());
    }

    /**
     * We should, by default, walk symlinks. This preserves behaviour that users depend on from at
     * least 2.3.0, as seen in https://issuetracker.google.com/68262460.
     */
    @Test
    public void testSymlink() throws IOException {
        Path example = fromRoot(fs, "exampleSymlink");
        Files.createSymbolicLink(example, fromRoot(fs, "src", "main", "com", "google", "example"));

        List<Path> foundPaths = Lists.newArrayList();
        DirectoryWalker.builder()
                .root(example)
                .action((root, path) -> foundPaths.add(path))
                .build()
                .walk();

        assertThat(foundPaths)
                .containsExactly(example.resolve("Main.java"), example.resolve("Utils.java"));
    }
}
