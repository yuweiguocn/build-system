/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.builder.model.TestOptions.Execution;
import com.android.utils.HelpfulEnumConverter;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import groovy.lang.Closure;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.ConfigureUtil;

/** Options for running tests. */
@SuppressWarnings("unused") // Exposed in the DSL.
public class TestOptions {
    private static final HelpfulEnumConverter<Execution> EXECUTION_CONVERTER =
            new HelpfulEnumConverter<>(Execution.class);

    @Nullable private String resultsDir;

    @Nullable private String reportDir;

    private boolean animationsDisabled;

    @NonNull private Execution execution = Execution.HOST;

    /**
     * Options for controlling unit tests execution.
     *
     * @since 1.1.0
     */
    @NonNull private final UnitTestOptions unitTests;

    @Inject
    public TestOptions(ObjectFactory objectFactory) {
        this.unitTests = objectFactory.newInstance(UnitTestOptions.class);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    public void unitTests(Closure closure) {
        ConfigureUtil.configure(closure, unitTests);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    @NonNull
    public UnitTestOptions getUnitTests() {
        return unitTests;
    }

    /** Name of the results directory. */
    @Nullable
    public String getResultsDir() {
        return resultsDir;
    }

    public void setResultsDir(@Nullable String resultsDir) {
        this.resultsDir = resultsDir;
    }

    /** Name of the reports directory. */
    @Nullable
    public String getReportDir() {
        return reportDir;
    }

    public void setReportDir(@Nullable String reportDir) {
        this.reportDir = reportDir;
    }

    /**
     * Disables animations during instrumented tests you run from the cammand line.
     *
     * <p>If you set this property to {@code true}, running instrumented tests with Gradle from the
     * command line executes {@code am instrument} with the {@code --no-window-animation} flag.
     * By default, this property is set to {@code false}.</p>
     *
     * <p>This property does not affect tests that you run using Android Studio. To learn more about
     * running tests from the command line, see
     * <a href="https://d.android.com/studio/test/command-line.html">Test from the Command Line</a>.
     * </p>
     */
    public boolean getAnimationsDisabled() {
        return animationsDisabled;
    }

    public void setAnimationsDisabled(boolean animationsDisabled) {
        this.animationsDisabled = animationsDisabled;
    }

    /**
     * Specifies whether to use on-device test orchestration.
     *
     * <p>If you want to <a
     * href="https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator">use
     * Android Test Orchestrator</a>, you need to specify <code>"ANDROID_TEST_ORCHESTRATOR"</code>,
     * as shown below. By default, this property is set to <code>"HOST"</code>, which disables
     * on-device orchestration.
     *
     * <pre>
     * android {
     *   testOptions {
     *     execution 'ANDROID_TEST_ORCHESTRATOR'
     *   }
     * }
     * </pre>
     *
     * @since 3.0.0
     */
    @NonNull
    public String getExecution() {
        return Verify.verifyNotNull(
                EXECUTION_CONVERTER.reverse().convert(execution),
                "No string representation for enum.");
    }

    @NonNull
    public Execution getExecutionEnum() {
        return execution;
    }

    public void setExecution(@NonNull String execution) {
        this.execution =
                Preconditions.checkNotNull(
                        EXECUTION_CONVERTER.convert(execution),
                        "The value of `execution` cannot be null.");
    }

    /** Options for controlling unit tests execution. */
    public static class UnitTestOptions {
        // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
        @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "Convert2Diamond"})
        private DomainObjectSet<Test> testTasks = new DefaultDomainObjectSet<Test>(Test.class);

        private boolean returnDefaultValues;
        private boolean includeAndroidResources;

        /**
         * Whether unmocked methods from android.jar should throw exceptions or return default
         * values (i.e. zero or null).
         *
         * <p>See <a href="https://developer.android.com/studio/test/index.html">Test Your App</a>
         * for details.
         *
         * @since 1.1.0
         */
        public boolean isReturnDefaultValues() {
            return returnDefaultValues;
        }

        public void setReturnDefaultValues(boolean returnDefaultValues) {
            this.returnDefaultValues = returnDefaultValues;
        }

        /**
         * Enables unit tests to use Android resources, assets, and manifests.
         *
         * <p>If you set this property to <code>true</code>, the plugin performs resource, asset,
         * and manifest merging before running your unit tests. Your tests can then inspect a file
         * called {@code com/android/tools/test_config.properties} on the classpath, which is a Java
         * properties file with the following keys:
         *
         * <ul>
         *   <li><code>android_sdk_home</code>: the absolute path to the Android SDK.
         *   <li><code>android_merged_resources</code>: the absolute path to the merged resources
         *       directory, which contains all the resources from this subproject and all its
         *       dependencies.
         *   <li><code>android_merged_assets</code>: the absolute path to the merged assets
         *       directory. For app subprojects, the merged assets directory contains assets from
         *       this subproject and its dependencies. For library subprojects, the merged assets
         *       directory contains only the assets from this subproject.
         *   <li><code>android_merged_manifest</code>: the absolute path to the merged manifest
         *       file. Only app subprojects merge manifests of its dependencies. So, library
         *       subprojects won't include manifest components from their dependencies.
         *   <li><code>android_custom_package</code>: the package name of the final R class. If you
         *       modify the application ID in your build scripts, this package name may not match
         *       the <code>package</code> attribute in the final app manifest.
         * </ul>
         *
         * @since 3.0.0
         */
        public boolean isIncludeAndroidResources() {
            return includeAndroidResources;
        }

        public void setIncludeAndroidResources(boolean includeAndroidResources) {
            this.includeAndroidResources = includeAndroidResources;
        }

        /**
         * Configures all unit testing tasks.
         *
         * <p>See {@link Test} for available options.
         *
         * <p>Inside the closure you can check the name of the task to configure only some test
         * tasks, e.g.
         *
         * <pre>
         * android {
         *     testOptions {
         *         unitTests.all {
         *             if (it.name == 'testDebug') {
         *                 systemProperty 'debug', 'true'
         *             }
         *         }
         *     }
         * }
         * </pre>
         *
         * @since 1.2.0
         */
        public void all(final Closure<Test> configClosure) {
            //noinspection Convert2Lambda - DSL docs generator can't handle lambdas.
            testTasks.all(
                    new Action<Test>() {
                        @Override
                        public void execute(Test testTask) {
                            ConfigureUtil.configure(configClosure, testTask);
                        }
                    });
        }

        /**
         * Configures a given test task. The configuration closures that were passed to {@link
         * #all(Closure)} will be applied to it.
         *
         * <p>Not meant to be called from build scripts. The reason it exists is that tasks are
         * created after the build scripts are evaluated, so users have to "register" their
         * configuration closures first and we can only apply them later.
         *
         * @since 1.2.0
         */
        public void applyConfiguration(@NonNull Test task) {
            this.testTasks.add(task);
        }
    }
}
