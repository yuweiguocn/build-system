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

package com.android.build.gradle.integration.databinding;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test use of Java 8 language in the application module with data binding.
 *
 * <p>regression test for - http://b.android.com/321693
 */
@RunWith(Parameterized.class)
public class DataBindingDesugarAppTest {

    @NonNull private final Boolean enableGradleWorkers;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Parameterized.Parameters(name = "enableGradleWorkers={0}")
    public static Boolean[] getParameters() {
        return new Boolean[] {Boolean.TRUE, Boolean.FALSE};
    }

    public DataBindingDesugarAppTest(@NonNull Boolean enableGradleWorkers) {
        this.enableGradleWorkers = enableGradleWorkers;
    }

    @Test
    public void testDatabinding() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n"
                                + "android.compileOptions.sourceCompatibility 1.8\n"
                                + "android.compileOptions.targetCompatibility 1.8\n"
                                + "android.dataBinding.enabled true\n"
                                + "android.defaultConfig.minSdkVersion %d\n"
                                + "dependencies {\n"
                                + "    compile 'com.android.support:support-v4:%s'\n"
                                + "}",
                        TestVersions.SUPPORT_LIB_MIN_SDK, TestVersions.SUPPORT_LIB_VERSION));

        getProjectExecutor().run("assembleDebug");
    }

    private GradleTaskExecutor getProjectExecutor() {
        return project.executor().with(BooleanOption.ENABLE_GRADLE_WORKERS, enableGradleWorkers);
    }
}
