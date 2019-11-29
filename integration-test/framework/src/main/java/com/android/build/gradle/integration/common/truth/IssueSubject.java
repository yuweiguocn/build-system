/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.builder.model.SyncIssue;
import com.google.common.base.Objects;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.util.List;

public class IssueSubject extends Subject<IssueSubject, SyncIssue> {

    static class Factory extends SubjectFactory<IssueSubject, SyncIssue> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public IssueSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull SyncIssue subject) {
            return new IssueSubject(failureStrategy, subject);
        }
    }

    public IssueSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull SyncIssue subject) {
        super(failureStrategy, subject);
    }

    public void hasSeverity(int severity) {
        if (severity != getSubject().getSeverity()) {
            failWithBadResults("has severity", severity, "is", getSubject().getSeverity());
        }
    }

    public void hasType(int type) {
        if (type != getSubject().getType()) {
            failWithBadResults("has type", type, "is", getSubject().getType());
        }
    }

    public void hasData(@Nullable String data) {
        if (!Objects.equal(data, getSubject().getData())) {
            failWithBadResults("has data", data, "is", getSubject().getData());
        }
    }

    public void hasMessage(@Nullable String message) {
        if (!Objects.equal(message, getSubject().getMessage())) {
            failWithBadResults("has message", message, "is", getSubject().getMessage());
        }
    }

    public void hasMultiLineMessage(@NonNull List<String> lines) {
        if (!Objects.equal(lines, getSubject().getMultiLineMessage())) {
            failWithBadResults(
                    "has multi-line message", lines, "is", getSubject().getMultiLineMessage());
        }
    }

    public void hasMessageThatContains(@NonNull String messageContent) {
        if (!actual().getMessage().contains(messageContent)) {
            failWithBadResults(
                    "has message that contains", messageContent, "is", actual().getMessage());
        }
    }

    @Override
    protected String getDisplaySubject() {
        String name = (internalCustomName() == null) ? "" : "\"" + this.internalCustomName() + "\" ";

        SyncIssue issue = getSubject();
        String fullName =
                String.format(
                        "%d|%d|%s|%s",
                        issue.getSeverity(), issue.getType(), issue.getData(), issue.getMessage());

        return name + "<" + fullName + ">";
    }
}
