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
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InstantAppOutputScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.testing.ConnectedDevice;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.IDevice;
import com.android.instantapp.run.InstantAppRunException;
import com.android.instantapp.run.InstantAppSideLoader;
import com.android.instantapp.run.RunListener;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Task side loading an instant app variant. It looks at connected device, checks if preO or postO
 * and either multi-install the feature APKs or upload the bundle.
 */
public class InstantAppSideLoadTask extends AndroidBuilderTask {

    private Supplier<File> adbExe;

    private BuildableArtifact bundleDir;

    public InstantAppSideLoadTask() {
        this.getOutputs()
                .upToDateWhen(
                        task -> {
                            getLogger().debug("Side load task is always run.");
                            return false;
                        });
    }

    @TaskAction
    public void sideLoad() throws DeviceException, InstantAppRunException, IOException {
        if (adbExe.get() == null) {
            throw new GradleException("No adb file found.");
        }

        InstantAppOutputScope outputScope =
                InstantAppOutputScope.load(BuildableArtifactUtil.singleFile(bundleDir));

        if (outputScope == null) {
            throw new GradleException(
                    "Instant app outputs not found in "
                            + BuildableArtifactUtil.singleFile(bundleDir).getAbsolutePath()
                            + ".");
        }

        DeviceProvider deviceProvider = new ConnectedDeviceProvider(adbExe.get(), 0, getILogger());

        RunListener runListener =
                new RunListener() {
                    @Override
                    public void printMessage(@NonNull String message) {
                        getLogger().info(message);
                    }

                    @Override
                    public void logMessage(
                            @NonNull String message, @Nullable InstantAppRunException e) {
                        if (e == null) {
                            getLogger().debug(message);
                        } else {
                            getLogger().debug(message, e);
                            getLogger().error(message, e);
                        }
                    }

                    @Override
                    public void setProgress(double fraction) {}

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                };

        String appId = outputScope.getApplicationId();
        File bundleFile = outputScope.getInstantAppBundle();

        deviceProvider.init();

        try {
            List<? extends DeviceConnector> devices = deviceProvider.getDevices();
            for (DeviceConnector device : devices) {
                if (device instanceof ConnectedDevice) {
                    IDevice iDevice = ((ConnectedDevice) device).getIDevice();

                    InstantAppSideLoader sideLoader;
                    if (iDevice.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.O)) {
                        // List of apks to install in postO rather than unzipping the bundle
                        // It will be computed only if there's at least one device postO
                        final List<File> apks = new ArrayList<>();
                        for (File apkDirectory : outputScope.getApkDirectories()) {
                            for (BuildOutput buildOutput :
                                    ExistingBuildElements.from(
                                            InternalArtifactType.APK, apkDirectory)) {
                                apks.add(buildOutput.getOutputFile());
                            }
                        }
                        sideLoader = new InstantAppSideLoader(appId, apks, runListener);
                    } else {
                        sideLoader = new InstantAppSideLoader(appId, bundleFile, runListener);
                    }
                    sideLoader.install(iDevice);
                }
            }
        } finally {
            deviceProvider.terminate();
        }
    }

    @InputFile
    @Nullable
    public File getAdbExe() {
        return adbExe.get();
    }

    @InputFiles
    @NonNull
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getBundleDir() {
        return bundleDir;
    }

    public static class CreationAction extends TaskCreationAction<InstantAppSideLoadTask> {

        @NonNull private final VariantScope scope;

        public CreationAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("sideLoad", "InstantApp");
        }

        @NonNull
        @Override
        public Class<InstantAppSideLoadTask> getType() {
            return InstantAppSideLoadTask.class;
        }

        @Override
        public void configure(@NonNull InstantAppSideLoadTask task) {
            task.setDescription("Side loads the " + scope.getVariantData().getDescription() + ".");
            task.setVariantName(scope.getFullVariantName());

            task.setGroup(TaskManager.INSTALL_GROUP);

            task.adbExe =
                    TaskInputHelper.memoize(
                            () -> {
                                SdkInfo info = scope.getGlobalScope().getSdkHandler().getSdkInfo();
                                return (info == null ? null : info.getAdb());
                            });
            task.bundleDir =
                    scope.getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.INSTANTAPP_BUNDLE);
        }
    }
}
