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

package com.android.builder.merge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.tools.build.apkzlib.utils.IOExceptionFunction;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class IncrementalFileMergerOutputsTest {

    private List<List<byte[]>> algFrom = new ArrayList<>();

    private StreamMergeAlgorithm alg =
            (path, from, closer) -> {
                algFrom.add(
                        from.stream()
                                .map(IOExceptionFunction.asFunction(ByteStreams::toByteArray))
                                .collect(Collectors.toList()));
                InputStream mergedStream = new ByteArrayInputStream(new byte[] {4, 5, 6, 3});
                from.forEach(closer::register);
                closer.register(mergedStream);
                return mergedStream;
            };

    private List<Void> opens = new ArrayList<>();

    private List<Void> closes = new ArrayList<>();

    private List<String> removes = new ArrayList<>();

    private List<String> creates = new ArrayList<>();

    private List<byte[]> createsData = new ArrayList<>();

    private List<String> replaces = new ArrayList<>();

    private List<byte[]> replacesData = new ArrayList<>();

    private MergeOutputWriter writer = new MergeOutputWriter() {
        @Override
        public void remove(@NonNull String path) {
            assertTrue(opens.size() == closes.size() + 1);
            removes.add(path);
        }

        @Override
        public void create(@NonNull String path, @NonNull InputStream data) {
            assertTrue(opens.size() == closes.size() + 1);
            creates.add(path);
            try {
                createsData.add(ByteStreams.toByteArray(data));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void replace(@NonNull String path, @NonNull InputStream data) {
            assertTrue(opens.size() == closes.size() + 1);
            replaces.add(path);
            try {
                replacesData.add(ByteStreams.toByteArray(data));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void open() {
            assertTrue(opens.size() == closes.size());
            opens.add(null);
        }

        @Override
        public void close() {
            assertTrue(opens.size() == closes.size() + 1);
            closes.add(null);
        }
    };

    private IncrementalFileMergerOutput out =
            IncrementalFileMergerOutputs.fromAlgorithmAndWriter(alg, writer);

    @Test
    public void algorithmWriterRemoveFile() throws Exception {
        out.open();
        out.remove("foo");
        out.close();

        assertEquals(0, creates.size());
        assertEquals(0, replaces.size());
        assertEquals(1, removes.size());
        assertEquals("foo", removes.get(0));
        assertEquals(0, algFrom.size());
    }

    @Test
    public void algorithmWriterCreateFile() throws Exception {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("in0");
        i0.add("/blah");
        i0.setData("/blah", new byte[] { 9, 8 });

        IncrementalFileMergerTestInput i1 = new IncrementalFileMergerTestInput("in1");
        i1.add("/blah");
        i1.setData("/blah", new byte[] { 8, 9 });

        i0.open();
        i1.open();
        out.open();
        out.create("/blah", ImmutableList.of(i0, i1));
        out.close();
        i1.close();
        i0.close();

        assertEquals(0, removes.size());
        assertEquals(0, replaces.size());
        assertEquals(1, creates.size());
        assertArrayEquals(new byte[] { 4, 5, 6, 3 }, createsData.get(0));
        assertEquals(1, algFrom.size());
        assertEquals(2, algFrom.get(0).size());
        assertArrayEquals(new byte[] { 9, 8 }, algFrom.get(0).get(0));
        assertArrayEquals(new byte[] { 8, 9 }, algFrom.get(0).get(1));
    }

    @Test
    public void algorithmWriterUpdateFile() throws Exception {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("in0");
        i0.add("/blah");
        i0.setData("/blah", new byte[] { 9, 8 });

        IncrementalFileMergerTestInput i1 = new IncrementalFileMergerTestInput("in1");
        i1.add("/blah");
        i1.setData("/blah", new byte[] { 8, 9 });

        i0.open();
        i1.open();
        out.open();
        out.update("/blah", ImmutableList.of("foobar"), ImmutableList.of(i0, i1));
        out.close();
        i1.close();
        i0.close();

        assertEquals(0, removes.size());
        assertEquals(0, creates.size());
        assertEquals(1, replaces.size());
        assertArrayEquals(new byte[] { 4, 5, 6, 3 }, replacesData.get(0));
        assertEquals(1, algFrom.size());
        assertEquals(2, algFrom.get(0).size());
        assertArrayEquals(new byte[] { 9, 8 }, algFrom.get(0).get(0));
        assertArrayEquals(new byte[] { 8, 9 }, algFrom.get(0).get(1));
    }
}
