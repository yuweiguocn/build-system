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

package com.android.builder.packaging;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.primitives.Bytes;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;

public class JarMergerTest {

    private static final ByteArrayHolder MYCLASS_CONTENT =
            new ByteArrayHolder(
                    new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x0});

    private static final ByteArrayHolder LIBCLASS_CONTENT =
            new ByteArrayHolder(
                    new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x2});

    private static final ByteArrayHolder RESOURCE_CONTENT =
            new ByteArrayHolder("resource content\n".getBytes(Charsets.UTF_8));
    private static final ByteArrayHolder RESOURCE_CONTENT_2 =
            new ByteArrayHolder("resource content 2\n".getBytes(Charsets.UTF_8));
    private static final ByteArrayHolder REMOVED_TYPEDEFS =
            new ByteArrayHolder(new byte[] {0x12, 0x34});

    @Test
    public void basicDirectory() throws Exception {
        Path out = Jimfs.newFileSystem(Configuration.unix()).getPath("/out/output.jar");
        Path dir = createDirectoryWithClassAndResource();

        try (JarMerger merger = new JarMerger(out)) {
            merger.addDirectory(dir);
        }

        assertThat(getEntries(out))
                .containsExactly(
                        "com/example/MyClass.class",
                        MYCLASS_CONTENT,
                        "resource.txt",
                        RESOURCE_CONTENT)
                .inOrder();
    }

    @Test
    public void basicDirectoryWindows() throws Exception {
        Path build = Jimfs.newFileSystem(Configuration.windows()).getPath("C:\\src\\build");
        Path out = build.resolve("jar.jar");
        Path dir = createDirectoryWithClassAndResource(build.resolve("inDirectory"));

        try (JarMerger merger = new JarMerger(out)) {
            merger.addDirectory(dir);
        }

        assertThat(getEntries(out))
                .containsExactly(
                        "com/example/MyClass.class",
                        MYCLASS_CONTENT,
                        "resource.txt",
                        RESOURCE_CONTENT)
                .inOrder();
    }

    @Test
    public void basicJar() throws Exception {
        Path out = Jimfs.newFileSystem(Configuration.unix()).getPath("/out/output.jar");
        Path dir = createJarWithClass();

        try (JarMerger merger = new JarMerger(out)) {
            merger.addJar(dir);
        }

        assertThat(getEntries(out))
                .containsExactly("com/example/lib/LibClass.class", LIBCLASS_CONTENT);
    }

    @Test
    public void basicFilter() throws Exception {
        Path out = Jimfs.newFileSystem(Configuration.unix()).getPath("/out/output.jar");
        try (JarMerger merger = new JarMerger(out, JarMerger.CLASSES_ONLY)) {
            merger.addDirectory(createDirectoryWithClassAndResource());
            merger.addJar(createJarWithClass());
        }

        assertThat(getEntries(out))
                .containsExactly(
                        "com/example/MyClass.class",
                        MYCLASS_CONTENT,
                        "com/example/lib/LibClass.class",
                        LIBCLASS_CONTENT);
    }

    @Test
    public void basicFilter2() throws Exception {
        Path out = Jimfs.newFileSystem(Configuration.unix()).getPath("/out/output.jar");
        try (JarMerger merger = new JarMerger(out, JarMerger.EXCLUDE_CLASSES)) {
            merger.addDirectory(createDirectoryWithClassAndResource());
            merger.addJar(createJarWithClass());
        }
        assertThat(getEntries(out)).containsExactly("resource.txt", RESOURCE_CONTENT);
    }

    @Test
    public void transformerInstrument() throws Exception {
        Path out = Jimfs.newFileSystem(Configuration.unix()).getPath("/out/output.jar");

        JarMerger.Transformer transformer =
                (path, is) -> {
                    switch (path) {
                        case "com/example/MyClass.class":
                            return new ByteArrayInputStream(REMOVED_TYPEDEFS.data);
                        default:
                            throw new IllegalArgumentException("Unexpected path" + path);
                    }
                };
        try (JarMerger merger = new JarMerger(out, JarMerger.CLASSES_ONLY)) {
            merger.addDirectory(
                    createDirectoryWithClassAndResource(),
                    JarMerger.CLASSES_ONLY,
                    transformer,
                    null);
        }
        assertThat(getEntries(out)).containsExactly("com/example/MyClass.class", REMOVED_TYPEDEFS);
    }

    @Test
    public void transformerRemove() throws Exception {
        Path out = Jimfs.newFileSystem(Configuration.unix()).getPath("/out/output.jar");
        JarMerger.Transformer transformer =
                (path, is) -> {
                    switch (path) {
                        case "com/example/MyClass.class":
                            return null;
                        default:
                            throw new IllegalArgumentException("Unexpected path" + path);
                    }
                };
        try (JarMerger merger = new JarMerger(out)) {
            merger.addDirectory(
                    createDirectoryWithClassAndResource(),
                    JarMerger.CLASSES_ONLY,
                    transformer,
                    null);
        }
        assertThat(getEntries(out)).isEmpty();
    }

    @Test
    public void preservesCompression() throws IOException {
        Path root = Jimfs.newFileSystem(Configuration.unix()).getPath("/");
        Path out = root.resolve("/out/output.jar");
        Path jar = root.resolve("/in/jar.jar");

        Files.createDirectories(jar.getParent());
        try (JarOutputStream jos =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jar)))) {
            ZipEntry stored = new ZipEntry("stored.txt");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(RESOURCE_CONTENT.data.length);
            CRC32 checksum = new CRC32();
            checksum.update(RESOURCE_CONTENT.data);
            stored.setCrc(checksum.getValue());
            jos.putNextEntry(stored);
            jos.write(RESOURCE_CONTENT.data);
            jos.closeEntry();

            ZipEntry deflated = new ZipEntry("deflated.txt");
            deflated.setMethod(ZipEntry.DEFLATED);
            jos.putNextEntry(deflated);
            jos.write(RESOURCE_CONTENT_2.data);
            jos.closeEntry();
        }

        try (JarMerger merger = new JarMerger(out)) {
            merger.addJar(jar);
        }

        assertThat(getEntries(out))
                .containsExactly(
                        "stored.txt[stored]", RESOURCE_CONTENT, "deflated.txt", RESOURCE_CONTENT_2);
    }

    private static Path createDirectoryWithClassAndResource() throws IOException {
        return createDirectoryWithClassAndResource(
                Jimfs.newFileSystem(Configuration.unix()).getPath("test", "dir"));
    }

    private static Path createDirectoryWithClassAndResource(@NonNull Path dir) throws IOException {
        Files.createDirectories(dir);
        Path classFile = dir.resolve("com").resolve("example").resolve("MyClass.class");
        Path resourceFile = dir.resolve("resource.txt");
        Files.write(resourceFile, RESOURCE_CONTENT.data);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, MYCLASS_CONTENT.data);
        return dir;
    }

    private static Path createJarWithClass() throws IOException {
        Path jar = Jimfs.newFileSystem(Configuration.unix()).getPath("test", "testjar.jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream jos =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jar)))) {
            jos.putNextEntry(new ZipEntry("pointless/dir/"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("com/example/lib/LibClass.class"));
            jos.write(LIBCLASS_CONTENT.data);
            jos.closeEntry();
        }
        return jar;
    }

    private static ImmutableMap<String, ByteArrayHolder> getEntries(@NonNull Path zip)
            throws IOException {
        ImmutableMap.Builder<String, ByteArrayHolder> builder = ImmutableMap.builder();
        try (ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ByteStreams.copy(zipInputStream, baos);
                String name = entry.getName();
                if (entry.getMethod() == ZipEntry.STORED) {
                    name += "[stored]";
                }
                builder.put(name, new ByteArrayHolder(baos.toByteArray()));
            }
        }
        return builder.build();
    }

    private static JarMerger.Transformer exampleTransformer() {
        return (path, is) -> {
            switch (path) {
                case "com/example/MyClass.class":
                    return new ByteArrayInputStream(REMOVED_TYPEDEFS.data);
                default:
                    throw new IllegalArgumentException("Unexpected path" + path);
            }
        };
    }

    static class ByteArrayHolder {
        final byte[] data;

        public ByteArrayHolder(byte[] data) {
            this.data = data;
        }

        public int hashCode() {
            return 31 + Arrays.hashCode(this.data);
        }

        public boolean equals(Object obj) {
            return this == obj
                    || obj instanceof ByteArrayHolder
                            && Arrays.equals(this.data, ((ByteArrayHolder) obj).data);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add(
                            "bytes",
                            Bytes.asList(data)
                                    .stream()
                                    .map(b -> String.format("%02x", b))
                                    .collect(Collectors.joining(" ")))
                    .toString();
        }
    }
}
