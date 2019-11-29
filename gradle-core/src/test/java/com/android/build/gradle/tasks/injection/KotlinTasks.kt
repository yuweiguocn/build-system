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

package com.android.build.gradle.tasks.injection

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.tasks.InternalID
import com.android.build.gradle.tasks.Replace
import com.android.build.gradle.tasks.TaskArtifactsHolderTest
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import javax.inject.Inject

class KotlinTasks {

    open class ValidInputTask: TaskArtifactsHolderTest.TestTask() {

        @get:InputFiles
        @get:InternalID(InternalArtifactType.APP_CLASSES)
        lateinit var classes: BuildableArtifact

        @get:InputFiles
        @get:InternalID(InternalArtifactType.APK_MAPPING)
        @get:Optional
        var optional: BuildableArtifact? = null

        override fun executeTask(vararg parameters: Any) {
            assertThat(classes).isNotNull()
            assertThat(optional).isNull()
        }
    }

    open class ValidOutputTask: TaskArtifactsHolderTest.TestTask()  {
        @get:OutputDirectory
        @get:InternalID(InternalArtifactType.APP_CLASSES)
        @get:Replace
        var classes: Provider<Directory>? = null
            private set

        override fun executeTask(vararg parameters: Any) {
            assertThat(classes).isEqualTo(parameters[0])
        }
    }

    open class InvalidOutputTypeTask: TaskArtifactsHolderTest.TestTask()  {
        @get:OutputDirectory
        @get:InternalID(InternalArtifactType.APP_CLASSES)
        @get:Replace
        lateinit var classes: Directory
    }

    open class InvalidParameterizedOutputTypeTask: TaskArtifactsHolderTest.TestTask()  {
        @get:OutputDirectory
        @get:InternalID(InternalArtifactType.APP_CLASSES)
        @get:Replace
        lateinit var classes: Provider<RegularFile>
    }

    open class NoParameterizedOutputTypeTask: TaskArtifactsHolderTest.TestTask()  {
        @get:OutputDirectory
        @get:InternalID(InternalArtifactType.APP_CLASSES)
        @get:Replace
        lateinit var classes: Provider<*>
    }

    open class MismatchedOutputTypeTask: TaskArtifactsHolderTest.TestTask()  {
        @get:OutputDirectory
        @get:InternalID(InternalArtifactType.BUNDLE)
        @get:Replace
        lateinit var classes: Provider<Directory>
    }

    open class NoIDOnInputProvidedTask: TaskArtifactsHolderTest.TestTask() {
        @get:InputFiles
        lateinit var classes: BuildableArtifact
    }
}