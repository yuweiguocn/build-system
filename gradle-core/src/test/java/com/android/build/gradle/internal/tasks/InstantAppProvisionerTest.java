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

package com.android.build.gradle.internal.tasks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.builder.testing.ConnectedDevice;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.IDevice;
import com.android.instantapp.provision.ProvisionRunner;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import org.gradle.api.logging.Logger;
import org.junit.Test;

/** Unit tests for {@link InstantAppProvisioner}. */
public class InstantAppProvisionerTest {

    @Test
    public void testProvisionIsRun() throws Exception {
        IDevice device1 = mock(IDevice.class);
        IDevice device2 = mock(IDevice.class);

        ProvisionRunner provisionRunner = mock(ProvisionRunner.class);

        File instantAppSdk = mock(File.class);

        InstantAppProvisioner provisioner =
                new InstantAppProvisioner(
                        instantAppSdk,
                        new FakeDeviceProvider(Lists.newArrayList(device1, device2)),
                        mock(Logger.class));

        provisioner.setFakeProvisionRunner(provisionRunner);

        provisioner.provisionDevices();

        verify(provisionRunner, times(1)).runProvision(device1);
        verify(provisionRunner, times(1)).runProvision(device2);
    }

    private static final class FakeDeviceProvider extends DeviceProvider {
        @NonNull private final List<? extends DeviceConnector> devices;

        private FakeDeviceProvider(@NonNull List<IDevice> iDevices) {
            devices =
                    Lists.transform(
                            iDevices,
                            input -> {
                                ConnectedDevice connectedDevice = mock(ConnectedDevice.class);
                                when(connectedDevice.getIDevice()).thenReturn(input);
                                return connectedDevice;
                            });
        }

        @NonNull
        @Override
        public String getName() {
            return "FakeDeviceProvider";
        }

        @Override
        public void init() throws DeviceException {}

        @Override
        public void terminate() throws DeviceException {}

        @NonNull
        @Override
        public List<? extends DeviceConnector> getDevices() {
            return devices;
        }

        @Override
        public int getTimeoutInMs() {
            return 0;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }
}
