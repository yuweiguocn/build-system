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

package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.tasks.factory.AbstractCompilesUtil.getDefaultJavaVersion;

import org.gradle.api.JavaVersion;
import org.junit.Assert;
import org.junit.Test;

/** Tests the logic for choosing Java language level. */
public class AbstractCompilesUtilTest {

    @Test
    public void testChooseDefaultJavaVersion_jdk8() throws Exception {
        Assert.assertEquals(JavaVersion.VERSION_1_6, getDefaultJavaVersion("android-15"));
        Assert.assertEquals(JavaVersion.VERSION_1_7, getDefaultJavaVersion("android-21"));
        Assert.assertEquals(
                JavaVersion.VERSION_1_7, getDefaultJavaVersion("Google Inc.:Google APIs:22"));
        Assert.assertEquals(JavaVersion.VERSION_1_7, getDefaultJavaVersion("android-24"));
        Assert.assertEquals(JavaVersion.VERSION_1_7, getDefaultJavaVersion("android-24"));
        Assert.assertEquals(JavaVersion.VERSION_1_7, getDefaultJavaVersion("android-24"));
    }
}
