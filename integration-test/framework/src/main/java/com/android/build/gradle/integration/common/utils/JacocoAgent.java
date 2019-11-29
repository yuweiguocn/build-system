/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.testutils.TestUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Objects;
import org.jacoco.agent.AgentJar;

/**
 * Utility to setup for Jacoco agent.
 */
public class JacocoAgent {
    public static boolean isJacocoEnabled() {
        if (TestUtils.runningFromBazel()) {
            return getJacocoJavaAgent() != null;
        }
        String attachJacoco = System.getenv("ATTACH_JACOCO_AGENT");
        return attachJacoco != null;
    }

    public static String getJvmArg() {
        if (TestUtils.runningFromBazel()) {
            return getBazelJacocoAgentJvmArg();
        }
        return getJvmArgGradle();
    }

    @NonNull
    private static String getJvmArgGradle() {
        File buildDir = GradleTestProject.BUILD_DIR;
        File jacocoAgent = new File(buildDir, "jacoco/agent.jar");
        if (!jacocoAgent.isFile()) {
            try {
                AgentJar.extractTo(jacocoAgent);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return "-javaagent:" + jacocoAgent.toString() + "=destfile=" + buildDir + "/jacoco/test.exec";
    }


    private static String getBazelJacocoAgentJvmArg() {
        String jacocoJavaAgent = Objects.requireNonNull(getJacocoJavaAgent());
        return "-javaagent:"
                // The java agent of the outer JVM uses a relative path, convert it to absolute.
                + System.getProperty("user.dir")
                + "/"
                // Make the inner exec file distinct to avoid two processes trying to write to
                // it at the same time.
                + jacocoJavaAgent.substring("-javaagent:".length()).replace(".exec", "_inner.exec");
    }

    @Nullable
    private static String getJacocoJavaAgent() {
        RuntimeMXBean mxBean = ManagementFactory.getRuntimeMXBean();
        List<String> inputArguments = mxBean.getInputArguments();
        for (String inputArgument : inputArguments) {
            if (inputArgument.startsWith("-javaagent") && inputArgument.contains("jacoco.agent")) {
                return inputArgument;
            }
        }
        return null;
    }
}
