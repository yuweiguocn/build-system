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
import org.junit.Test;

/** Test cases for {@link IncrementalFileMergerState}. */
public class IncrementalFileMergerStateTest {

    @Test
    public void testChangeStates() {
        IncrementalFileMergerState firstState = new IncrementalFileMergerState();

        // Set up initial state: input1 -> path1, path2; input2 -> path1, path3
        IncrementalFileMergerState.Builder secondStateBuilder =
                new IncrementalFileMergerState.Builder(firstState);
        secondStateBuilder.setInputNames(ImmutableList.of("input1", "input2"));
        secondStateBuilder.set("path1", ImmutableList.of("input1", "input2"));
        secondStateBuilder.set("path2", ImmutableList.of("input1"));
        secondStateBuilder.set("path3", ImmutableList.of("input2"));
        IncrementalFileMergerState secondState = secondStateBuilder.build();

        assertThat(secondState.getInputNames()).containsExactly("input1", "input2");
        assertThat(secondState.filesOf("input1")).containsExactly("path1", "path2");
        assertThat(secondState.filesOf("input2")).containsExactly("path1", "path3");
        assertThat(secondState.inputsFor("path1")).containsExactly("input1", "input2");
        assertThat(secondState.inputsFor("path2")).containsExactly("input1");
        assertThat(secondState.inputsFor("path3")).containsExactly("input2");

        // Change state: input1 -> path1, path2; input2 -> path2, path3
        IncrementalFileMergerState.Builder thirdStateBuilder =
                new IncrementalFileMergerState.Builder(secondState);
        thirdStateBuilder.set("path1", ImmutableList.of("input1"));
        thirdStateBuilder.set("path2", ImmutableList.of("input1", "input2"));
        IncrementalFileMergerState thirdState = thirdStateBuilder.build();

        assertThat(thirdState.getInputNames()).containsExactly("input1", "input2");
        assertThat(thirdState.filesOf("input1")).containsExactly("path1", "path2");
        assertThat(thirdState.filesOf("input2")).containsExactly("path2", "path3");
        assertThat(thirdState.inputsFor("path1")).containsExactly("input1");
        assertThat(thirdState.inputsFor("path2")).containsExactly("input1", "input2");
        assertThat(thirdState.inputsFor("path3")).containsExactly("input2");

        // Change state: Remove input2 (regression test for issuetracker.google.com/66712906)
        IncrementalFileMergerState.Builder fourthStateBuilder =
                new IncrementalFileMergerState.Builder(thirdState);
        fourthStateBuilder.setInputNames(ImmutableList.of("input1"));
        IncrementalFileMergerState fourthState = fourthStateBuilder.build();

        assertThat(fourthState.getInputNames()).containsExactly("input1");
        assertThat(fourthState.filesOf("input1")).containsExactly("path1", "path2");
        assertThat(fourthState.filesOf("input2")).hasSize(0);
        assertThat(fourthState.inputsFor("path1")).containsExactly("input1");
        assertThat(fourthState.inputsFor("path2")).containsExactly("input1");
        assertThat(fourthState.inputsFor("path3")).hasSize(0);
    }
}
