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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
/**
 * Assemble tests for ndkPrebuilts.
 */
public class NdkPrebuiltsTest {
    @ClassRule public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkPrebuilts")
            .create();

    public static AndroidProject model;

    @BeforeClass
    public static void setUp() throws Exception {
        model = project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void lint() throws Exception {
        project.execute("lint");
    }

    @Test
    public void checkAbiFilterInModel() throws Exception {
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 8, variants.size());

        // flavor names to ABIs
        // create a temp list to make the compiler happy. generics are fun!
        List<String> ls1 = ImmutableList.of("x86");
        Map<String, List<String>> map =
                ImmutableMap.of(
                        "x86", ls1,
                        "x86_64", ImmutableList.of("x86_64"),
                        "arm", ImmutableList.of("armeabi-v7a", "armeabi"),
                        "mips", ImmutableList.of("mips"));

        // loop on the variants
        for (Variant variant : variants) {
            String variantName = variant.getName();

            // get the flavor name to get the expected ABIs.
            List<String> flavors = variant.getProductFlavors();
            assertNotNull("Null check flavors for " + variantName, flavors);
            assertEquals("Size check flavors for " + variantName, 1, flavors.size());

            List<String> expectedAbis = map.get(flavors.get(0));

            Set<String> actualAbis = variant.getMainArtifact().getAbiFilters();
            assertNotNull("Null check artifact abi for " + variantName, actualAbis);

            assertEquals("Size check artifact abis for " + variantName,
                    expectedAbis.size(), actualAbis.size());
            for (String abi : expectedAbis) {
                assertTrue("Check " + abi + " present in artifact abi for " + variantName,
                        actualAbis.contains(abi));
            }
        }
    }
}
