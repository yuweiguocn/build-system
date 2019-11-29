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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.extension.OnDeviceTestProperties
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.model.AdbOptions
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import org.gradle.api.Action

class OnDeviceTestPropertiesImpl(dslScope: DslScope)
        : SealableObject(dslScope), OnDeviceTestProperties {

    override val adbOptions: AdbOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun adbOptions(action: Action<AdbOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var deviceProviders: MutableList<DeviceProvider>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
    override var testServers: MutableList<TestServer>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
}