/*
 * Copyright (C) 2015 The Android Open Source Project
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
/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.external.gnumake;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class FlowAnalyzerTest {

    @NonNull private final ArrayList<String> compileFlagC = Lists.newArrayList("-c");

    private static void assertFlowAnalysisEquals(
            @NonNull String string, @NonNull FlowAnalysisBuilder expected) {
        ListMultimap<String, List<BuildStepInfo>> io =
                FlowAnalyzer.analyze(string, AbstractOsFileConventions.createForCurrentHost());

        assertThat(io).isEqualTo(expected.map);
    }

    private static class FlowAnalysisBuilder {
        @NonNull final ListMultimap<String, List<BuildStepInfo>> map;
        FlowAnalysisBuilder() {
            this.map = ArrayListMultimap.create();
        }

        @NonNull
        FlowAnalysisBuilder with(String library, @NonNull BuildStepInfosBuilder info) {
            map.put(library, info.infos);
            return this;
        }
    }

    @NonNull
    private static FlowAnalysisBuilder flow() {
        return new FlowAnalysisBuilder();
    }

    private static class BuildStepInfosBuilder {
        @NonNull final List<BuildStepInfo> infos = Lists.newArrayList();

        @NonNull
        BuildStepInfosBuilder with(
                @NonNull String command,
                @NonNull List<String> commandArgs,
                @NonNull String input,
                @NonNull List<String> outputs,
                boolean inputsAreSourceFiles) {
            infos.add(
                    new BuildStepInfo(
                            new CommandLine(command, commandArgs, commandArgs),
                            Lists.newArrayList(input),
                            outputs,
                            inputsAreSourceFiles));
            return this;
        }
    }

    @NonNull
    private static BuildStepInfosBuilder step() {
        return new BuildStepInfosBuilder();
    }

    @Test
    public void disallowedTerminal() throws FileNotFoundException {
        assertFlowAnalysisEquals(
                "g++ -c a.c -o a.o\ng++ a.o -o a.so",
                flow().with(
                                "a.so",
                                step().with(
                                                "g++",
                                                compileFlagC,
                                                "a.c",
                                                Lists.newArrayList("a.o"),
                                                true)));
    }

    @Test
    public void doubleTarget() throws FileNotFoundException {
        assertFlowAnalysisEquals(
                "g++ -c a.c -o x/a.o\n"
                        + "g++ x/a.o -o x/a.so\n"
                        + "g++ -c a.c -o y/a.o\n"
                        + "g++ y/a.o -o y/a.so",
                flow().with(
                                "y/a.so",
                                step().with(
                                                "g++",
                                                compileFlagC,
                                                "a.c",
                                                Lists.newArrayList("y/a.o"),
                                                true))
                        .with(
                                "x/a.so",
                                step().with(
                                                "g++",
                                                compileFlagC,
                                                "a.c",
                                                Lists.newArrayList("x/a.o"),
                                                true)));
    }

    @Test
    public void simple() throws FileNotFoundException {
        assertFlowAnalysisEquals(
                "g++ -c a.c -o a.o\ng++ a.o -o a.so",
                flow().with(
                                "a.so",
                                step().with(
                                                "g++",
                                                compileFlagC,
                                                "a.c",
                                                Lists.newArrayList("a.o"),
                                                true)));
    }
}
