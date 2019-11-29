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

package com.android.build.gradle.integration.ndk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Assemble tests for ndkSanAngeles.
 */
public class NdkSanAngelesTest {
    @ClassRule public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkSanAngeles")
            .create();

    public static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void setUp() throws Exception {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        outputModel = project.executeAndReturnOutputModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        outputModel = null;
    }

    @Test
    public void lint() throws Exception {
        project.execute("lint");
    }

    @Test
    public void checkVersionCodeInModel() {
        VariantBuildOutput debugOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);

        // get the outputs.
        Collection<OutputFile> debugOutputs = debugOutput.getOutputs();
        assertEquals(2, debugOutputs.size());

        // build a map of expected outputs and their versionCode
        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(2);
        expected.put("armeabi-v7a", 1000123);
        expected.put("x86", 2000123);

        assertEquals(2, debugOutputs.size());
        for (OutputFile output : debugOutputs) {
            for (FilterData filterData : output.getFilters()) {
                if (filterData.getFilterType().equals(OutputFile.ABI)) {
                    String abiFilter = filterData.getIdentifier();
                    Integer value = expected.get(abiFilter);
                    // this checks we're not getting an unexpected output.
                    assertNotNull("Check Valid output: " + abiFilter, value);

                    assertEquals(value.intValue(), output.getVersionCode());
                    expected.remove(abiFilter);
                }
            }
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }
}
