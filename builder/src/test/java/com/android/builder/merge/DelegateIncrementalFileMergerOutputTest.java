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

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class DelegateIncrementalFileMergerOutputTest {
    private IncrementalFileMergerOutput mockOutput;

    private DelegateIncrementalFileMergerOutput delegate;

    @Before
    public final void before() {
        mockOutput = Mockito.mock(IncrementalFileMergerOutput.class);
        delegate = new DelegateIncrementalFileMergerOutput(mockOutput);
    }

    @After
    public final void after() {
        Mockito.verifyNoMoreInteractions(mockOutput);
    }

    @Test
    public void open() {
        delegate.open();
        Mockito.verify(mockOutput).open();
    }

    @Test
    public void close() {
        delegate.close();
        Mockito.verify(mockOutput).close();
    }

    @Test
    public void remove() {
        delegate.remove("foo");
        Mockito.verify(mockOutput).remove(Matchers.eq("foo"));
    }

    @Test
    public void create() {
        ImmutableList<IncrementalFileMergerInput> inputs = ImmutableList.of();

        delegate.create("foo", inputs);
        Mockito.verify(mockOutput).create(Matchers.eq("foo"), Matchers.same(inputs));
    }

    @Test
    public void update() {
        ImmutableList<String> prevNames = ImmutableList.of();
        ImmutableList<IncrementalFileMergerInput> inputs = ImmutableList.of();

        delegate.update("foo", prevNames, inputs);
        Mockito.verify(mockOutput).update(
                Matchers.eq("foo"),
                Matchers.same(prevNames),
                Matchers.same(inputs));
    }
}
