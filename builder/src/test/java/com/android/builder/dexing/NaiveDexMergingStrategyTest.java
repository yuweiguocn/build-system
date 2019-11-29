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

package com.android.builder.dexing;

import static com.android.builder.dexing.NaiveDexMergingStrategy.MAX_NUMBER_OF_IDS_IN_DEX;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.dex.Dex;
import java.io.IOException;
import org.junit.Test;

/** Tests for the {@link NaiveDexMergingStrategy}. */
public class NaiveDexMergingStrategyTest {

    @Test
    public void testToManyFieldsAndMethods() throws IOException {
        NaiveDexMergingStrategy strategy = new NaiveDexMergingStrategy();

        assertThat(strategy.tryToAddForMerging(createDex(100, 100))).isTrue();
        assertThat(strategy.tryToAddForMerging(createDex(100, 100))).isTrue();

        Dex bigDex = createDex(MAX_NUMBER_OF_IDS_IN_DEX, MAX_NUMBER_OF_IDS_IN_DEX);
        assertThat(strategy.tryToAddForMerging(bigDex)).isFalse();

        strategy.startNewDex();
        assertThat(strategy.tryToAddForMerging(bigDex)).isTrue();
    }

    @Test
    public void testTooManyFields() throws IOException {
        NaiveDexMergingStrategy strategy = new NaiveDexMergingStrategy();

        assertThat(strategy.tryToAddForMerging(createDex(100, 0))).isTrue();
        Dex bigDex = createDex(MAX_NUMBER_OF_IDS_IN_DEX, 0);
        assertThat(strategy.tryToAddForMerging(bigDex)).isFalse();

        strategy.startNewDex();
        assertThat(strategy.tryToAddForMerging(bigDex)).isTrue();
    }

    @Test
    public void testTooManyMethods() throws IOException {
        NaiveDexMergingStrategy strategy = new NaiveDexMergingStrategy();

        assertThat(strategy.tryToAddForMerging(createDex(0, 100))).isTrue();
        Dex bigDex = createDex(0, MAX_NUMBER_OF_IDS_IN_DEX);
        assertThat(strategy.tryToAddForMerging(bigDex)).isFalse();

        strategy.startNewDex();
        assertThat(strategy.tryToAddForMerging(bigDex)).isTrue();
    }

    @NonNull
    private static Dex createDex(int numFields, int numMethods) throws IOException {
        Dex dex = new Dex(0);
        dex.getTableOfContents().fieldIds.size = numFields;
        dex.getTableOfContents().methodIds.size = numMethods;
        return dex;
    }
}
