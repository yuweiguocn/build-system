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

package com.android.build.gradle.internal.fixtures;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.ForkMode;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;

/** This is implementation of {@link WorkerConfiguration} that only captures params. */
public class ParamCapturingWorkerConfiguration implements WorkerConfiguration {

    List<Object> params = new ArrayList<>();

    @Override
    public void classpath(Iterable<File> iterable) {}

    @Override
    public void setClasspath(Iterable<File> iterable) {}

    @Override
    public Iterable<File> getClasspath() {
        return null;
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.NONE;
    }

    @Override
    public void setIsolationMode(IsolationMode isolationMode) {}

    @Override
    public ForkMode getForkMode() {
        return ForkMode.NEVER;
    }

    @Override
    public void setForkMode(ForkMode forkMode) {}

    @Override
    public void forkOptions(Action<? super JavaForkOptions> action) {}

    @Override
    public JavaForkOptions getForkOptions() {
        return null;
    }

    @Override
    public void setDisplayName(String s) {}

    @Override
    public void params(Object... objects) {
        params.addAll(Arrays.asList(objects));
    }

    @Override
    public void setParams(Object... objects) {
        params.clear();
        params.addAll(Arrays.asList(objects));
    }

    @Override
    public Object[] getParams() {
        return params.toArray();
    }

    @Override
    public String getDisplayName() {
        return null;
    }
}
