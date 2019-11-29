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

package com.android.builder.internal;



import static com.android.testutils.truth.PathSubject.assertThat;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestManifestGeneratorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Tests the common case, making sure the template resource is packaged correctly.
     */
    @Test
    public void generate() throws Exception {
        File destination = temporaryFolder.newFile();
        TestManifestGenerator generator =
                new TestManifestGenerator(
                        destination,
                        "com.example.test",
                        "19",
                        "24",
                        "com.example",
                        "android.support.test.runner.AndroidJUnitRunner",
                        false,
                        false);

        generator.generate();

        assertThat(destination).isFile();
    }
}
