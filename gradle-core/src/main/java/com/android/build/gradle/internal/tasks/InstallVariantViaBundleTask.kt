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
package com.android.build.gradle.internal.tasks

import com.android.annotations.VisibleForTesting
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.internal.InstallUtils
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceConfigProviderImpl
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.android.utils.ILogger
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import javax.inject.Inject

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
open class InstallVariantViaBundleTask  @Inject constructor(workerExecutor: WorkerExecutor) : AndroidBuilderTask() {

    private val workers = Workers.getWorker(workerExecutor)

    private lateinit var adbExe: () -> File
    private lateinit var projectName: String

    private var minSdkVersion = 0
    private var minSdkCodename: String? = null

    private var timeOutInMs = 0

    private var installOptions = mutableListOf<String>()

    @get:InputFiles
    lateinit var apkBundle: BuildableArtifact
        private set

    init {
        this.outputs.upToDateWhen { false }
    }

    @TaskAction
    fun install() {
        workers.use {
            it.submit(
                InstallRunnable::class.java,
                Params(
                    adbExe(),
                    apkBundle.singleFile(),
                    timeOutInMs,
                    installOptions,
                    projectName,
                    variantName,
                    minSdkCodename,
                    minSdkVersion
                )
            )
        }
    }

    internal data class Params(
        val adbExe: File,
        val apkBundle: File,
        val timeOutInMs: Int,
        val installOptions: List<String>,
        val projectName: String,
        val variantName: String,
        val minApiCodeName: String?,
        val minSdkVersion: Int
    ) : Serializable

    internal open class InstallRunnable @Inject constructor(protected val params: Params): Runnable {
        override fun run() {

            val logger: Logger = Logging.getLogger(InstallVariantViaBundleTask::class.java)
            val iLogger = LoggerWrapper(logger)
            val deviceProvider = createDeviceProvider(iLogger)
            deviceProvider.init()

            var successfulInstallCount = 0
            val devices = deviceProvider.devices

            val androidVersion = AndroidVersion(params.minSdkVersion, params.minApiCodeName)

            try {
                for (device in devices) {
                    if (!InstallUtils.checkDeviceApiLevel(
                            device, androidVersion, iLogger, params.projectName, params.variantName)
                    ) {
                        continue
                    }

                    logger.lifecycle(
                        "Generating APKs for device '{}' for {}:{}",
                        device.name,
                        params.projectName,
                        params.variantName
                    )

                    val apkPaths = getApkFiles(device)

                    if (apkPaths.isEmpty()) {
                        logger.lifecycle(
                            "Skipping device '{}' for '{}:{}': No APK generated",
                            device.name,
                            params.projectName,
                            params.variantName)

                    } else {
                        val apkFiles = apkPaths.map { it.toFile() }

                        // install them.
                        logger.lifecycle(
                            "Installing APKs '{}' on '{}' for {}:{}",
                            FileUtils.getNamesAsCommaSeparatedList(apkFiles),
                            device.name,
                            params.projectName,
                            params.variantName
                        )

                        if (apkFiles.size > 1) {
                            device.installPackages(apkFiles, params.installOptions, params.timeOutInMs, iLogger)
                            successfulInstallCount++
                        } else {
                            device.installPackage(apkFiles[0], params.installOptions, params.timeOutInMs, iLogger)
                            successfulInstallCount++
                        }
                    }
                }

                if (successfulInstallCount == 0) {
                    throw GradleException("Failed to install on any devices.")
                } else {
                    logger.quiet(
                        "Installed on {} {}.",
                        successfulInstallCount,
                        if (successfulInstallCount == 1) "device" else "devices"
                    )
                }
            } finally {
                deviceProvider.terminate()
            }
        }

        protected open fun createDeviceProvider(iLogger: ILogger): DeviceProvider =
            ConnectedDeviceProvider(
                params.adbExe,
                params.timeOutInMs,
                iLogger
            )

        @VisibleForTesting
        protected open fun getApkFiles(device: DeviceConnector) : List<Path> {
            return getApkFiles(
                params.apkBundle.toPath(),
                DeviceConfigProviderImpl(device))
        }
     }

    internal class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<InstallVariantViaBundleTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("install")
        override val type: Class<InstallVariantViaBundleTask>
            get() = InstallVariantViaBundleTask::class.java

        override fun configure(task: InstallVariantViaBundleTask) {
            super.configure(task)

            task.description = "Installs the " + variantScope.variantData.description + ""
            task.group = TaskManager.INSTALL_GROUP
            task.projectName = variantScope.globalScope.project.name

            variantScope.variantConfiguration.minSdkVersion.let {
                task.minSdkVersion = it.apiLevel
                task.minSdkCodename = it.codename
            }
            variantScope.globalScope.extension.adbOptions.installOptions?.let {
                task.installOptions.addAll(it)
            }

            task.apkBundle = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.APKS_FROM_BUNDLE)

            task.timeOutInMs = variantScope.globalScope.extension.adbOptions.timeOutInMs

            task.adbExe = { variantScope.globalScope.sdkHandler.sdkInfo?.adb!! }

        }

        override fun handleProvider(taskProvider: TaskProvider<out InstallVariantViaBundleTask>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.installTask = taskProvider
        }
    }
}
