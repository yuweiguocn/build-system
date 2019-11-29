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
import com.android.annotations.Nullable;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.SyncIssue;

/** a Fake EvalIssueReporter that throws on all error/warnings. */
public class ThrowingIssueReporter implements EvalIssueReporter {
    public ThrowingIssueReporter() {}

    @NonNull
    @Override
    public SyncIssue reportIssue(
            @NonNull Type type,
            @NonNull Severity severity,
            @NonNull String msg,
            @Nullable String data) {
        throw new RuntimeException("fake");
    }

    @NonNull
    @Override
    public SyncIssue reportIssue(
            @NonNull Type type, @NonNull Severity severity, @NonNull String msg) {
        throw new RuntimeException("fake");
    }

    @NonNull
    @Override
    public SyncIssue reportWarning(@NonNull Type type, @NonNull String msg, @Nullable String data) {
        throw new RuntimeException("fake");
    }

    @NonNull
    @Override
    public SyncIssue reportWarning(@NonNull Type type, @NonNull String msg) {
        throw new RuntimeException("fake");
    }

    @NonNull
    @Override
    public SyncIssue reportIssue(
            @NonNull Type type, @NonNull Severity severity, @NonNull EvalIssueException exception) {
        throw new RuntimeException("fake");
    }

    @NonNull
    @Override
    public SyncIssue reportError(@NonNull Type type, @NonNull EvalIssueException exception) {
        throw new RuntimeException("fake");
    }
}
