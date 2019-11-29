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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import org.gradle.api.Action;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;

/**
 * This is an implementation of {@link WorkerExecutor} that executes runnables directly, with
 * isolation mode {@link org.gradle.workers.IsolationMode} NONE. It will try to find the matching
 * constructor of the {@link Runnable} class, and runnable will be executed immediately.
 */
public class DirectWorkerExecutor implements WorkerExecutor {
    @Override
    public void submit(
            Class<? extends Runnable> aClass, Action<? super WorkerConfiguration> action) {
        ParamCapturingWorkerConfiguration config = new ParamCapturingWorkerConfiguration();
        action.execute(config);

        Object[] params = config.getParams();
        Class<?>[] paramTypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            paramTypes[i] = params[i].getClass();
        }

        for (Constructor<?> constructor : aClass.getConstructors()) {
            Class<?>[] constructorTypes = constructor.getParameterTypes();

            if (constructorTypes.length != paramTypes.length) {
                continue;
            }

            boolean canBeInvoked = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!constructorTypes[i].isAssignableFrom(paramTypes[i])) {
                    canBeInvoked = false;
                    break;
                }
            }

            if (canBeInvoked) {
                try {
                    constructor.setAccessible(true);
                    Runnable workerAction = (Runnable) constructor.newInstance(params);
                    workerAction.run();
                    return;
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException e) {
                    throw new RuntimeException("Unable to execute worker action", e);
                }
            }
        }

        throw new RuntimeException(
                String.format(
                        "Unable to find matching constructor in %s for parameters %s",
                        aClass.getName(), Arrays.toString(params)));
    }

    @Override
    public void await() throws WorkerExecutionException {
        // do nothing, as we execute submit immediately
    }
}
