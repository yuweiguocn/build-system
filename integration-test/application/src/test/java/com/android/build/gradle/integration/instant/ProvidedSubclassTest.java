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

package com.android.build.gradle.integration.instant;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.google.common.truth.Expect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Check that the super classes can be found when subclassing things in provided dependencies.
 */
public class ProvidedSubclassTest {

    private static final AndroidTestModule TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    static {
        TEST_APP.addFile(new TestSourceFile("src/main/java/com/example/helloworld",
                "MyByteSink.java",
                "package com.example.helloworld;" +
                "public class MyByteSink extends com.google.common.io.ByteSink {\n" +
                "    public java.io.OutputStream openStream() {\n" +
                "        throw new RuntimeException();\n" +
                "    }\n" +
                "}\n"));
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(TEST_APP)
                    .create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void addProvidedLibrary() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies { provided 'com.google.guava:guava:18.0' }\n");
    }

    @Test
    public void checkV() throws Exception {
        project.execute("clean");
        GradleBuildResult result =
                project.executor()
                        .withInstantRun(
                                new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                        .run("assembleDebug");
        // Asserts build doesn't fail
    }


}
