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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.SyncIssue;
import java.util.List;

public class NoOpIssueReporter implements EvalIssueReporter {

    private static final SyncIssue FAKE_ISSUE =
            new SyncIssue() {
                @Override
                public int getSeverity() {
                    return 0;
                }

                @Override
                public int getType() {
                    return 0;
                }

                @Nullable
                @Override
                public String getData() {
                    return null;
                }

                @NonNull
                @Override
                public String getMessage() {
                    return "";
                }

                @Nullable
                @Override
                public List<String> getMultiLineMessage() {
                    return null;
                }
            };

    @NonNull
    @Override
    public SyncIssue reportIssue(
            @NonNull Type type,
            @NonNull Severity severity,
            @NonNull String msg,
            @Nullable String data) {
        return FAKE_ISSUE;
    }

    @NonNull
    @Override
    public SyncIssue reportIssue(
            @NonNull Type type, @NonNull Severity severity, @NonNull String msg) {
        return FAKE_ISSUE;
    }

    @NonNull
    @Override
    public SyncIssue reportError(@NonNull Type type, @NonNull EvalIssueException exception) {
        return FAKE_ISSUE;
    }

    @NonNull
    @Override
    public SyncIssue reportWarning(@NonNull Type type, @NonNull String msg, @Nullable String data) {
        return FAKE_ISSUE;
    }

    @NonNull
    @Override
    public SyncIssue reportWarning(@NonNull Type type, @NonNull String msg) {
        return FAKE_ISSUE;
    }

    @NonNull
    @Override
    public SyncIssue reportIssue(
            @NonNull Type type, @NonNull Severity severity, @NonNull EvalIssueException exception) {
        return FAKE_ISSUE;
    }
}
