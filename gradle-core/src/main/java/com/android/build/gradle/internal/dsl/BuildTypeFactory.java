/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.builder.errors.EvalIssueReporter;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;

/** Factory to create BuildType object using an {@link ObjectFactory} to add the DSL methods. */
public class BuildTypeFactory implements NamedDomainObjectFactory<BuildType> {

    @NonNull private final ObjectFactory objectFactory;
    @NonNull private final Project project;
    @NonNull private final EvalIssueReporter issueReporter;
    @NonNull private final DeprecationReporter deprecationReporter;

    public BuildTypeFactory(
            @NonNull ObjectFactory objectFactory,
            @NonNull Project project,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull DeprecationReporter deprecationReporter) {
        this.objectFactory = objectFactory;
        this.project = project;
        this.issueReporter = issueReporter;
        this.deprecationReporter = deprecationReporter;
    }

    @NonNull
    @Override
    public BuildType create(@NonNull String name) {
        return objectFactory.newInstance(
                BuildType.class, name, project, objectFactory, issueReporter, deprecationReporter);
    }
}
