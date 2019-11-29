/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.builder.core;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.ApiVersion;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class DefaultApiVersionTest {

    @Test
    public void checkPreviewSdkVersion() {
        ApiVersion version = new DefaultApiVersion("P");
        assertThat(version.getApiLevel()).isEqualTo(27);
        assertThat(version.getApiString()).isEqualTo("P");
        assertThat(version.getCodename()).isEqualTo("P");
    }

    @Test
    public void checkEquals() {
        EqualsVerifier.forClass(DefaultApiVersion.class).verify();
    }
}
