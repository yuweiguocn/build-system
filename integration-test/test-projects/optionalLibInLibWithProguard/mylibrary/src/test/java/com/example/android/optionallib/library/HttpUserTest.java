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

package com.example.android.optionallib.library;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.http.HttpEntity;
import org.junit.Test;

public class HttpUserTest {

    /** test to make sure org.apache.http.legacy is on the unit test runtime classpath */
    @Test
    public void testMockHttpEntity() {
        HttpEntity httpEntity = mock(HttpEntity.class);
        when(httpEntity.getContentLength()).thenReturn(1L);
        assertEquals(1L, HttpUser.getHttpEntityContentLength(httpEntity));
    }
}
