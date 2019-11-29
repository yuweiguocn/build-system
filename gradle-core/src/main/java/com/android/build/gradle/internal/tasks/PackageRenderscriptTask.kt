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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Sync
import java.io.File

/** task packaging the rs headers */
open class PackageRenderscriptTask : Sync(), VariantAwareTask {

    @Internal
    override lateinit var variantName: String

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<PackageRenderscriptTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("package", "Renderscript")
        override val type: Class<PackageRenderscriptTask>
            get() = PackageRenderscriptTask::class.java

        private lateinit var headersDir: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            headersDir = variantScope.artifacts.appendArtifact(
                InternalArtifactType.RENDERSCRIPT_HEADERS, taskName, "out")
        }

        override fun configure(task: PackageRenderscriptTask) {
            super.configure(task)

            // package from 3 sources. the order is important to make sure the override works well.
            task
                .from(variantScope.variantConfiguration.renderscriptSourceList)
                .include("**/*.rsh")
            task.into(headersDir)
        }
    }
}

