/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.api;

import com.android.testutils.TestUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Updates the stable API file. Simply run inside Intellij to update the stable API file.
 */
public class StableApiUpdater {

    public static void main(String... args) throws IOException {
        Path subProject =
                TestUtils.getWorkspaceFile(
                                "tools/base/build-system/gradle-api/src/test/resources/com/android/build/api")
                        .toPath();
        Path stable = subProject.resolve("stable-api.txt");
        updateFile(stable, StableApiTest.getStableApiElements());
        Path incubating = subProject.resolve("incubating-api.txt");
        updateFile(incubating, StableApiTest.getIncubatingApiElements());
    }

    private static void updateFile(Path file, List<String> content) throws IOException {
        List<String> previous = Files.readAllLines(file);
        Files.write(file, content, StandardCharsets.UTF_8);
        if (!previous.equals(content)) {
            System.out.println();
            System.out.println("Applied diff to " + file);
            System.out.println(
                    TestUtils.getDiff(
                            previous.toArray(new String[0]), content.toArray(new String[0])));
        } else {
            System.out.println("No updates to " + file);
        }
        System.out.println();
    }

}
