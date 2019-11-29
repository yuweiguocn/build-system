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

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;
import static com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

public class CustomClassTransformTest {

    private static final String FAKE_DEPENDENCIES = "fake_dependencies";
    @Mock Context context;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File transformJar;

    public static class TestTransform implements BiConsumer<InputStream, OutputStream> {
        @Override
        public void accept(InputStream inputStream, OutputStream outputStream) {
            try {
                ByteStreams.copy(inputStream, outputStream);
                outputStream.write("*".getBytes(Charsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Before
    public void setUp() throws IOException {
        String name = TestTransform.class.getName();
        String entry = name.replace('.', '/') + ".class";
        String resource = "/" + entry;
        URL url = TestTransform.class.getResource(resource);
        transformJar = temporaryFolder.newFile("transform.jar");

        try (InputStream res = url.openStream();
                ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(transformJar))) {
            ZipEntry e = new ZipEntry(entry);
            zip.putNextEntry(e);
            ByteStreams.copy(res, zip);
            zip.closeEntry();

            e = new ZipEntry("META-INF/services/java.util.function.BiConsumer");
            zip.putNextEntry(e);
            zip.write(name.getBytes(Charsets.UTF_8));
            zip.closeEntry();

            e = new ZipEntry("dependencies/" + FAKE_DEPENDENCIES + ".jar");
            zip.putNextEntry(e);
            zip.write("foo".getBytes(Charsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static File addFakeFile(File file, String name, String content) throws IOException {
        if (file.getName().endsWith(".jar")) {
            Map<String, String> env = new HashMap<>();
            env.put("create", "true");
            URI uri = URI.create("jar:" + file.toURI());
            try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
                Path pathInZipfile = zipfs.getPath(name);
                // copy a file into the zip file
                Files.write(pathInZipfile, content.getBytes(Charsets.UTF_8));
            }
            return file;
        } else {
            File clazz = new File(file, name);
            clazz.getParentFile().mkdirs();
            Files.write(clazz.toPath(), content.getBytes(Charsets.UTF_8));
            return clazz;
        }
    }

    private static void assertValidTransform(Path in, Path out) throws IOException {

        Set<String> outs = new HashSet<>();
        for (Path path : Files.newDirectoryStream(out)) {
            outs.add(path.getFileName().toString());
        }
        for (Path file : Files.newDirectoryStream(in)) {

            Path outFile = out.resolve(file.getFileName());
            if (Files.isDirectory(file)) {
                Truth.assertThat(outs).contains(file.getFileName().toString());
                Truth.assertThat(Files.isDirectory(outFile)).isTrue();
                assertValidTransform(file, outFile);
            } else if (file.toString().endsWith(".jar")) {
                Truth.assertThat(outs).contains(file.getFileName().toString());
                Truth.assertThat(outFile.toString().endsWith(".jar")).isTrue();
                URI inUri = URI.create("jar:" + file.toUri());
                URI outUri = URI.create("jar:" + outFile.toUri());
                try (FileSystem infs = FileSystems.newFileSystem(inUri, new HashMap<>());
                        FileSystem outfs = FileSystems.newFileSystem(outUri, new HashMap<>())) {
                    ArrayList<Path> inRoots = Lists.newArrayList(infs.getRootDirectories());
                    ArrayList<Path> outRoots = Lists.newArrayList(outfs.getRootDirectories());
                    Truth.assertThat(inRoots.size()).isEqualTo(outRoots.size());
                    for (int i = 0; i < inRoots.size(); i++) {
                        assertValidTransform(inRoots.get(i), outRoots.get(i));
                    }
                }
            } else if (file.toString().endsWith(".class")) {
                Truth.assertThat(outs).contains(file.getFileName().toString());
                String before = new String(Files.readAllBytes(file), Charsets.UTF_8);
                String after = new String(Files.readAllBytes(outFile), Charsets.UTF_8);
                Truth.assertThat(after).isEqualTo(before + "*");
            } else {
                Truth.assertThat(Files.exists(outFile)).named(outFile.toString()).isFalse();
            }
            outs.remove(file.getFileName().toString());
        }
        Truth.assertThat(outs).isEmpty();
    }

    private CustomClassTransform getTransform(boolean addDependency) {
        return new CustomClassTransform(transformJar.getPath(), addDependency);
    }

    private static class CustomOutputProvider implements TransformOutputProvider {

        private static class Output {
            @NonNull public final String name;
            @NonNull public final Set<QualifiedContent.ContentType> types;
            @NonNull public final Set<? super QualifiedContent.Scope> scopes;
            @NonNull public final Format format;

            Output(
                    @NonNull String name,
                    @NonNull Set<QualifiedContent.ContentType> types,
                    @NonNull Set<? super QualifiedContent.Scope> scopes,
                    @NonNull Format format) {
                this.name = name;
                this.types = ImmutableSet.copyOf(types);
                this.scopes = ImmutableSet.copyOf(scopes);
                this.format = format;
            }
        }

        private final File out;
        private final List<Output> outputs = Lists.newArrayList();

        private CustomOutputProvider(File out) {
            this.out = out;
        }

        public List<Output> getOutputs() {
            return outputs;
        }

        @Override
        public void deleteAll() throws IOException {}

        @NonNull
        @Override
        public File getContentLocation(
                @NonNull String name,
                @NonNull Set<QualifiedContent.ContentType> types,
                @NonNull Set<? super QualifiedContent.Scope> scopes,
                @NonNull Format format) {
            outputs.add(new Output(name, types, scopes, format));
            return new File(out, name);
        }
    }

    private static CustomOutputProvider createTransformOutput(File out) {
        return new CustomOutputProvider(out);
    }

    private static List<TransformInput> createTransformInputs(
            @NonNull File folder,
            @Nullable Map<File, Status> changes,
            @NonNull Map<File, Status> jars) {
        ImmutableList.Builder<TransformInput> builder = ImmutableList.builder();
        for (Map.Entry<File, Status> entry : jars.entrySet()) {
            builder.add(
                    TransformTestHelper.singleJarBuilder(entry.getKey())
                            .setStatus(entry.getValue())
                            .build());
        }
        builder.add(TransformTestHelper.directoryBuilder(folder).putChangedFiles(changes).build());
        return builder.build();
    }

    @Test
    public void transformNonIncrementalDirectory() throws Exception {
        File in = temporaryFolder.newFolder("in");
        File out = temporaryFolder.newFolder("out");
        File dir = new File(in, "dir");
        File jar1 = new File(in, "jar1.jar");
        File jar2 = new File(in, "jar2.jar");

        addFakeFile(dir, "a.class", "A");
        addFakeFile(dir, "b/c.class", "C");
        addFakeFile(dir, "b/d.class", "D");
        addFakeFile(dir, "c/d/e.class", "E");
        addFakeFile(jar1, "j.class", "J");
        addFakeFile(jar1, "k.class", "K");
        addFakeFile(jar1, "x.xml", "X");
        addFakeFile(jar2, "l.class", "L");
        addFakeFile(jar2, "m.class", "M");
        addFakeFile(jar2, "x.xml", "X");

        List<TransformInput> transformInput =
                createTransformInputs(
                        dir,
                        ImmutableMap.of(),
                        ImmutableMap.of(
                                jar1, Status.ADDED,
                                jar2, Status.ADDED));
        TransformOutputProvider transformOutput = createTransformOutput(out);
        TransformInvocation invocation =
                new TransformInvocationBuilder(context)
                        .addInputs(transformInput)
                        .addOutputProvider(transformOutput)
                        .build();
        getTransform(false).transform(invocation);

        assertValidTransform(in.toPath(), out.toPath());
    }

    @Test
    public void transformIncrementalDirectory() throws Exception {
        File in = temporaryFolder.newFolder("in");
        File out = temporaryFolder.newFolder("out");
        File dir = new File(in, "dir");

        File notchanged = addFakeFile(dir, "a.class", "A");
        File added = addFakeFile(dir, "b/c.class", "C");
        File removed = new File(dir, "d.class");
        File changed = addFakeFile(dir, "e.class", "E");

        File notChangedJar = new File(in, "jar0.jar");
        addFakeFile(notChangedJar, "j0.class", "J0");
        addFakeFile(notChangedJar, "k0.class", "K0");

        File addedJar = new File(in, "jar1.jar");
        addFakeFile(addedJar, "j1.class", "J1");
        addFakeFile(addedJar, "k1.class", "K1");

        File changedJar = new File(in, "jar2.jar");
        addFakeFile(changedJar, "j2.class", "J2");
        addFakeFile(changedJar, "k2.class", "K2");

        // Set up the expected output initial state
        addFakeFile(out, "dir/a.class", "A*");
        addFakeFile(out, "dir/d.class", "D*");
        addFakeFile(out, "dir/e.class", "OLD*");

        File notChangedJarOut = new File(out, "jar0.jar");
        addFakeFile(notChangedJarOut, "j0.class", "J0*");
        addFakeFile(notChangedJarOut, "k0.class", "K0*");

        File changedJarOut = new File(out, "jar2.jar");
        addFakeFile(changedJarOut, "j2.class", "J2OLD*");
        addFakeFile(changedJarOut, "k2.class", "K2OLD*");

        File removedJar = new File(out, "jar3.jar");
        addFakeFile(removedJar, "j3.class", "GONE*");
        addFakeFile(removedJar, "k3.class", "GONE*");

        TransformOutputProvider transformOutput = createTransformOutput(out);
        ImmutableMap<File, Status> files =
                ImmutableMap.of(
                        notchanged, Status.NOTCHANGED,
                        added, Status.ADDED,
                        removed, Status.REMOVED,
                        changed, Status.CHANGED);
        ImmutableMap<File, Status> jars =
                ImmutableMap.of(
                        addedJar, Status.ADDED,
                        notChangedJar, Status.NOTCHANGED,
                        removedJar, Status.REMOVED,
                        changedJar, Status.CHANGED);

        List<TransformInput> transformInput = createTransformInputs(dir, files, jars);
        TransformInvocation invocation =
                new TransformInvocationBuilder(context)
                        .addInputs(transformInput)
                        .addOutputProvider(transformOutput)
                        .setIncrementalMode(true)
                        .build();
        getTransform(false).transform(invocation);

        assertValidTransform(in.toPath(), out.toPath());
    }

    @Test
    public void checkTransformPropertiesBasedOnAddDependencies() {
        Truth.assertThat(getTransform(false).getOutputTypes())
                .named("getOutputTypes on addDependency == false")
                .containsExactly(CLASSES);
        Truth.assertThat(getTransform(true).getOutputTypes())
                .named("getOutputTypes on addDependency == true")
                .containsExactly(CLASSES, NATIVE_LIBS);
    }

    @Test
    public void checkAddDependencyTrue() throws Exception {
        File in = temporaryFolder.newFolder("in");
        File out = temporaryFolder.newFolder("out");
        File dir = new File(in, "dir");
        File jar1 = new File(in, "jar1.jar");
        File jar2 = new File(in, "jar2.jar");

        addFakeFile(dir, "a.class", "A");
        addFakeFile(jar1, "x.xml", "X");
        addFakeFile(jar2, "l.class", "L");

        List<TransformInput> transformInput =
                createTransformInputs(
                        dir,
                        ImmutableMap.of(),
                        ImmutableMap.of(
                                jar1, Status.ADDED,
                                jar2, Status.ADDED));
        CustomOutputProvider transformOutput = createTransformOutput(out);
        TransformInvocation invocation =
                new TransformInvocationBuilder(context)
                        .addInputs(transformInput)
                        .addOutputProvider(transformOutput)
                        .build();
        getTransform(true).transform(invocation);

        List<String> outputNames =
                transformOutput
                        .getOutputs()
                        .stream()
                        .map(output -> output.name)
                        .collect(Collectors.toList());

        Truth.assertThat(outputNames)
                .containsExactly("dir", "jar1.jar", "jar2.jar", FAKE_DEPENDENCIES);
        Optional<CustomOutputProvider.Output> depOutput =
                transformOutput
                        .getOutputs()
                        .stream()
                        .filter(output -> FAKE_DEPENDENCIES.equals(output.name))
                        .findAny();
        Truth.assertThat(depOutput.isPresent()).isTrue();
        Truth.assertThat(depOutput.get().types).containsExactly(CLASSES, NATIVE_LIBS);
    }

    @Test
    public void checkAddDependencyFalse() throws Exception {
        File in = temporaryFolder.newFolder("in");
        File out = temporaryFolder.newFolder("out");
        File dir = new File(in, "dir");
        File jar1 = new File(in, "jar1.jar");
        File jar2 = new File(in, "jar2.jar");

        addFakeFile(dir, "a.class", "A");
        addFakeFile(jar1, "x.xml", "X");
        addFakeFile(jar2, "l.class", "L");

        List<TransformInput> transformInput =
                createTransformInputs(
                        dir,
                        ImmutableMap.of(),
                        ImmutableMap.of(
                                jar1, Status.ADDED,
                                jar2, Status.ADDED));
        CustomOutputProvider transformOutput = createTransformOutput(out);
        TransformInvocation invocation =
                new TransformInvocationBuilder(context)
                        .addInputs(transformInput)
                        .addOutputProvider(transformOutput)
                        .build();
        getTransform(false).transform(invocation);

        List<String> outputNames =
                transformOutput
                        .getOutputs()
                        .stream()
                        .map(output -> output.name)
                        .collect(Collectors.toList());

        Truth.assertThat(outputNames).containsExactly("dir", "jar1.jar", "jar2.jar");
    }
}
