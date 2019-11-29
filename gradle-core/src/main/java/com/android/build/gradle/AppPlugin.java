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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.AppModelBuilder;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.model.AndroidProject;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on the base application module */
public class AppPlugin extends AbstractAppPlugin {
    @Inject
    public AppPlugin(ToolingModelBuilderRegistry registry) {
        super(registry, true /*isBaseApplication*/);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
    }

    @Override
    protected void registerModelBuilder(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantManager variantManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo) {
        registry.register(
                new AppModelBuilder(
                        globalScope,
                        variantManager,
                        taskManager,
                        (BaseAppModuleExtension) config,
                        extraModelInfo,
                        getProjectType(),
                        AndroidProject.GENERATION_ORIGINAL));
    }

    @Override
    @NonNull
    protected Class<? extends AppExtension> getExtensionClass() {
        return BaseAppModuleExtension.class;
    }

    private static class DeprecatedConfigurationAction implements Action<Dependency> {
        @NonNull private final String newDslElement;
        @NonNull private final String configName;
        @NonNull private final DeprecationReporter deprecationReporter;
        @NonNull private final DeprecationReporter.DeprecationTarget target;
        private boolean warningPrintedAlready = false;

        public DeprecatedConfigurationAction(
                @NonNull String newDslElement,
                @NonNull String configName,
                @NonNull DeprecationReporter deprecationReporter,
                @NonNull DeprecationReporter.DeprecationTarget target) {
            this.newDslElement = newDslElement;
            this.configName = configName;
            this.deprecationReporter = deprecationReporter;
            this.target = target;
        }

        @Override
        public void execute(@NonNull Dependency dependency) {
            if (!warningPrintedAlready) {
                warningPrintedAlready = true;
                deprecationReporter.reportDeprecatedConfiguration(
                        newDslElement, configName, target);
            }
        }
    }
}
