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

package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SeparateTestModuleConnectedTest {
    @Rule public Adb adb = new Adb();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("separateTestModule").create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  defaultConfig {\n"
                        + "    testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "  }\n"
                        + "  dependencies {\n"
                        + "    implementation ('com.android.support.test:runner:"
                        + TestVersions.TEST_SUPPORT_LIB_VERSION
                        + "', {\n"
                        + "      exclude group: 'com.android.support', module: 'support-annotations'\n"
                        + "    })\n"
                        + "  }\n"
                        + "}\n");
    }

    @Test
    @Category(DeviceTests.class)
    public void checkWillRunWithoutInstrumentationInManifest() throws Exception {
        project.execute(":test:deviceCheck");
    }

    @Test
    @Category(DeviceTests.class)
    public void checkConnectedCheckCompletesNormally() throws Exception {
        adb.exclusiveAccess();
        project.execute(":test:connectedCheck");
    }
}
