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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestModule;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DexLimitTest {

    @Parameterized.Parameters(name="dexInProcess={0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

    private static final AndroidTestModule TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    static {
        StringBuilder classFileBuilder = new StringBuilder();
        for (int i=0; i<65536/2; i++) {
            classFileBuilder.append("    public void m").append(i).append("() {}\n");
        }

        String methods = classFileBuilder.toString();

        String classFileA = "package com.example;\npublic class A {\n" + methods + "\n}";
        String classFileB = "package com.example;\npublic class B {\n" + methods + "\n}";

        TEST_APP.addFile(new TestSourceFile("src/main/java/com/example", "A.java", classFileA));
        TEST_APP.addFile(new TestSourceFile("src/main/java/com/example", "B.java", classFileB));
    }

    @Rule
    public final GradleTestProject mProject = GradleTestProject.builder()
            .fromTestApp(TEST_APP).withHeap("2G").create();

    private final boolean mDexInProcess;

    public DexLimitTest(boolean dexInProcess) {
        mDexInProcess = dexInProcess;
    }

    @Before
    public void disableDexInProcess() throws Exception {
        if (!mDexInProcess) {
            DexInProcessHelper.disableDexInProcess(mProject.getBuildFile());
        }
    }

    @Test
    public void checkDexErrorMessage() throws Exception {
        GradleBuildResult result = mProject.executor().expectFailure().run("assembleDebug");
        assertThat(result.getStderr())
                .contains("https://developer.android.com/tools/building/multidex.html");

        // Check that when dexing in-process, we don't keep bad state after a failure
        if (mDexInProcess) {
            FileUtils.delete(mProject.file("src/main/java/com/example/A.java"));
            mProject.execute("assembleDebug");

            Apk apk = mProject.getApk("debug");
            assertThat(apk).doesNotContainClass("Lcom/example/A;");
            assertThat(apk).containsClass("Lcom/example/B;");
        }
    }
}
