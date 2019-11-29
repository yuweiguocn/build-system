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

import com.android.annotations.NonNull;
import com.android.builder.dexing.ClassFileEntry;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.DexArchiveBuilderConfig;
import com.android.builder.dexing.DexerTool;
import com.android.dx.command.dexer.DxContext;
import com.android.testutils.apk.Dex;
import com.android.testutils.truth.MoreTruth;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for the dx {@link DexArchiveBuilder} class. */
@RunWith(Parameterized.class)
public class DxDexArchiveBuilderTest {

    DxContext dxContext = new DxContext(System.out, System.err);

    private final int bufferSize;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public DxDexArchiveBuilderTest(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Parameterized.Parameters(name = "bufferSize_{0}")
    public static List<Integer> parameters() {
        return ImmutableList.of(0, 10 * 1024);
    }

    @Test
    public void withBufferTest() throws IOException {
        DexArchiveBuilderConfig config =
                new DexArchiveBuilderConfig(
                        dxContext, true, bufferSize, 21, DexerTool.DX, bufferSize, false);
        Path outputArchive = temporaryFolder.newFolder().toPath();

        DexArchiveBuilder archiveBuilder = DexArchiveBuilder.createDxDexBuilder(config);
        String name = TestClassFileEntry.class.getName().replace('.', '/') + ".class";
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(name)) {
            Stream<ClassFileEntry> stream =
                    ImmutableList.<ClassFileEntry>of(new TestClassFileEntry(name, is)).stream();
            archiveBuilder.convert(stream, outputArchive, true);
        }
        String descriptor = name.substring(0, name.length() - ".class".length());
        Path dexedClass = outputArchive.resolve(descriptor + ".dex");
        MoreTruth.assertThat(new Dex(dexedClass)).containsClass("L" + descriptor + ";");
    }

    private static class TestClassFileEntry implements ClassFileEntry {

        private final InputStream is;
        private final String name;

        private TestClassFileEntry(String name, InputStream is) {
            this.name = name;
            this.is = is;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public long getSize() throws IOException {
            return 0;
        }

        @Override
        public String getRelativePath() {
            return name;
        }

        @NonNull
        @Override
        public ClassFileInput getInput() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            return ByteStreams.toByteArray(is);
        }

        @Override
        public int readAllBytes(byte[] bytes) throws IOException {
            return ByteStreams.read(is, bytes, 0, bytes.length);
        }
    }
}
