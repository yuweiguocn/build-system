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

package com.android.build.gradle.internal;

import static com.android.build.gradle.AndroidConfig.CONFIG_DESC;
import static com.android.build.gradle.AndroidConfig.CONFIG_DESC_OLD;

import com.android.annotations.NonNull;
import com.android.build.api.dsl.sourceSets.AndroidSourceSet;
import com.android.build.gradle.AndroidConfig.DeprecatedConfigurationAction;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.AndroidSourceSetFactory;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.managed.AndroidConfig;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.internal.reflect.Instantiator;

/**
 * Utility functions for initializing an AndroidConfig.
 */
public class AndroidConfigHelper {
    public static void configure(
            @NonNull AndroidConfig model,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull Instantiator instantiator) {
        model.setDefaultPublishConfig(BuilderConstants.RELEASE);
        model.setGeneratePureSplits(false);
        model.setDeviceProviders(Lists.newArrayList());
        model.setTestServers(Lists.newArrayList());
        model.setAaptOptions(instantiator.newInstance(AaptOptions.class));
        model.setDexOptions(instantiator.newInstance(DexOptions.class, extraModelInfo));
        model.setLintOptions(instantiator.newInstance(LintOptions.class));
        model.setTestOptions(instantiator.newInstance(TestOptions.class, instantiator));
        model.setCompileOptions(instantiator.newInstance(CompileOptions.class));
        model.setPackagingOptions(instantiator.newInstance(PackagingOptions.class));
        model.setJacoco(instantiator.newInstance(JacocoOptions.class));
        model.setAdbOptions(instantiator.newInstance(AdbOptions.class));
        model.setSplits(instantiator.newInstance(Splits.class, instantiator));
        model.setLibraryRequests(new ArrayList<>());
        model.setBaseFeature(false);
        model.setBuildToolsRevision(AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION);
    }


    public static NamedDomainObjectContainer<AndroidSourceSet> createSourceSetsContainer(
            @NonNull final Project project,
            @NonNull Instantiator instantiator,
            final boolean publishPackage) {
        NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer =
                project.container(
                        AndroidSourceSet.class,
                        new AndroidSourceSetFactory(instantiator, project, publishPackage));

        sourceSetsContainer.whenObjectAdded(
                sourceSet -> {
                    ConfigurationContainer configurations = project.getConfigurations();

                    final String implementationName =
                            sourceSet.getImplementationConfigurationName();
                    final String runtimeOnlyName = sourceSet.getRuntimeOnlyConfigurationName();
                    final String compileOnlyName = sourceSet.getCompileOnlyConfigurationName();

                    // deprecated configurations first.
                    final String compileName = sourceSet.getCompileConfigurationName();
                    // due to compatibility with other plugins and with Gradle sync,
                    // we have to keep 'compile' as resolvable.
                    // TODO Fix this in gradle sync.
                    Configuration compile =
                            createConfiguration(
                                    configurations,
                                    compileName,
                                    String.format(
                                            CONFIG_DESC_OLD,
                                            "Compile",
                                            sourceSet.getName(),
                                            implementationName),
                                    "compile".equals(compileName)
                                            || "testCompile".equals(compileName) /*canBeResolved*/);
                    compile.getAllDependencies()
                            .whenObjectAdded(
                                    new DeprecatedConfigurationAction(
                                            project, compile, implementationName));

                    String packageConfigDescription;
                    if (publishPackage) {
                        packageConfigDescription =
                                String.format(
                                        CONFIG_DESC_OLD,
                                        "Publish",
                                        sourceSet.getName(),
                                        runtimeOnlyName);
                    } else {
                        packageConfigDescription =
                                String.format(
                                        CONFIG_DESC_OLD,
                                        "Apk",
                                        sourceSet.getName(),
                                        runtimeOnlyName);
                    }

                    Configuration apk =
                            createConfiguration(
                                    configurations,
                                    sourceSet.getPackageConfigurationName(),
                                    packageConfigDescription);
                    apk.getAllDependencies()
                            .whenObjectAdded(
                                    new DeprecatedConfigurationAction(
                                            project, apk, runtimeOnlyName));

                    Configuration provided =
                            createConfiguration(
                                    configurations,
                                    sourceSet.getProvidedConfigurationName(),
                                    String.format(
                                            CONFIG_DESC_OLD,
                                            "Provided",
                                            sourceSet.getName(),
                                            compileOnlyName));
                    provided.getAllDependencies()
                            .whenObjectAdded(
                                    new DeprecatedConfigurationAction(
                                            project, provided, compileOnlyName));

                    // then the new configurations.
                    String apiName = sourceSet.getApiConfigurationName();
                    Configuration api =
                            createConfiguration(
                                    configurations,
                                    apiName,
                                    String.format(CONFIG_DESC, "API", sourceSet.getName()));
                    api.extendsFrom(compile);

                    Configuration implementation =
                            createConfiguration(
                                    configurations,
                                    implementationName,
                                    String.format(
                                            CONFIG_DESC,
                                            "Implementation only",
                                            sourceSet.getName()));
                    implementation.extendsFrom(api);

                    Configuration runtimeOnly =
                            createConfiguration(
                                    configurations,
                                    runtimeOnlyName,
                                    String.format(
                                            CONFIG_DESC, "Runtime only", sourceSet.getName()));
                    runtimeOnly.extendsFrom(apk);

                    Configuration compileOnly =
                            createConfiguration(
                                    configurations,
                                    compileOnlyName,
                                    String.format(
                                            CONFIG_DESC, "Compile only", sourceSet.getName()));
                    compileOnly.extendsFrom(provided);

                    // then the secondary configurations.
                    Configuration wearConfig =
                            createConfiguration(
                                    configurations,
                                    sourceSet.getWearAppConfigurationName(),
                                    "Link to a wear app to embed for object '"
                                            + sourceSet.getName()
                                            + "'.");

                    createConfiguration(
                            configurations,
                            sourceSet.getAnnotationProcessorConfigurationName(),
                            "Classpath for the annotation processor for '"
                                    + sourceSet.getName()
                                    + "'.");

                    sourceSet.setRoot(String.format("src/%s", sourceSet.getName()));
                });
        return sourceSetsContainer;
    }

    /**
     * Creates a Configuration for a given source set.
     *
     * The configuration cannot be resolved
     *
     * @param configurations the configuration container to create the new configuration
     * @param name the name of the configuration to create.
     * @param description the configuration description.
     * @return the configuration
     *
     * @see Configuration#isCanBeResolved()
     */
    private static Configuration createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String name,
            @NonNull String description) {
        return createConfiguration(configurations, name, description, false);
    }

    /**
     * Creates a Configuration for a given source set.
     *
     * @param configurations the configuration container to create the new configuration
     * @param name the name of the configuration to create.
     * @param description the configuration description.
     * @param canBeResolved Whether the configuration can be resolved directly.
     * @return the configuration
     *
     * @see Configuration#isCanBeResolved()
     */
    private static Configuration createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String name,
            @NonNull String description,
            boolean canBeResolved) {
        Configuration configuration = configurations.findByName(name);
        if (configuration == null) {
            configuration = configurations.create(name);
        }
        // Disable modification to configurations as this causes issues when accessed through the
        // tooling-api.  Check that it works with Studio's ImportProjectAction before re-enabling
        // them.
        //configuration.setVisible(false);
        //configuration.setDescription(description);
        configuration.setCanBeConsumed(false);
        configuration.setCanBeResolved(canBeResolved);

        return configuration;
    }
}
