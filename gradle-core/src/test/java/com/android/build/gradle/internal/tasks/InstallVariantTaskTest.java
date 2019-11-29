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

package com.android.build.gradle.internal.tasks;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessExecutor;
import com.android.sdklib.AndroidVersion;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class InstallVariantTaskTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock DeviceConnector kitkatDevice;
    @Mock DeviceConnector lollipopDevice;
    @Mock Logger logger;
    private ProcessExecutor processExecutor =
            new DefaultProcessExecutor(new StdLogger(StdLogger.Level.INFO));

    @NonNull
    private static BuildOutput createSingleMainApkOutput(@NonNull File mainOutputFileApk) {

        ApkData apkInfo = mock(ApkData.class);
        when(apkInfo.getType()).thenReturn(OutputFile.OutputType.MAIN);
        return new BuildOutput(InternalArtifactType.APK, apkInfo, mainOutputFileApk);
    }

    @Before
    public void setUpMocks() throws Exception {
        when(lollipopDevice.getApiLevel()).thenReturn(21);
        when(lollipopDevice.getName()).thenReturn("lollipop_device");
        when(kitkatDevice.getApiLevel()).thenReturn(19);
        when(kitkatDevice.getName()).thenReturn("kitkat_device");
    }

    @Test
    public void checkPreLSingleApkInstall() throws Exception {
        checkSingleApk(kitkatDevice);
    }

    @Test
    public void checkPostKSingleApkInstall() throws Exception {
        checkSingleApk(lollipopDevice);
    }

    private void checkSingleApk(DeviceConnector deviceConnector) throws Exception {
        File mainOutputFileApk = temporaryFolder.newFile("main.apk");

        InstallVariantTask.install(
                "project",
                "variant",
                new FakeDeviceProvider(ImmutableList.of(deviceConnector)),
                new AndroidVersion(1),
                processExecutor,
                temporaryFolder.newFile("split_select"),
                ImmutableList.of(createSingleMainApkOutput(mainOutputFileApk)),
                null,
                ImmutableList.of(),
                4000,
                logger);
        verify(logger)
                .lifecycle(
                        "Installing APK '{}' on '{}' for {}:{}",
                        "main.apk",
                        deviceConnector.getName(),
                        "project",
                        "variant");
        verify(logger).quiet("Installed on {} {}.", 1, "device");
        verifyNoMoreInteractions(logger);

        verify(deviceConnector, atLeastOnce()).getName();
        verify(deviceConnector, atLeastOnce()).getApiLevel();
        verify(deviceConnector, atLeastOnce()).getDensity();
        verify(deviceConnector, atLeastOnce()).getAbis();
        verify(deviceConnector, atLeastOnce()).getDeviceConfig();
        verify(deviceConnector).installPackage(eq(mainOutputFileApk), any(), anyInt(), any());
        verifyNoMoreInteractions(deviceConnector);
    }

    static final class FakeDeviceProvider extends DeviceProvider {

        private final List<DeviceConnector> devices;
        private State state = State.NOT_READY;

        FakeDeviceProvider(@NonNull List<DeviceConnector> devices) {
            this.devices = devices;
        }

        @NonNull
        @Override
        public String getName() {
            return "FakeDeviceProvider";
        }

        @Override
        public void init() throws DeviceException {
            if (state != State.NOT_READY) {
                throw new IllegalStateException(
                        "Can only go to READY from NOT_READY. Current state is " + state);
            }
            state = State.READY;
        }

        @Override
        public void terminate() throws DeviceException {
            if (state != State.READY) {
                throw new IllegalStateException(
                        "Can only go to TERMINATED from READY. Current state is " + state);
            }
            state = State.TERMINATED;
        }

        @NonNull
        @Override
        public List<? extends DeviceConnector> getDevices() {
            return devices;
        }

        @Override
        public int getTimeoutInMs() {
            return 4000;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        private enum State {
            NOT_READY,
            READY,
            TERMINATED,
        }
    }
}
