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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * This wrapper is a workaround for the worker API. This should be removed once we have real
 * MessageReceiver support for Gradle workers.
 */
public class SerializableMessageReceiver implements MessageReceiver, Serializable {

    @NonNull private final transient MessageReceiver forward;

    public SerializableMessageReceiver(@NonNull MessageReceiver forward) {
        this.forward = forward;
    }

    @Override
    public void receiveMessage(@NonNull Message message) {
        forward.receiveMessage(message);
    }

    @NonNull
    private Object readResolve() throws ObjectStreamException {
        return new StandardOutErrMessageReceiver();
    }
}
