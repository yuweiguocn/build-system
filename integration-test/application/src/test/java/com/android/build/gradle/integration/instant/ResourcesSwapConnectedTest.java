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
import com.android.build.gradle.integration.common.category.DeviceTestsQuarantine;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunClient;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourcesSwapConnectedTest {

    private static final String LOG_TAG = "ResourcesSwapTest.sha";
    private static final String BLACK_PNG_SHA = "256111655c33c5b5c095f6287abe6db307eab27a";
    private static final String WHITE_PNG_SHA = "bdd80c122f819fd58ee0603530d27f591a9cc46c";

    @Rule
    public GradleTestProject mProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public Logcat logcat = Logcat.create();

    @Rule public final Adb adb = new Adb();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                mProject.getBuildFile(),
                // Use Guava for hashing:
                "dependencies { api 'com.google.guava:guava:19.0'}\n"
                        // Don't mess with the PNGs, to keep hashes stable:
                        + "android.aaptOptions.cruncherEnabled = false\n");

        copyTestResourceToProjectFile("images/black.png");

        File activity = mProject.file("src/main/java/com/example/helloworld/HelloWorld.java");

        TestFileUtils.addMethod(
                activity,
                "private void logChecksum() {\n"
                        + "    java.io.InputStream stream = \n"
                        + "            getResources().openRawResource(com.example.helloworld.R.drawable.image);\n"
                        + "        \n"
                        + "    try {\n"
                        + "        byte [] bytes = com.google.common.io.ByteStreams.toByteArray(stream);\n"
                        + "        android.util.Log.d(\n"
                        + "            \""
                        + LOG_TAG
                        + "\", com.google.common.hash.Hashing.sha1().hashBytes(bytes).toString());\n"
                        + "    } catch (java.io.IOException e) {\n"
                        + "        throw new RuntimeException(e);\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.searchAndReplace(activity, "// onCreate", "logChecksum();");
    }

    @Test
    @Category(DeviceTestsQuarantine.class)
    public void swapResourcesDeviceTest_art() throws Exception {
        // Run up to 5 times if necessary, the test sometimes even 3 times in a row.
        testWithRetries(5, this::doTest);
    }

    private void doTest() throws Exception {
        IDevice device = adb.getDevice(AndroidVersionMatcher.thatUsesArt());
        try {
            HotSwapTester tester =
                    new HotSwapTester(
                            mProject,
                            HelloWorldApp.APP_ID,
                            "HelloWorld",
                            LOG_TAG,
                            device,
                            logcat,
                            PORTS.get(ResourcesSwapConnectedTest.class.getSimpleName()));

            tester.run(
                    () -> {
                        List<LogCatMessage> allMessages = logcat.getFilteredLogCatMessages();
                        String sha = allMessages.get(0).getMessage();
                        assertThat(sha).named("SHA on first run").isEqualTo(BLACK_PNG_SHA);
                    },
                    new HotSwapTester.Change() {
                        @Override
                        public void makeChange() throws Exception {
                            copyTestResourceToProjectFile("images/white.png");
                        }

                        @Override
                        public void verifyChange(
                                @NonNull InstantRunClient client,
                                @NonNull Logcat logcat,
                                @NonNull IDevice device) {
                            String sha = logcat.getFilteredLogCatMessages().get(0).getMessage();

                            assertThat(sha)
                                    .named("SHA after first change")
                                    .isEqualTo(WHITE_PNG_SHA);
                        }

                        @Override
                        public InstantRunArtifactType getExpectedArtifactType() {
                            return InstantRunArtifactType.RESOURCES;
                        }
                    },
                    new HotSwapTester.Change() {
                        @Override
                        public void makeChange() throws Exception {
                            copyTestResourceToProjectFile("images/black.png");
                        }

                        @Override
                        public void verifyChange(
                                @NonNull InstantRunClient client,
                                @NonNull Logcat logcat,
                                @NonNull IDevice device) {
                            String sha = logcat.getFilteredLogCatMessages().get(0).getMessage();

                            assertThat(sha)
                                    .named("SHA after second change")
                                    .isEqualTo(BLACK_PNG_SHA);
                        }

                        @Override
                        public InstantRunArtifactType getExpectedArtifactType() {
                            return InstantRunArtifactType.RESOURCES;
                        }
                    });
        } finally {
            // In case we failed and need to re-run the test, clean up after the last run.
            cleanUp();
        }
    }

    private void cleanUp() throws Exception {
        // Revert the image to the state it was before the tests ran.
        copyTestResourceToProjectFile("images/black.png");
        // Return the device, so we can get it again for the next run. We cannot just keep the same
        // iDevice because that results in concurrent modification exceptions.
        adb.close();
    }

    private void copyTestResourceToProjectFile(String resourceName) throws Exception {
        File file = mProject.file("src/main/res/drawable/image.png");
        Files.createParentDirs(file);

        Resources.asByteSource(Resources.getResource(resourceName)).copyTo(Files.asByteSink(file));
    }

    // TODO(imorlowska): if this works, refactor it out so other tests can use it too.
    private static void testWithRetries(int retries, TestMethod test) throws Exception {
        int failedTries = 0;
        while (true) {
            try {
                test.runTest();
                break;
            } catch (AssertionError e) {
                failedTries++;
                if (failedTries >= retries) {
                    System.out.println("Failed too many times.");
                    throw new TooManyRetriesException(e);
                }
                System.out.println("Failed with error: " + e.getMessage());
                System.out.println("Retrying...");
            }
        }
    }

    private interface TestMethod {
        void runTest() throws Exception;
    }

    public static class TooManyRetriesException extends Exception {
        public TooManyRetriesException(Throwable e) {
            super(e);
        }
    }
}
