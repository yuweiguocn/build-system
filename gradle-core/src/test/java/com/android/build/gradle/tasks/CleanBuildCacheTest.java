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

package com.android.build.gradle.tasks;

import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.builder.utils.FileCache;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit test for {@link CleanBuildCache}. */
public class CleanBuildCacheTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    @Test
    public void test() throws IOException {
        File projectDir = testDir.newFolder();
        File buildCacheDir = testDir.newFolder();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        FileUtils.mkdirs(new File(buildCacheDir, "some_cache_entry"));

        CleanBuildCache task = project.getTasks().create("cleanBuildCache", CleanBuildCache.class);
        try {
            task.clean();
            fail("expected NullPointerException");
        } catch (NullPointerException exception) {
            assertEquals("buildCache must not be null", exception.getMessage());
        }

        task.setBuildCache(FileCache.getInstanceWithMultiProcessLocking(buildCacheDir));
        task.clean();
        assertThat(buildCacheDir).doesNotExist();

        // Clean one more time to see if any exception occurs when buildCacheDir does not exist
        task.clean();
        assertThat(buildCacheDir).doesNotExist();
    }
}
