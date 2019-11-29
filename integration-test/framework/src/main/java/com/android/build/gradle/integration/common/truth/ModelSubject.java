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

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.truth.IndirectSubject;
import com.google.common.collect.Iterables;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Truth support for AndroidProject.
 */
public class ModelSubject extends Subject<ModelSubject, AndroidProject> {

    static class Factory extends SubjectFactory<ModelSubject, AndroidProject> {

        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public ModelSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull AndroidProject subject) {
            return new ModelSubject(failureStrategy, subject);
        }
    }

    public ModelSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull AndroidProject subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    public static ModelSubject assertThat(@Nullable AndroidProject androidProject) {
        return assert_().about(ModelSubject.Factory.get()).that(androidProject);
    }

    public void hasIssueSize(int size) {
        Collection<SyncIssue> issues = getSubject().getSyncIssues();

        check().that(issues)
                .named("Issue count for project " + getSubject().getName())
                .hasSize(size);
    }

    /**
     * Asserts that the issue collection has only a single element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasSingleIssue(int severity, int type) {
        Collection<SyncIssue> subject = getSubject().getSyncIssues();

        check().that(subject).hasSize(1);

        SyncIssue issue = subject.iterator().next();
        check().that(issue).isNotNull();
        check().that(issue).hasSeverity(severity);
        check().that(issue).hasType(type);

        return issue;
    }

    /**
     * Asserts that the issue collection has only a single error with the given type.
     *
     * <p>Warnings are ignored.
     *
     * @param type the expected type
     * @return the indirect issue subject for further testing
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public IndirectSubject<IssueSubject> hasSingleError(int type) {
        Collection<SyncIssue> syncIssues =
                actual().getSyncIssues()
                        .stream()
                        .filter(issue -> issue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .collect(Collectors.toList());
        check().that(syncIssues).hasSize(1);

        SyncIssue issue = Iterables.getOnlyElement(syncIssues);
        check().that(issue).isNotNull();
        check().that(issue).hasType(type);

        return () -> new IssueSubject(failureStrategy, issue);
    }

    /**
     * Asserts that the issue collection has only a single element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @param data the expected data
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasSingleIssue(int severity, int type, String data) {
        Collection<SyncIssue> subject = getSubject().getSyncIssues();

        check().that(subject).hasSize(1);

        SyncIssue issue = subject.iterator().next();
        check().that(issue).isNotNull();
        check().that(issue).hasSeverity(severity);
        check().that(issue).hasType(type);
        check().that(issue).hasData(data);

        return issue;
    }

    /**
     * Asserts that the issue collection has only a single element with the given properties.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @param data the expected data
     * @param message the expected message
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasSingleIssue(int severity, int type, String data, String message) {
        Collection<SyncIssue> subject = getSubject().getSyncIssues();

        check().that(subject).hasSize(1);

        SyncIssue issue = subject.iterator().next();
        check().that(issue).isNotNull();
        check().that(issue).hasSeverity(severity);
        check().that(issue).hasType(type);
        check().that(issue).hasData(data);
        check().that(issue).hasMessage(message);
    }

    /**
     * Asserts that the issue collection has only an element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasIssue(int severity, int type) {
        Collection<SyncIssue> subject = getSubject().getSyncIssues();

        for (SyncIssue issue : subject) {
            if (severity == issue.getSeverity() &&
                    type == issue.getType()) {
                return issue;
            }
        }

        failWithRawMessage("'%s' does not contain <%s / %s>", getDisplaySubject(),
                severity, type);
        // won't reach
        return null;
    }

    /**
     * Asserts that the issue collection has only an element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @param data the expected data
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasIssue(int severity, int type, String data) {
        Collection<SyncIssue> subject = getSubject().getSyncIssues();

        for (SyncIssue issue : subject) {
            if (severity == issue.getSeverity() &&
                    type == issue.getType() &&
                    data.equals(issue.getData())) {
                return issue;
            }
        }

        failWithRawMessage("'%s' does not contain <%s / %s / %s>", getDisplaySubject(),
                severity, type, data);
        // won't reach
        return null;
    }

    @Override
    public CustomTestVerb check() {
        return new CustomTestVerb(failureStrategy);
    }
}
