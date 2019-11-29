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

package com.android.build.gradle.options;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.options.SyncOptions.ErrorFormatMode;
import com.android.build.gradle.options.SyncOptions.EvaluationMode;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class SyncOptionsTest {
    @Test
    public void getModelQueryMode() throws Exception {
        ProjectOptions noOptions = new ProjectOptions(ImmutableMap.of());
        assertThat(SyncOptions.getModelQueryMode(noOptions)).isEqualTo(EvaluationMode.STANDARD);

        ProjectOptions advancedOptions =
                new ProjectOptions(
                        ImmutableMap.of(
                                "android.injected.build.model.only.advanced",
                                "true",
                                "android.injected.build.model.only",
                                "true"));
        assertThat(SyncOptions.getModelQueryMode(advancedOptions)).isEqualTo(EvaluationMode.IDE);
    }

    @Test
    public void getErrorFormatMode() throws Exception {

        ProjectOptions noOptions = new ProjectOptions(ImmutableMap.of());
        assertThat(SyncOptions.getErrorFormatMode(noOptions))
                .isEqualTo(ErrorFormatMode.HUMAN_READABLE);

        ProjectOptions ideOptions =
                new ProjectOptions(ImmutableMap.of("android.injected.invoked.from.ide", "true"));
        assertThat(SyncOptions.getErrorFormatMode(ideOptions))
                .isEqualTo(ErrorFormatMode.MACHINE_PARSABLE);
    }
}
