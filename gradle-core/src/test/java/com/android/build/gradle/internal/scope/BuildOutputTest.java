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

package com.android.build.gradle.internal.scope;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.google.common.collect.ImmutableList;
import java.io.File;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link BuildOutput} class. */
public class BuildOutputTest {

    @Mock ApkData apkInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEquals() {
        EqualsVerifier.forClass(BuildOutput.class)
                // This is done because EqualsVerifier can't instantiate some fields of ApkData
                // so we need to give it a little help to build a valid BuildOutput object.
                .withPrefabValues(
                        ApkData.class,
                        ApkData.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0),
                        ApkData.of(VariantOutput.OutputType.FULL_SPLIT, ImmutableList.of(), 1))
                .verify();
    }

    @Test
    public void testGetFilterTypes() {
        BuildOutput buildOutput =
                new BuildOutput(InternalArtifactType.APK, apkInfo, new File("/tmp/bar/output"));
        when(apkInfo.getFilters())
                .thenReturn(
                        ImmutableList.of(
                                new FilterDataImpl(VariantOutput.FilterType.LANGUAGE, "fr"),
                                new FilterDataImpl(VariantOutput.FilterType.DENSITY, "xhdpi"),
                                new FilterDataImpl(VariantOutput.FilterType.ABI, "arm")));

        assertThat(buildOutput.getFilterTypes())
                .containsExactlyElementsIn(
                        ImmutableList.of(OutputFile.DENSITY, OutputFile.ABI, OutputFile.LANGUAGE));
    }
}
