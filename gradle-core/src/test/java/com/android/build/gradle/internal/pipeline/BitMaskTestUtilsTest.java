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

package com.android.build.gradle.internal.pipeline;

import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import org.junit.Test;

public class BitMaskTestUtilsTest {

    @Test
    public void checkValidCase() {
        BitMaskTestUtils.checkScopeBitMaskUnique(ImmutableSet.of(1,2,4,8,16,32), Integer::intValue);
    }

    @Test
    public void checkInvalidCase() {
        try {
            BitMaskTestUtils.checkScopeBitMaskUnique(ImmutableSet.of(1,2,4,8,16,34), Integer::intValue);
            fail();
        } catch (AssertionError e) {
            Truth.assertThat(e.getMessage()).contains("34 [100010]");
            Truth.assertThat(e.getMessage()).contains("2 [10]");
            Truth.assertThat(e.getMessage()).contains("are not unique");
        }
    }

    @Test
    public void checkEmptyMask() {
        try {
            BitMaskTestUtils.checkScopeBitMaskUnique(ImmutableSet.of(0), Integer::intValue);
            fail();
        } catch (AssertionError e) {
            Truth.assertThat(e.getMessage()).contains("Bit mask for 0 is zero");
        }
    }
}
