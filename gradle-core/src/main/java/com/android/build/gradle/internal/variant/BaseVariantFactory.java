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

package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.tasks.factory.AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter.Type;
import org.gradle.api.Project;

/** Common superclass for all {@link VariantFactory} implementations. */
public abstract class BaseVariantFactory implements VariantFactory {

    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final AndroidConfig extension;

    public BaseVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig extension) {
        this.globalScope = globalScope;
        this.extension = extension;
    }

    @Override
    public void preVariantWork(Project project) {
        if (project.getPluginManager().hasPlugin(ANDROID_APT_PLUGIN_NAME)) {
            globalScope
                    .getAndroidBuilder()
                    .getIssueReporter()
                    .reportError(
                            Type.INCOMPATIBLE_PLUGIN,
                            new EvalIssueException(
                                    "android-apt plugin is incompatible with the Android Gradle plugin.  "
                                            + "Please use 'annotationProcessor' configuration "
                                            + "instead.",
                                    "android-apt"));
        }
    }
}
