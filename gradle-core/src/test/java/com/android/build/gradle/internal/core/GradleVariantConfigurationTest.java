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

package com.android.build.gradle.internal.core;

import static com.android.build.gradle.options.IntegerOption.IDE_TARGET_DEVICE_API;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.DefaultVectorDrawablesOptions;
import com.android.builder.core.VariantTypeImpl;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test cases for {@link GradleVariantConfiguration}. */
public class GradleVariantConfigurationTest {

    @Mock CoreBuildType buildType;
    @Mock CoreProductFlavor coreProductFlavor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(coreProductFlavor.getName()).thenReturn("mockCoreProductFlavor");
        when(coreProductFlavor.getVectorDrawables())
                .thenReturn(new DefaultVectorDrawablesOptions());
        when(coreProductFlavor.getMinSdkVersion()).thenReturn(new DefaultApiVersion(16));
        when(coreProductFlavor.getTargetSdkVersion()).thenReturn(new DefaultApiVersion(20));
    }

    @Test
    public void testGetMinSdkVersion_MultiDexEnabledNonDebuggable() {
        when(buildType.getMultiDexEnabled()).thenReturn(true);
        when(buildType.isDebuggable()).thenReturn(false);

        GradleVariantConfiguration variant = createVariant(18);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(16);
    }

    @Test
    public void testGetMinSdkVersion_MultiDexDisabledIsDebuggable() {
        when(buildType.getMultiDexEnabled()).thenReturn(false);
        when(buildType.isDebuggable()).thenReturn(true);

        GradleVariantConfiguration variant = createVariant(18);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(16);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiLessSdkVersion() {
        when(buildType.getMultiDexEnabled()).thenReturn(true);
        when(buildType.isDebuggable()).thenReturn(true);

        GradleVariantConfiguration variant = createVariant(18);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiGreaterSdkVersion() {
        when(buildType.getMultiDexEnabled()).thenReturn(true);
        when(buildType.isDebuggable()).thenReturn(true);

        GradleVariantConfiguration variant = createVariant(22);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(20);
    }

    private GradleVariantConfiguration createVariant(int deviceApiVersion) {
        return new GradleVariantConfiguration(
                new ProjectOptions(
                        ImmutableMap.of(IDE_TARGET_DEVICE_API.getPropertyName(), deviceApiVersion)),
                null,
                coreProductFlavor,
                new MockSourceProvider("src/main"),
                null,
                buildType,
                null,
                VariantTypeImpl.BASE_APK,
                null,
                new FakeEvalIssueReporter(),
                () -> true);
    }
}
