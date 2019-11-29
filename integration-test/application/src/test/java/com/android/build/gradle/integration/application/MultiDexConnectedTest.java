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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.application.MultiDexTest.MainDexListTool;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class MultiDexConnectedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("multiDex").withHeap("2048M").create();

    @Rule public Adb adb = new Adb();

    @Parameterized.Parameters(name = "mainDexListTool = {0}")
    public static Object[] data() {
        return MainDexListTool.values();
    }

    @Parameterized.Parameter public MainDexListTool tool;

    @Test
    @Ignore("b/78108767")
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executor()
                .run(
                        "assembleIcsDebug",
                        "assembleIcsDebugAndroidTest",
                        "assembleLollipopDebug",
                        "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        project.executor().run("connectedCheck");
    }
}
