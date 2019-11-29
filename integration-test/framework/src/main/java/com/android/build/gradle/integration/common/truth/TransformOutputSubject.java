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

package com.android.build.gradle.integration.common.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

public class TransformOutputSubject
        extends Subject<TransformOutputSubject, TransformOutputContent> {

    static class Factory extends SubjectFactory<TransformOutputSubject, TransformOutputContent> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public TransformOutputSubject getSubject(
                @NonNull FailureStrategy failureStrategy, @NonNull TransformOutputContent subject) {
            return new TransformOutputSubject(failureStrategy, subject);
        }
    }

    TransformOutputSubject(
            @NonNull FailureStrategy failureStrategy, @NonNull TransformOutputContent subject) {
        super(failureStrategy, subject);
    }

    /** Attests (with a side-effect failure) that the subject contains the supplied item. */
    public final void containsByName(@Nullable String name) {
        for (SubStream subStream : getSubject()) {
            if (subStream.getName().equals(name)) {
                return;
            }
        }

        failWithRawMessage("%s should have contained stream named <%s>", getDisplaySubject(), name);
    }
}
