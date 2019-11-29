/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.SyncIssue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * An implementation of BaseConfig specifically for sending as part of the Android model
 * through the Gradle tooling API.
 */
@Immutable
public final class SyncIssueImpl implements SyncIssue, Serializable {
    private static final long serialVersionUID = 1L;

    private final int type;
    private final int severity;
    @Nullable private final String data;
    @NonNull private final String message;
    @Nullable private final List<String> multiLineMessage;

    public SyncIssueImpl(
            @NonNull EvalIssueReporter.Type type,
            @NonNull EvalIssueReporter.Severity severity,
            @Nullable String data,
            @NonNull String message) {
        this(type, severity, data, message, null);
    }

    public SyncIssueImpl(
            @NonNull EvalIssueReporter.Type type,
            @NonNull EvalIssueReporter.Severity severity,
            @Nullable String data,
            @NonNull String message,
            @Nullable List<String> multiLineMessage) {
        this.type = type.getType();
        this.severity = severity.getSeverity();
        this.data = data;
        this.message = message;
        this.multiLineMessage =
                multiLineMessage == null ? null : ImmutableList.copyOf(multiLineMessage);
    }

    @Override
    public int getSeverity() {
        return severity;
    }

    @Override
    public int getType() {
        return type;
    }

    @Nullable
    @Override
    public String getData() {
        return data;
    }

    @NonNull
    @Override
    public String getMessage() {
        return message;
    }

    @Nullable
    @Override
    public List<String> getMultiLineMessage() {
        return multiLineMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SyncIssueImpl syncIssue = (SyncIssueImpl) o;
        return type == syncIssue.type
                && severity == syncIssue.severity
                && Objects.equals(data, syncIssue.data)
                && Objects.equals(message, syncIssue.message)
                && Objects.equals(multiLineMessage, syncIssue.multiLineMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, severity, data, message, multiLineMessage);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("severity", severity)
                .add("data", data)
                .add("message", message)
                .toString();
    }
}
