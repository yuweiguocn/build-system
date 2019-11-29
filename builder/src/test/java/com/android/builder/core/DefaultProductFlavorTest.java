/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.builder.internal.ClassFieldImpl;
import com.google.common.collect.ImmutableMap;
import junit.framework.TestCase;

public class DefaultProductFlavorTest extends TestCase {

    private DefaultProductFlavor custom;

    @Override
    protected void setUp() throws Exception {
        custom = new DefaultProductFlavor("custom");
        custom.setMinSdkVersion(new DefaultApiVersion(42));
        custom.setTargetSdkVersion(new DefaultApiVersion(43));
        custom.setRenderscriptTargetApi(17);
        custom.setVersionCode(44);
        custom.setVersionName("42.0");
        custom.setApplicationId("com.forty.two");
        custom.setTestApplicationId("com.forty.two.test");
        custom.setTestInstrumentationRunner("com.forty.two.test.Runner");
        custom.setTestHandleProfiling(true);
        custom.setTestFunctionalTest(true);
        custom.addResourceConfiguration("hdpi");
        custom.addManifestPlaceholders(ImmutableMap.of("one", "oneValue", "two", "twoValue"));

        custom.addResValue(new ClassFieldImpl("foo", "one", "oneValue"));
        custom.addResValue(new ClassFieldImpl("foo", "two", "twoValue"));
        custom.addBuildConfigField(new ClassFieldImpl("foo", "one", "oneValue"));
        custom.addBuildConfigField(new ClassFieldImpl("foo", "two", "twoValue"));
        custom.setVersionNameSuffix("custom");
        custom.setApplicationIdSuffix("custom");
    }

    public void test_initWith() {
        DefaultProductFlavor flavor = new DefaultProductFlavor(custom.getName());
        flavor._initWith(custom);
        assertEquals(custom.toString(), flavor.toString());
    }
}
