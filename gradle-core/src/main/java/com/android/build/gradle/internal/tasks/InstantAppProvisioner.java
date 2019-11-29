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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.ConnectedDevice;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.IDevice;
import com.android.instantapp.provision.ProvisionException;
import com.android.instantapp.provision.ProvisionListener;
import com.android.instantapp.provision.ProvisionRunner;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import org.gradle.api.logging.Logger;

/**
 * Code consumed by {@link InstantAppProvisionTask}. In a separate class for testing without
 * exposing methods.
 */
public class InstantAppProvisioner {
    @NonNull private final File instantAppSdk;
    @NonNull private final DeviceProvider deviceProvider;
    @NonNull private final Logger logger;

    private ProvisionRunner fakeProvisionRunner;

    InstantAppProvisioner(
            @NonNull File instantAppSdk,
            @NonNull DeviceProvider deviceProvider,
            @NonNull Logger logger) {
        this.instantAppSdk = instantAppSdk;
        this.deviceProvider = deviceProvider;
        this.logger = logger;
    }

    void provisionDevices() throws ProvisionException, DeviceException {
        ProvisionListener listener =
                new ProvisionListener() {
                    @Override
                    public void printMessage(@NonNull String message) {
                        logger.info(message);
                    }

                    @Override
                    public void logMessage(
                            @NonNull String message, @Nullable ProvisionException e) {
                        if (e == null) {
                            logger.debug(message);
                        } else {
                            logger.debug(message, e);
                            logger.error(message, e);
                        }
                    }

                    @Override
                    public void setProgress(double fraction) {}

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                };

        ProvisionRunner provisionRunner =
                fakeProvisionRunner == null
                        ? new ProvisionRunner(instantAppSdk, listener)
                        : fakeProvisionRunner;

        deviceProvider.init();

        try {
            List<? extends DeviceConnector> devices = deviceProvider.getDevices();
            List<IDevice> iDevices = Lists.newArrayList();
            for (DeviceConnector device : devices) {
                if (device instanceof ConnectedDevice) {
                    iDevices.add(((ConnectedDevice) device).getIDevice());
                }
            }

            for (IDevice device : iDevices) {
                provisionRunner.runProvision(device);
            }
        } finally {
            deviceProvider.terminate();
        }
    }

    @VisibleForTesting
    void setFakeProvisionRunner(@NonNull ProvisionRunner fakeProvisionRunner) {
        this.fakeProvisionRunner = fakeProvisionRunner;
    }
}
