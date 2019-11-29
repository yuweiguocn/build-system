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

package com.android.builder.core;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DesugarProcessArgsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testTooManyPathArgs() throws IOException {
        // enough inputs to trigger creation of args file on windows
        int INPUTS = DesugarProcessArgs.MAX_CMD_LENGTH_FOR_WINDOWS / 64;
        Map<String, String> inToOut = new HashMap<>(INPUTS);
        List<String> classpath = new ArrayList<>(INPUTS);
        List<String> bootClasspath = new ArrayList<>(INPUTS);
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < INPUTS; i++) {
            String path = "my_path_number_" + Integer.toString(i);
            inToOut.put(path, path);
            classpath.add(path);
            bootClasspath.add(path);

            expected.add("--input");
            expected.add(path);
            expected.add("--output");
            expected.add(path);
            expected.add("--classpath_entry");
            expected.add(path);
            expected.add("--bootclasspath_entry");
            expected.add(path);
        }
        expected.add("--min_sdk_version");
        expected.add(Integer.toString(10));
        expected.add("--desugar_try_with_resources_if_needed");
        expected.add("--desugar_try_with_resources_omit_runtime_classes");
        expected.add("--legacy_jacoco_fix");
        expected.add("--copy_bridges_from_classpath");

        DesugarProcessArgs args =
                new DesugarProcessArgs(
                        inToOut,
                        classpath,
                        bootClasspath,
                        tmp.getRoot().toPath().toString(),
                        false,
                        10,
                        true);
        String windowsArgs = Iterables.getOnlyElement(args.getArgs(true)).substring(1);
        assertThat(Files.readAllLines(Paths.get(windowsArgs))).containsExactlyElementsIn(expected);

        List<String> nonWinArgs = args.getArgs(false);
        assertThat(nonWinArgs).containsExactlyElementsIn(expected);
    }

    @Test
    public void testFewPathArgsOnWindows() {
        int INPUTS = 10;
        Map<String, String> inToOut = new HashMap<>(INPUTS);
        List<String> classpath = new ArrayList<>(INPUTS);
        List<String> bootClasspath = new ArrayList<>(INPUTS);
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < INPUTS; i++) {
            String path = "my_path_number_" + Integer.toString(i);
            inToOut.put(path, path);
            classpath.add(path);
            bootClasspath.add(path);

            expected.add("--input");
            expected.add(path);
            expected.add("--output");
            expected.add(path);
            expected.add("--classpath_entry");
            expected.add(path);
            expected.add("--bootclasspath_entry");
            expected.add(path);
        }
        expected.add("--min_sdk_version");
        expected.add("--verbose");
        expected.add(Integer.toString(19));
        expected.add("--nodesugar_try_with_resources_if_needed");
        expected.add("--desugar_try_with_resources_omit_runtime_classes");
        expected.add("--copy_bridges_from_classpath");

        DesugarProcessArgs args =
                new DesugarProcessArgs(
                        inToOut,
                        classpath,
                        bootClasspath,
                        tmp.getRoot().toPath().toString(),
                        true,
                        19,
                        false);
        List<String> winArgs = args.getArgs(true);
        assertThat(winArgs).containsExactlyElementsIn(expected);
    }
}
