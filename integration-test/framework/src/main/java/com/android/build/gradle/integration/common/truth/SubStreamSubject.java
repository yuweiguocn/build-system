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

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Format;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

/** Truth Subject for {@link com.android.build.gradle.internal.pipeline.SubStream} */
public class SubStreamSubject extends Subject<SubStreamSubject, SubStream> {

    @NonNull
    public static SubStreamSubject assertThat(@Nullable SubStream stream) {
        return assert_().about(Factory.get()).that(stream);
    }

    static class Factory extends SubjectFactory<SubStreamSubject, SubStream> {
        @NonNull
        public static Factory get() {
            return new SubStreamSubject.Factory();
        }

        private Factory() {}

        @Override
        public SubStreamSubject getSubject(
                @NonNull FailureStrategy failureStrategy, @NonNull SubStream subject) {
            return new SubStreamSubject(failureStrategy, subject);
        }
    }

    SubStreamSubject(@NonNull FailureStrategy failureStrategy, @NonNull SubStream subject) {
        super(failureStrategy, subject);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasName(@NonNull String name) {
        final String actualName = getSubject().getName();
        if (!name.equals(actualName)) {
            failWithBadResults("has name", name, "is", actualName);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasFormat(@NonNull Format format) {
        final Format actualFormat = getSubject().getFormat();

        if (format != actualFormat) {
            failWithBadResults("has format", format, "is", actualFormat);
        }
    }
}
