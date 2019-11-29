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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.AndroidVersionMatcher.thatUsesArt;
import static com.android.build.gradle.integration.instant.InstantRunTestUtils.PORTS;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.UninstallOnClose;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.packaging.PackagingUtils;
import com.android.ddmlib.IDevice;
import com.android.tools.ir.client.AppState;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.ir.client.InstantRunClient;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import java.io.Closeable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Connected smoke test for cold swap. */
@Category(DeviceTests.class)
public class ColdSwapConnectedTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    @Rule
    public Adb adb = new Adb();

    @Rule
    public Expect expect = Expect.create();

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public TestWatcher onFailure = new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
            InstantRunTestUtils.printBuildInfoFile(instantRunModel);
        }
    };

    @Mock
    ILogger iLogger;

    @Before
    public void activityClass() throws Exception {
        createActivityClass("Logger.getLogger(\"coldswaptest\").warning(\"coldswaptest_before\");\n");
    }

    @Test
    public void multiApkTest() throws Exception {
        doTest(adb.getDevice(thatUsesArt()));
    }

    private InstantRun instantRunModel;

    private void doTest(@NonNull IDevice device)
            throws Exception {
        try (Closeable ignored = new UninstallOnClose(device, HelloWorldApp.APP_ID)) {
            // Set up
            device.uninstallPackage(HelloWorldApp.APP_ID);
            logcat.start(device, "coldswaptest");
            AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();
            instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
            long token = PackagingUtils.computeApplicationHash(model.getBuildFolder());

            // Initial build
            project.executor()
                    .withInstantRun(device, OptionalCompilationStep.RESTART_ONLY)
                    .run("clean", "assembleDebug");

            InstantRunBuildInfo info = InstantRunTestUtils.loadContext(instantRunModel);
            InstantRunTestUtils.doInstall(device, info);
            InstantRunTestUtils.unlockDevice(device);
            Logcat.MessageListener messageListener = logcat.listenForMessage("coldswaptest_before");
            InstantRunTestUtils.runApp(device, HelloWorldApp.APP_ID + "/.HelloWorld");
            // Connect to device
            InstantRunClient client =
                    new InstantRunClient(
                            HelloWorldApp.APP_ID,
                            iLogger,
                            token,
                            PORTS.get(ColdSwapConnectedTest.class.getSimpleName()));

            // Give the app a chance to start
            messageListener.await();

            // Check the app is running
            assertThat(client.getAppState(device)).isEqualTo(AppState.FOREGROUND);

            // Cold swap
            makeColdSwapChange();
            project.executor().withInstantRun(device).run("assembleDebug");

            InstantRunBuildInfo coldSwapContext = InstantRunTestUtils.loadContext(instantRunModel);

            InstantRunTestUtils.doInstall(device, info);

            Logcat.MessageListener afterMessageListener =
                    logcat.listenForMessage("coldswaptest_after");

            InstantRunTestUtils.runApp(device, HelloWorldApp.APP_ID + "/.HelloWorld");
            // Check the app is running
            afterMessageListener.await();
            InstantRunTestUtils.waitForAppStart(client, device);
        }
    }

    private void makeColdSwapChange() throws Exception {
        createActivityClass("newMethod();\n"
                + "    }\n"
                + "    public void newMethod() {\n"
                + "        Logger.getLogger(\"coldswaptest\").warning(\"coldswaptest_after\");\n"
                + "");
    }

    private void createActivityClass(@NonNull String newMethodBody) throws Exception {
        String javaCompile = "package com.example.helloworld;\n"
                + "\n"
                + "import java.util.logging.Logger;\n" +
                "\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "\n"
                + "public class HelloWorld extends Activity {\n"
                + "    /** Called when the activity is first created. */\n"
                + "    @Override\n"
                + "    public void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.main);\n"
                + "        " +
                newMethodBody +
                "    }\n"
                + "}";
        Files.asCharSink(
                        project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                        Charsets.UTF_8)
                .write(javaCompile);
    }

}
