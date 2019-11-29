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

package com.android.build.gradle.external.cmake.server;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ServerFactoryTest {
    File mockCmakeInstallPath;
    ServerReceiver serverReceiver;

    @Before
    public void setUp() throws Exception {
        mockCmakeInstallPath = Mockito.mock(File.class);
        serverReceiver = new ServerReceiver();
    }

    @Test
    public void testValidServerCreation() throws IOException {
        assertThat(
                        ServerFactory.create(
                                CmakeUtils.getVersion("3.7.1"),
                                mockCmakeInstallPath,
                                serverReceiver))
                .isNotNull();
        assertThat(
                        ServerFactory.create(
                                CmakeUtils.getVersion("3.8.0-rc2"),
                                mockCmakeInstallPath,
                                serverReceiver))
                .isNotNull();
    }

    @Test
    public void testInvalidServerCreation() throws IOException {
        assertThat(
                        ServerFactory.create(
                                CmakeUtils.getVersion("3.6.3"),
                                mockCmakeInstallPath,
                                serverReceiver))
                .isNull();
        assertThat(
                        ServerFactory.create(
                                CmakeUtils.getVersion("2.3.5-rc2"),
                                mockCmakeInstallPath,
                                serverReceiver))
                .isNull();
        assertThat(
                        ServerFactory.create(
                                CmakeUtils.getVersion("1.2.1"),
                                mockCmakeInstallPath,
                                serverReceiver))
                .isNull();
    }
}
