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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.FileStatus;
import com.android.utils.Pair;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Tests {@link IncrementalFileMerger}. This relies on {@link IncrementalFileMergerTestInput}
 * and {@link IncrementalFileMergerTestOutput} to mock inputs and outputs.
 */
public class IncrementalFileMergerTests {

    private Random random = new Random();

    @Test
    public void fullMergeOfOneInput() throws Exception {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file", FileStatus.NEW);
        i0.add("dir/level_1_file", FileStatus.NEW);
        i0.add("empty/dir/level_2_file", FileStatus.NEW);


        IncrementalFileMergerTestOutput out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState newState =
                IncrementalFileMerger.merge(
                        ImmutableList.of(i0),
                        out,
                        new IncrementalFileMergerState());

        assertEquals(0, out.removed.size());
        assertEquals(0, out.updated.size());
        assertEquals(3, out.created.size());
        assertNotNull(out.created.get("root_file"));
        assertEquals(1, out.created.get("root_file").size());
        assertSame(i0, out.created.get("root_file").get(0));
        assertNotNull(out.created.get("dir/level_1_file"));
        assertEquals(1, out.created.get("dir/level_1_file").size());
        assertSame(i0, out.created.get("dir/level_1_file").get(0));
        assertNotNull(out.created.get("empty/dir/level_2_file"));
        assertEquals(1, out.created.get("empty/dir/level_2_file").size());
        assertSame(i0, out.created.get("empty/dir/level_2_file").get(0));

        assertEquals(ImmutableList.of("i0"), newState.getInputNames());
        assertEquals(
                ImmutableSet.of("root_file", "dir/level_1_file", "empty/dir/level_2_file"),
                newState.filesOf("i0"));
        assertEquals(ImmutableList.of("i0"), newState.inputsFor("root_file"));
        assertEquals(ImmutableList.of("i0"), newState.inputsFor("dir/level_1_file"));
        assertEquals(ImmutableList.of("i0"), newState.inputsFor("empty/dir/level_2_file"));
    }

    @Test
    public void fullMergeOfTwoInputs() {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file", FileStatus.NEW);
        i0.add("dir/level_1_file", FileStatus.NEW);

        IncrementalFileMergerTestInput i1 = new IncrementalFileMergerTestInput("i1");
        i1.add("dir/level_1_file", FileStatus.NEW);
        i1.add("empty/dir/level_2_file", FileStatus.NEW);


        IncrementalFileMergerTestOutput out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState newState =
                IncrementalFileMerger.merge(
                        ImmutableList.of(i0, i1),
                        out,
                        new IncrementalFileMergerState());

        assertEquals(0, out.removed.size());
        assertEquals(0, out.updated.size());
        assertEquals(3, out.created.size());
        assertNotNull(out.created.get("root_file"));
        assertEquals(1, out.created.get("root_file").size());
        assertSame(i0, out.created.get("root_file").get(0));
        assertNotNull(out.created.get("dir/level_1_file"));
        assertEquals(2, out.created.get("dir/level_1_file").size());
        assertSame(i0, out.created.get("dir/level_1_file").get(0));
        assertSame(i1, out.created.get("dir/level_1_file").get(1));
        assertNotNull(out.created.get("empty/dir/level_2_file"));
        assertEquals(1, out.created.get("empty/dir/level_2_file").size());
        assertSame(i1, out.created.get("empty/dir/level_2_file").get(0));

        assertEquals(ImmutableList.of("i0", "i1"), newState.getInputNames());
        assertEquals(ImmutableSet.of("root_file", "dir/level_1_file"), newState.filesOf("i0"));
        assertEquals(
                ImmutableSet.of("dir/level_1_file", "empty/dir/level_2_file"),
                newState.filesOf("i1"));
        assertEquals(ImmutableList.of("i0"), newState.inputsFor("root_file"));
        assertEquals(ImmutableList.of("i0", "i1"), newState.inputsFor("dir/level_1_file"));
        assertEquals(ImmutableList.of("i1"), newState.inputsFor("empty/dir/level_2_file"));
    }

    @Test
    public void incrementalMergeOneInputAddFile() {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file", FileStatus.NEW);

        IncrementalFileMergerTestOutput out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState afterFullState =
                IncrementalFileMerger.merge(
                        ImmutableList.of(i0),
                        out,
                        new IncrementalFileMergerState());

        i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file");
        i0.add("foo", FileStatus.NEW);
        out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState afterIncState =
                IncrementalFileMerger.merge(ImmutableList.of(i0), out, afterFullState);

        assertEquals(0, out.removed.size());
        assertEquals(0, out.updated.size());
        assertEquals(1, out.created.size());
        assertNotNull(out.created.get("foo"));
        assertEquals(1, out.created.get("foo").size());
        assertEquals(i0, out.created.get("foo").get(0));

        assertEquals(ImmutableList.of("i0"), afterIncState.getInputNames());
        assertEquals(ImmutableSet.of("root_file", "foo"), afterIncState.filesOf("i0"));
        assertEquals(ImmutableList.of("i0"), afterIncState.inputsFor("root_file"));
        assertEquals(ImmutableList.of("i0"), afterIncState.inputsFor("foo"));
    }

    @Test
    public void incrementalMergeOneInputRemoveFile() {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file", FileStatus.NEW);
        i0.add("foo", FileStatus.NEW);

        IncrementalFileMergerTestOutput out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState afterFullState =
                IncrementalFileMerger.merge(
                        ImmutableList.of(i0),
                        out,
                        new IncrementalFileMergerState());

        i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file");
        i0.add("foo", FileStatus.REMOVED);
        out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState afterIncState =
                IncrementalFileMerger.merge(ImmutableList.of(i0), out, afterFullState);

        assertEquals(1, out.removed.size());
        assertEquals(0, out.updated.size());
        assertEquals(0, out.created.size());
        assertEquals(ImmutableSet.of("foo"), out.removed);

        assertEquals(ImmutableList.of("i0"), afterIncState.getInputNames());
        assertEquals(ImmutableSet.of("root_file"), afterIncState.filesOf("i0"));
        assertEquals(ImmutableList.of("i0"), afterIncState.inputsFor("root_file"));
        assertEquals(ImmutableList.of(), afterIncState.inputsFor("foo"));
    }

    @Test
    public void incrementalMergeOneInputUpdateFile() {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file", FileStatus.NEW);
        i0.add("foo", FileStatus.NEW);

        IncrementalFileMergerTestOutput out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState afterFullState =
                IncrementalFileMerger.merge(
                        ImmutableList.of(i0),
                        out,
                        new IncrementalFileMergerState());

        i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("root_file");
        i0.add("foo", FileStatus.CHANGED);
        out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState afterIncState =
                IncrementalFileMerger.merge(ImmutableList.of(i0), out, afterFullState);

        assertEquals(0, out.removed.size());
        assertEquals(1, out.updated.size());
        assertEquals(0, out.created.size());
        assertEquals(Pair.of(ImmutableList.of("i0"), ImmutableList.of(i0)), out.updated.get("foo"));

        assertEquals(ImmutableList.of("i0"), afterIncState.getInputNames());
        assertEquals(ImmutableSet.of("root_file", "foo"), afterIncState.filesOf("i0"));
        assertEquals(ImmutableList.of("i0"), afterIncState.inputsFor("root_file"));
        assertEquals(ImmutableList.of("i0"), afterIncState.inputsFor("foo"));
    }

    @Test
    public void addAndRemoveInputContributingToExistingFile() {
        IncrementalFileMergerTestInput i0 = new IncrementalFileMergerTestInput("i0");
        i0.add("untouched_file", FileStatus.NEW);
        i0.add("touched_file_after_i1", FileStatus.NEW);

        IncrementalFileMergerTestOutput out = new IncrementalFileMergerTestOutput();

        IncrementalFileMergerState afterInitialState =
                IncrementalFileMerger.merge(
                        ImmutableList.of(i0), out, new IncrementalFileMergerState());

        // After merging, we should get both files created in the output.
        assertEquals(0, out.removed.size());
        assertEquals(0, out.updated.size());
        assertEquals(2, out.created.size());
        assertEquals(ImmutableList.of(i0), out.created.get("untouched_file"));
        assertEquals(ImmutableList.of(i0), out.created.get("touched_file_after_i1"));
        out.created.clear();

        i0 = new IncrementalFileMergerTestInput("i0");
        IncrementalFileMergerTestInput i1 = new IncrementalFileMergerTestInput("i1");
        i1.add("touched_file_after_i1", FileStatus.NEW);

        IncrementalFileMergerState afterAdd1State =
                IncrementalFileMerger.merge(ImmutableList.of(i0, i1), out, afterInitialState);

        // After adding i1, we should get an update on the touched_file_after_i1.
        assertEquals(0, out.removed.size());
        assertEquals(1, out.updated.size());
        assertEquals(0, out.created.size());
        assertEquals(
                Pair.of(ImmutableList.of("i0"), ImmutableList.of(i0, i1)),
                out.updated.get("touched_file_after_i1"));
        out.updated.clear();

        IncrementalFileMergerState afterRemovingI1State =
                IncrementalFileMerger.merge(ImmutableList.of(i0), out, afterAdd1State);

        // After removing i1, we should get an update on the touched_file_after_i1.
        assertEquals(0, out.removed.size());
        assertEquals(1, out.updated.size());
        assertEquals(0, out.created.size());
        assertEquals(
                Pair.of(ImmutableList.of("i0", "i1"), ImmutableList.of(i0)),
                out.updated.get("touched_file_after_i1"));
    }

    private void randomTest(
            int inputCount,
            int initialFiles,
            @NonNull Supplier<String> nameGenerator,
            int cycles,
            @NonNull Supplier<Integer> changesPerCycleSupplier,
            @NonNull Supplier<FileStatus> changeTypeSupplier,
            double reorderProbability) {

        /*
         * inputFiles contains the simulated inputs. Each entry in the list corresponds to one
         * input and each set contains all files that input has.
         */
        List<Set<String>> inputFiles = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            inputFiles.add(new HashSet<>());
        }

        /*
         * output contains the expected outputs. It is mapped by the file name and contains the
         * set of all input names that contributed to that output. It does not contain the
         * order of the input sets, only which input sets contributed.
         */
        Map<String, Set<String>> output = new HashMap<>();

        /*
         * Each incremental merge operation generates an output. This list keeps all outputs that
         * were generated in each cycle.
         */
        List<IncrementalFileMergerTestOutput> outs = new ArrayList<>();

        /*
         * Saved state. We start with an empty state.
         */
        IncrementalFileMergerState state = new IncrementalFileMergerState();

        /*
         * Output of the merge. This is what the merge has generated. It is updated from the
         * IncrementalFileMergerTestOutputs.
         */
        Map<String, List<String>> generated = new HashMap<>();

        /*
         * Contains the order of the input sets. Each entry is the index of the input set that
         * should be fed to the merger.
         */
        List<Integer> inputSetOrder = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            inputSetOrder.add(i);
        }


        for (int i = 0; i < cycles; i++) {
            if (i > 0 && random.nextDouble() < reorderProbability) {

                /*
                 * Reorder inputs.
                 */
                Collections.shuffle(inputSetOrder);
            }

            /*
             * Start by creating new input sets and add all files they already have.
             */
            List<IncrementalFileMergerTestInput> inputs = new ArrayList<>();
            for (int j = 0; j < inputCount; j++) {
                IncrementalFileMergerTestInput input = new IncrementalFileMergerTestInput("i" + j);
                inputs.add(input);
                for (String f : inputFiles.get(j)) {
                    input.add(f);
                }
            }

            /*
             * Make changes to the original files (marking them on the inputs) and update the
             * outputs with the expected results.
             */
            if (i == 0) {
                /*
                 * Initial synchronization.
                 */
                for (int j = 0; j < inputs.size(); j++) {
                    for (int k = 0; k < initialFiles; k++) {
                        String newFile = newFor(inputFiles.get(j), nameGenerator);
                        inputFiles.get(j).add(newFile);
                        inputs.get(j).add(newFile, FileStatus.NEW);
                        outFor(output, newFile).add("i" + j);
                    }
                }
            } else {
                /*
                 * Incremental synchronization. Make random changes.
                 */
                int changeCount = changesPerCycleSupplier.get();

                Map<Integer, Set<String>> changedFiles = new HashMap<>();
                for (int j = 0; j < inputCount; j++) {
                    changedFiles.put(j, new HashSet<>());
                }

                for (int j = 0; j < changeCount; j++) {
                    int inputIdx = random.nextInt(inputCount);
                    FileStatus changeType = changeTypeSupplier.get();

                    switch (changeType) {
                        case NEW:
                            Set<String> namesNotAllowed = new HashSet<>(inputFiles.get(inputIdx));
                            namesNotAllowed.addAll(changedFiles.get(inputIdx));
                            String newFile = newFor(namesNotAllowed, nameGenerator);

                            assertTrue(inputFiles.get(inputIdx).add(newFile));
                            inputs.get(inputIdx).add(newFile, FileStatus.NEW);
                            outFor(output, newFile).add("i" + inputIdx);
                            changedFiles.get(inputIdx).add(newFile);
                            break;
                        case CHANGED:
                        case REMOVED:
                            Set<String> namesAllowed = new HashSet<>(inputFiles.get(inputIdx));
                            namesAllowed.removeAll(changedFiles.get(inputIdx));

                            if (namesAllowed.isEmpty()) {
                                break;
                            }

                            String updatedOrRemoved = pick(namesAllowed);
                            changedFiles.get(inputIdx).add(updatedOrRemoved);

                            if (changeType == FileStatus.CHANGED) {
                                inputs.get(inputIdx).add(updatedOrRemoved, FileStatus.CHANGED);
                            } else {
                                inputs.get(inputIdx).add(updatedOrRemoved, FileStatus.REMOVED);
                                inputFiles.get(inputIdx).remove(updatedOrRemoved);
                                Set<String> remaining = output.get(updatedOrRemoved);
                                assertTrue(remaining.remove(inputs.get(inputIdx).getName()));
                                if (remaining.isEmpty()) {
                                    output.remove(updatedOrRemoved);
                                }
                            }

                            break;
                        default:
                            throw new AssertionError();

                    }
                }
            }

            /*
             * Actually do the merge.
             */
            List<IncrementalFileMergerInput> inputsToMerge =
                    inputSetOrder.stream()
                            .map(inputs::get)
                            .collect(Collectors.toList());

            IncrementalFileMergerTestOutput out = new IncrementalFileMergerTestOutput();
            IncrementalFileMergerState newState =
                    IncrementalFileMerger.merge(ImmutableList.copyOf(inputsToMerge), out, state);
            outs.add(out);

            /*
             * Merge has completed. Update the generated outputs.
             */
            for (String r : out.removed) {
                assertTrue(generated.containsKey(r));
                generated.remove(r);
            }

            for (String u : out.updated.keySet()) {
                Pair<ImmutableList<String>, ImmutableList<IncrementalFileMergerInput>> p;
                p = out.updated.get(u);

                List<String> names =
                        p.getSecond().stream()
                                .map(IncrementalFileMergerInput::getName)
                                .collect(Collectors.toList());
                assertTrue(generated.containsKey(u));
                assertEquals(generated.get(u), p.getFirst());
                generated.put(u, names);
            }

            for (String c : out.created.keySet()) {
                List<String> names =
                        out.created.get(c).stream()
                                .map(IncrementalFileMergerInput::getName)
                                .collect(Collectors.toList());
                assertFalse(generated.containsKey(c));
                generated.put(c, names);
            }

            /*
             * Check that all files expected in the output have been generated.
             */
            for (String o : output.keySet()) {
                List<String> expectedList = new ArrayList<>(output.get(o));
                Collections.sort(expectedList, (n0, n1) -> {
                    int i0 = Integer.parseInt(n0.substring(1));
                    int i1 = Integer.parseInt(n1.substring(1));
                    int idx0 = inputSetOrder.indexOf(i0);
                    int idx1 = inputSetOrder.indexOf(i1);
                    return idx0 - idx1;
                });

                List<String> generatedList = generated.get(o);
                assertEquals(expectedList, generatedList);
            }

            /*
             * Check that there are no additional generated files.
             */
            assertTrue(output.keySet().containsAll(generated.keySet()));

            state = newState;
        }
    }

    @NonNull
    private String newFor(@NonNull Set<String> files, @NonNull Supplier<String> nameGenerator) {
        int it = 0;
        do {
            String n = nameGenerator.get();
            if (!files.contains(n)) {
                return n;
            }

            it++;
        } while (it < 100);

        throw new AssertionError();
    }

    @NonNull
    private Set<String> outFor(@NonNull Map<String, Set<String>> output, @NonNull String file) {
        Set<String> l = output.get(file);
        if (l == null) {
            l = new HashSet<>();
            output.put(file, l);
        }

        return l;
    }

    @NonNull
    private String newName(int chars) {
        int limit = (int) Math.pow(10, chars);
        int i = random.nextInt(limit);

        return "f" + Strings.padStart(Integer.toString(i), chars, '0');
    }

    @NonNull
    private <E> E pick(@NonNull Set<E> set) {
        List<E> l = new ArrayList<E>(set);
        int idx = random.nextInt(l.size());
        return l.get(idx);
    }

    @NonNull
    private FileStatus randomStatus() {
        FileStatus[] all = FileStatus.values();
        return all[random.nextInt(all.length)];
    }

    /*
     * This is a random test. If flaky, then there is a bug in the code :)
     */
    @Test
    public void randomSingleInputFullMergeTests() {
        for (int i = 0; i < 100; i++) {
            randomTest(1, 50, () -> newName(2), 1, () -> 0, () -> null, 0);
        }
    }

    /*
     * This is a random test. If flaky, then there is a bug in the code :)
     */
    @Test
    public void randomMultipleInputFullMergeTests() {
        for (int i = 0; i < 100; i++) {
            randomTest(i / 10 + 1, 50, () -> newName(3), 1, () -> 0, () -> null, 0);
        }
    }

    /*
     * This is a random test. If flaky, then there is a bug in the code :)
     */
    @Test
    public void randomSingleInputIncrementalMergeTests() {
        for (int i = 0; i < 100; i++) {
            randomTest(1, 50, () -> newName(3), 100, () -> 10, this::randomStatus, 0);
        }
    }

    /*
     * This is a random test. If flaky, then there is a bug in the code :)
     */
    @Test
    public void randomMultipleInputIncrementalMergeTests() {
        for (int i = 0; i < 100; i++) {
            randomTest(
                    i / 10 + 1,
                    50,
                    () -> newName(3),
                    100,
                    () -> random.nextInt(50),
                    this::randomStatus,
                    0);
        }
    }

    /*
     * This is a random test. If flaky, then there is a bug in the code :)
     */
    @Test
    public void randomMultipleInputIncrementalMergeTestsWithReorder() {
        for (int i = 0; i < 100; i++) {
            randomTest(
                    i / 10 + 2,
                    50,
                    () -> newName(3),
                    100,
                    () -> random.nextInt(50),
                    this::randomStatus,
                    0.1);
        }
    }
}
