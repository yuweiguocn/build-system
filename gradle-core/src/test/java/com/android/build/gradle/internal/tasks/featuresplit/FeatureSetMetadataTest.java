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

package com.android.build.gradle.internal.tasks.featuresplit;

import static com.google.common.truth.Truth.assertThat;

import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for {@link FeatureSetMetadata} class. */
@RunWith(Parameterized.class)
public class FeatureSetMetadataTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "minSdkVersion={0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(
                new Object[][] {
                    {AndroidVersion.VersionCodes.LOLLIPOP}, {AndroidVersion.VersionCodes.O}
                });
    }

    final int minSdkVersion;

    public FeatureSetMetadataTest(int minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    @Before
    public void setUp() {
    }

    @Test(expected = FileNotFoundException.class)
    public void testMissingPersistenceFile() throws IOException {
        FeatureSetMetadata.load(new File(""));
    }

    @Test
    public void testPersistence() throws IOException {
        FeatureSetMetadata featureSetMetadata =
                new FeatureSetMetadata(FeatureSetMetadata.MAX_NUMBER_OF_SPLITS_BEFORE_O);
        featureSetMetadata.addFeatureSplit(minSdkVersion, ":one", "one");
        featureSetMetadata.addFeatureSplit(minSdkVersion, ":two", "two");
        featureSetMetadata.addFeatureSplit(minSdkVersion, ":three", "three");
        featureSetMetadata.save(
                new File(temporaryFolder.getRoot(), FeatureSetMetadata.OUTPUT_FILE_NAME));
        File[] files = temporaryFolder.getRoot().listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isEqualTo(1);
        File outputFile = files[0];
        assertThat(outputFile.exists()).isTrue();

        // now reload the file.
        FeatureSetMetadata loaded = FeatureSetMetadata.load(outputFile);

        assertThat(loaded.getResOffsetFor(":one"))
                .named("getResOffsetFor :one")
                .isEqualTo(
                        minSdkVersion < 26
                                ? FeatureSetMetadata.BASE_ID - 1
                                : FeatureSetMetadata.BASE_ID + 1);
        assertThat(loaded.getFeatureNameFor(":one"))
                .named("getFeatureNameFor :one")
                .isEqualTo("one");

        assertThat(loaded.getResOffsetFor(":two"))
                .named("getResOffsetFor :two")
                .isEqualTo(
                        minSdkVersion < 26
                                ? FeatureSetMetadata.BASE_ID - 2
                                : FeatureSetMetadata.BASE_ID + 2);
        assertThat(loaded.getFeatureNameFor(":two"))
                .named("getFeatureNameFor :two")
                .isEqualTo("two");

        assertThat(loaded.getResOffsetFor(":three"))
                .named("getResOffsetFor :three")
                .isEqualTo(
                        minSdkVersion < 26
                                ? FeatureSetMetadata.BASE_ID - 3
                                : FeatureSetMetadata.BASE_ID + 3);
        assertThat(loaded.getFeatureNameFor(":three"))
                .named("getFeatureNameFor :three")
                .isEqualTo("three");

    }
}
