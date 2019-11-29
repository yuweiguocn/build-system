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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import java.io.File;

public class TestProjectPaths {

    private static final String TEST_PROJECT_PATH =
            "tools/base/build-system/integration-test/test-projects";
    private static final String EXTERNAL_PROJECT_PATH = "external";

    public static File getTestProjectDir(@NonNull String name) {
        return TestUtils.getWorkspaceFile(TEST_PROJECT_PATH + "/" + name);
    }

    public static File getTestProjectDir() {
        return TestUtils.getWorkspaceFile(TEST_PROJECT_PATH);
    }

    public static File getExternalProjectDir() {
        return TestUtils.getWorkspaceFile(EXTERNAL_PROJECT_PATH);
    }
}
