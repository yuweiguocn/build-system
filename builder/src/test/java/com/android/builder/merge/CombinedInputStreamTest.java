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

package com.android.builder.merge;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Test cases for {@link CombinedInputStream}. */
public class CombinedInputStreamTest {

    private InputStream combinedInputStream;
    private InputStream paddedCombinedInputStream;

    @SuppressWarnings("ZeroLengthArrayAllocation")
    @Before
    public void setUp() {
        combinedInputStream =
                new CombinedInputStream(
                        ImmutableList.of(
                                new ByteArrayInputStream(new byte[] {1, 2}),
                                new ByteArrayInputStream(new byte[] {}),
                                new ByteArrayInputStream(new byte[] {3, 4, '\n'}),
                                new ByteArrayInputStream(new byte[] {5, 6})),
                        false);
        paddedCombinedInputStream =
                new CombinedInputStream(
                        ImmutableList.of(
                                new ByteArrayInputStream(new byte[] {1, 2}),
                                new ByteArrayInputStream(new byte[] {}),
                                new ByteArrayInputStream(new byte[] {3, 4, '\n'}),
                                new ByteArrayInputStream(new byte[] {5, 6})),
                        true);
    }

    @After
    public void tearDown() throws IOException {
        combinedInputStream.close();
        paddedCombinedInputStream.close();
    }

    @Test
    public void testRead() throws IOException {
        assertThat(combinedInputStream.read()).isEqualTo(1);
        assertThat(combinedInputStream.read()).isEqualTo(2);
        assertThat(combinedInputStream.read()).isEqualTo(3);
        assertThat(combinedInputStream.read()).isEqualTo(4);
        assertThat(combinedInputStream.read()).isEqualTo('\n');
        assertThat(combinedInputStream.read()).isEqualTo(5);
        assertThat(combinedInputStream.read()).isEqualTo(6);
        assertThat(combinedInputStream.read()).isEqualTo(-1);
    }

    @Test
    public void testReadByteArray() throws IOException {
        // This test assumes that CombinedInputStream never reads from more than one input stream
        // in a single read operation
        byte[] buffer = new byte[10];

        int byteCount = combinedInputStream.read(buffer);
        int bytesRead = byteCount;
        assertThat(Arrays.copyOfRange(buffer, 0, bytesRead)).isEqualTo(new byte[] {1, 2});

        byteCount = combinedInputStream.read(buffer, bytesRead, buffer.length - bytesRead);
        bytesRead += byteCount;
        assertThat(Arrays.copyOfRange(buffer, 0, bytesRead))
                .isEqualTo(new byte[] {1, 2, 3, 4, '\n'});

        byteCount = combinedInputStream.read(buffer, bytesRead, buffer.length - bytesRead);
        bytesRead += byteCount;
        assertThat(Arrays.copyOfRange(buffer, 0, bytesRead))
                .isEqualTo(new byte[] {1, 2, 3, 4, '\n', 5, 6});

        byteCount = combinedInputStream.read(buffer, bytesRead, buffer.length - bytesRead);
        assertThat(byteCount).isEqualTo(-1);
    }

    @Test
    public void testReadFully() throws IOException {
        assertThat(ByteStreams.toByteArray(combinedInputStream))
                .isEqualTo(new byte[] {1, 2, 3, 4, '\n', 5, 6});
    }

    @Test
    public void testReadWithNewLinePadding() throws IOException {
        assertThat(paddedCombinedInputStream.read()).isEqualTo(1);
        assertThat(paddedCombinedInputStream.read()).isEqualTo(2);
        assertThat(paddedCombinedInputStream.read()).isEqualTo('\n');
        assertThat(paddedCombinedInputStream.read()).isEqualTo(3);
        assertThat(paddedCombinedInputStream.read()).isEqualTo(4);
        assertThat(paddedCombinedInputStream.read()).isEqualTo('\n');
        assertThat(paddedCombinedInputStream.read()).isEqualTo(5);
        assertThat(paddedCombinedInputStream.read()).isEqualTo(6);
        assertThat(paddedCombinedInputStream.read()).isEqualTo(-1);
    }

    @Test
    public void testReadByteArrayWithNewLinePadding() throws IOException {
        // This test assumes that CombinedInputStream never reads from more than one input stream
        // in a single read operation
        byte[] buffer = new byte[10];

        int byteCount = paddedCombinedInputStream.read(buffer);
        int bytesRead = byteCount;
        assertThat(Arrays.copyOfRange(buffer, 0, bytesRead)).isEqualTo(new byte[] {1, 2});

        byteCount = paddedCombinedInputStream.read(buffer, bytesRead, buffer.length - bytesRead);
        bytesRead += byteCount;
        assertThat(Arrays.copyOfRange(buffer, 0, bytesRead)).isEqualTo(new byte[] {1, 2, '\n'});

        byteCount = paddedCombinedInputStream.read(buffer, bytesRead, buffer.length - bytesRead);
        bytesRead += byteCount;
        assertThat(Arrays.copyOfRange(buffer, 0, bytesRead))
                .isEqualTo(new byte[] {1, 2, '\n', 3, 4, '\n'});

        byteCount = paddedCombinedInputStream.read(buffer, bytesRead, buffer.length - bytesRead);
        bytesRead += byteCount;
        assertThat(Arrays.copyOfRange(buffer, 0, bytesRead))
                .isEqualTo(new byte[] {1, 2, '\n', 3, 4, '\n', 5, 6});

        byteCount = paddedCombinedInputStream.read(buffer, bytesRead, buffer.length - bytesRead);
        assertThat(byteCount).isEqualTo(-1);
    }

    @Test
    public void testReadFullyWithNewLinePadding() throws IOException {
        assertThat(ByteStreams.toByteArray(paddedCombinedInputStream))
                .isEqualTo(new byte[] {1, 2, '\n', 3, 4, '\n', 5, 6});
    }
}
