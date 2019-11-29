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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.instant.InstantRunTestUtils.PORTS;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.ddmlib.IDevice;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HotSwapConnectedTest {
    private static final String LOG_TAG = "hotswapTest";
    private static final String ORIGINAL_MESSAGE = "Original";
    private static final int CHANGES_COUNT = 3;

    @Rule public final Adb adb = new Adb();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public Logcat logcat = Logcat.create();

    @Test
    @Ignore("b/68305039")
    @Category(DeviceTests.class)
    public void artHotSwapChangeTest() throws Exception {
        createActivityClass(ORIGINAL_MESSAGE);
        doHotSwapChangeTest(adb.getDevice(AndroidVersionMatcher.thatUsesArt()));
    }

    private void doHotSwapChangeTest(@NonNull IDevice device) throws Exception {
        HotSwapTester tester =
                new HotSwapTester(
                        project,
                        HelloWorldApp.APP_ID,
                        "HelloWorld",
                        LOG_TAG,
                        device,
                        logcat,
                        PORTS.get(HotSwapTest.class.getSimpleName()));

        List<HotSwapTester.Change> changes = new ArrayList<>();

        for (int i = 0; i < CHANGES_COUNT; i++) {
            changes.add(
                    new HotSwapTester.LogcatChange(i, ORIGINAL_MESSAGE) {
                        @Override
                        public void makeChange() throws Exception {
                            createActivityClass(CHANGE_PREFIX + changeId);
                        }
                    });
        }

        tester.run(() -> assertThat(logcat).containsMessageWithText("Original"), changes);
    }

    private void createActivityClass(String message) throws Exception {
        String javaCompile =
                "package com.example.helloworld;\n"
                        + "import android.app.Activity;\n"
                        + "import android.os.Bundle;\n"
                        + "import java.util.logging.Logger;\n"
                        + "\n"
                        + "import java.util.concurrent.Callable;\n"
                        + "\n"
                        + "public class HelloWorld extends Activity {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    @Override\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        setContentView(R.layout.main);\n"
                        + "        Callable<Void> callable = new Callable<Void>() {\n"
                        + "            @Override\n"
                        + "            public Void call() throws Exception {\n"
                        + "                Logger.getLogger(\""
                        + LOG_TAG
                        + "\")\n"
                        + "                        .warning(\""
                        + message
                        + "\");"
                        + "                return null;\n"
                        + "            }\n"
                        + "        };\n"
                        + "        try {\n"
                        + "            callable.call();\n"
                        + "        } catch (Exception e) {\n"
                        + "            throw new RuntimeException(e);\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n";
        Files.asCharSink(
                        project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                        Charsets.UTF_8)
                .write(javaCompile);
    }
}
