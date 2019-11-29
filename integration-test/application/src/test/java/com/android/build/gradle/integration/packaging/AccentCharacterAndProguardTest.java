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

package com.android.build.gradle.integration.packaging;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.testutils.TestUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks that we can handle a class with non-ASCII name in combination with ProGuard.
 *
 * <p>See http://b.android.com/221057
 */
public class AccentCharacterAndProguardTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minify").create();

    @Before
    public void createClass() throws Exception {
        File classWithUnicodeName =
                project.file("src/main/java/com/android/tests/basic/Ubicación.java");
        String classBody = "package com.android.tests.basic; public class Ubicación {}";

        Files.write(
                classWithUnicodeName.toPath(),
                classBody.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW);
    }

    @Before
    public void setUpProguard() throws Exception {
        File proguardRules = project.file("proguard-rules.pro");

        Files.write(
                proguardRules.toPath(),
                ImmutableList.of("-dontshrink", "-dontobfuscate"),
                StandardOpenOption.APPEND);
    }

    @Test
    public void assemble() throws Exception {
        // FIXME: b/79189049
        Assume.assumeFalse(TestUtils.runningFromBazel());
        GradleBuildResult result =
                project.executor().run("assembleMinified");
    }
}
