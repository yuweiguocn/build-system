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

import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.bundle.Devices
import com.android.tools.build.bundletool.commands.ExtractApksCommand
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class InstallVariantViaBundleTaskTest {

    @Mock
    private lateinit var deviceConnector: DeviceConnector

    @Before
    @Throws(Exception::class)
    fun setUpMocks() {
        MockitoAnnotations.initMocks(this)
        `when`<Int>(deviceConnector.apiLevel).thenReturn(21)
        `when`<String>(deviceConnector.name).thenReturn("fake_device")
    }

    @Test
    fun installSingle() {
        val params = InstallVariantViaBundleTask.Params(
            File("adb.exe"),
            File("bundle.aab"),
            0,
            listOf(),
            "projectName",
            "variantName",
            null,
            21
        )

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )

        val runnable = TestInstallRunnable(params, deviceConnector, outputPath)

        runnable.run()

        verify<DeviceConnector>(deviceConnector, atLeastOnce()).name
        verify<DeviceConnector>(deviceConnector, atLeastOnce()).apiLevel

        verify<DeviceConnector>(deviceConnector).installPackage(
            ArgumentMatchers.eq(outputPath.toFile()),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        verifyNoMoreInteractions(deviceConnector)
    }

    @Test
    fun installMultiple() {
        val params = InstallVariantViaBundleTask.Params(
            File("adb.exe"),
            File("bundle.aab"),
            0,
            listOf(),
            "projectName",
            "variantName",
            null,
            21
        )

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val outputPath2 = Files.createTempFile(
            "extract-apk",
            ""
        )

        val runnable = TestInstallRunnable(params, deviceConnector, outputPath, outputPath2)

        runnable.run()

        verify<DeviceConnector>(deviceConnector, atLeastOnce()).name
        verify<DeviceConnector>(deviceConnector, atLeastOnce()).apiLevel

        verify<DeviceConnector>(deviceConnector).installPackages(
            ArgumentMatchers.eq(listOf(outputPath.toFile(), outputPath2.toFile())),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        verifyNoMoreInteractions(deviceConnector)
    }

    private class TestInstallRunnable(
        params: InstallVariantViaBundleTask.Params,
        private val deviceConnector: DeviceConnector,
        private vararg val outputPaths: Path
    ) : InstallVariantViaBundleTask.InstallRunnable(params) {

        override fun createDeviceProvider(iLogger: ILogger): DeviceProvider =
            InstallVariantTaskTest.FakeDeviceProvider(ImmutableList.of(deviceConnector))

        override fun getApkFiles(device: DeviceConnector): List<Path> {
            return ImmutableList.copyOf(outputPaths)
        }
    }
}
