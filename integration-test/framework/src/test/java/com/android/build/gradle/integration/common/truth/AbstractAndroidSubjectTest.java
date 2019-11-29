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

package com.android.build.gradle.integration.common.truth;

import static com.android.build.gradle.integration.common.truth.AbstractAndroidSubject.isClassName;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import org.junit.Test;

public class AbstractAndroidSubjectTest {

    @Test
    public void testCheckClassName() throws Exception {

        checkFalse("");
        checkFalse("abc");
        checkFalse("L;");
        checkFalse("Lcom/foo");
        checkFalse("L/com/foo/Foo;");
        checkFalse("Lcom/12/Foo;");

        checkTrue("Lcom/foo/Foo;");
        checkTrue("Lcom/g/Foo;");
        checkTrue("Lcom/g12/Foo;");
        checkTrue("Lcom/foo/Foo.Bar;");
        checkTrue("LFoo;");
    }

    private static void checkTrue(String string) {
        assertThat(isClassName(string)).named(string).isTrue();
    }

    private static void checkFalse(String string) {
        assertThat(isClassName(string)).named(string).isFalse();
    }
}