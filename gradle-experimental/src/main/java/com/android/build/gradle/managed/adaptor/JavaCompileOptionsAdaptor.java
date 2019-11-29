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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.api.JavaCompileOptions;

/** An adaptor to convert a managed.JavaCompileOptions to a CoreJavaCompileOptions. */
public class JavaCompileOptionsAdaptor implements JavaCompileOptions {

    @NonNull private final com.android.build.gradle.managed.JavaCompileOptions javaCompileOptions;

    public JavaCompileOptionsAdaptor(
            @NonNull com.android.build.gradle.managed.JavaCompileOptions javaCompileOptions) {
        this.javaCompileOptions = javaCompileOptions;
    }

    @NonNull
    @Override
    public AnnotationProcessorOptions getAnnotationProcessorOptions() {
        return new AnnotationProcessorOptionsAdaptor(
                javaCompileOptions.getAnnotationProcessorOptions());
    }
}
