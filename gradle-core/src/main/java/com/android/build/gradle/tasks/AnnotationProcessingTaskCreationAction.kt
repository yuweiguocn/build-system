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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.Task

/**
 * Convenient super class for CreationAction implementation that will process all annotated
 * input and output properties. Each input and output will be looked up in the scope and
 * pre-allocated during the [VariantTaskCreationAction.preConfigure] call.
 *
 * Once the task is created and the [VariantTaskCreationAction.configure] is invoked, the pre-allocated
 * are transferred to the relevant input and output fields of the task instance.
 */
open class AnnotationProcessingTaskCreationAction<T>(
    variantScope: VariantScope,
    override val name: String,
    override val type: Class<T>
) : VariantTaskCreationAction<T>(variantScope) where T : Task, T : VariantAwareTask {

    private val artifactsHolder= TaskArtifactsHolder<T>(variantScope.artifacts)

    override fun preConfigure(taskName: String) {
        artifactsHolder.allocateArtifacts(this)
    }

    override fun configure(task: T)  {
        super.configure(task)

        artifactsHolder.transfer(task)
    }
}